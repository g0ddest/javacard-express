package name.velikodniy.jcexpress.converter.token;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Assigns numeric tokens to classes, methods, and fields within a Java Card package.
 *
 * <p>This class implements <b>Stage 3: Token Assignment</b> of the converter pipeline.
 * Tokens are compact numeric identifiers used by the JCVM to reference classes, methods,
 * and fields at runtime, replacing the symbolic references found in standard JVM class files.
 *
 * <h2>Token Assignment Rules (JCVM 3.0.5, Section 4.3)</h2>
 *
 * <p>Token assignment follows a deterministic ordering to ensure reproducible CAP file
 * generation:
 *
 * <ul>
 *   <li><b>Class tokens</b> (package-scoped, 0-based): interfaces are assigned tokens first,
 *       followed by concrete classes. Within each group, classes are sorted lexicographically
 *       by their fully-qualified internal name.</li>
 *   <li><b>Virtual method tokens</b> (class-scoped): inherited tokens from the superclass are
 *       preserved so that overriding methods share the same token as the method they override.
 *       New (non-overriding) virtual methods receive the next available token after the
 *       superclass's highest virtual method token. Constructors ({@code <init>}) and static
 *       initializers ({@code <clinit>}) are excluded from token assignment.</li>
 *   <li><b>Static method tokens</b> (class-scoped, 0-based): assigned sequentially in
 *       declaration order, independent of virtual method tokens.</li>
 *   <li><b>Instance field tokens</b> (class-scoped, 0-based): assigned sequentially in
 *       declaration order.</li>
 *   <li><b>Static field tokens</b> (class-scoped, 0-based): assigned sequentially in
 *       declaration order, independent of instance field tokens.</li>
 * </ul>
 *
 * <h2>Inheritance-Aware Assignment</h2>
 *
 * <p>The two-argument {@link #assign(PackageInfo, Function)} method supports inheritance-aware
 * virtual method token assignment. A {@code superVirtualMethodResolver} function is provided to
 * look up virtual method tokens from external superclasses (loaded from export files). For
 * superclasses within the same package, tokens are resolved internally by processing classes in
 * topological order.
 *
 * <p>The result is a {@link TokenMap} that the subsequent pipeline stages (reference resolution,
 * bytecode translation, and CAP generation) use to emit compact token-based references.
 *
 * @see TokenMap
 * @see ExportFile
 * @see name.velikodniy.jcexpress.converter.Converter
 */
public final class TokenAssigner {

    private TokenAssigner() {}

    /**
     * Assigns tokens without inheritance awareness.
     *
     * <p>This convenience overload uses an empty resolver that returns no superclass virtual
     * methods. It is suitable for packages whose classes do not extend external superclasses
     * (i.e., they only extend {@code java.lang.Object}), and for unit testing.
     *
     * @param pkg the package whose classes, methods, and fields will receive tokens
     * @return a complete {@link TokenMap} for the package
     * @see #assign(PackageInfo, Function)
     */
    public static TokenMap assign(PackageInfo pkg) {
        return assign(pkg, name -> List.of());
    }

    /**
     * Assigns tokens with inheritance-aware virtual method token assignment.
     *
     * <p>This is the primary entry point used by the converter pipeline. For each class in the
     * package, virtual method tokens are assigned as follows:
     *
     * <ol>
     *   <li>The superclass's virtual method table is resolved -- either from previously
     *       processed classes in the same package, or via the external
     *       {@code superVirtualMethodResolver} (which typically queries {@link ExportFile}
     *       data loaded from {@code .exp} files).</li>
     *   <li>Overriding methods inherit the token of the superclass method they override,
     *       preserving the virtual dispatch table layout (JCVM 3.0.5, Section 4.3.7.10).</li>
     *   <li>New (non-overriding) virtual methods are assigned tokens starting from
     *       {@code max(superclass tokens) + 1}.</li>
     * </ol>
     *
     * <p>Classes within the package are processed in a deterministic topological order
     * (interfaces first, then concrete classes, sorted by name within each group) so that
     * when a class extends another class in the same package, the superclass's tokens have
     * already been assigned.
     *
     * @param pkg                        the package whose classes, methods, and fields will
     *                                   receive tokens
     * @param superVirtualMethodResolver resolves an external superclass's internal name
     *                                   (e.g., {@code "javacard/framework/Applet"}) to
     *                                   its list of virtual method entries with pre-assigned
     *                                   tokens; returns an empty list if the class is unknown
     * @return a complete {@link TokenMap} for the package
     */
    public static TokenMap assign(PackageInfo pkg,
                                   Function<String, List<TokenMap.MethodEntry>> superVirtualMethodResolver) {
        List<ClassInfo> sorted = sortClasses(pkg.classes());

        // Track virtual methods per class for internal superclass lookups
        Map<String, List<TokenMap.MethodEntry>> localVirtualMethods = new HashMap<>();
        List<TokenMap.ClassEntry> entries = new ArrayList<>(sorted.size());
        int classToken = 0;

        for (ClassInfo ci : sorted) {
            List<TokenMap.MethodEntry> superVirtuals = resolveSuperVirtuals(
                    ci, localVirtualMethods, superVirtualMethodResolver);

            TokenMap.ClassEntry entry = buildClassEntry(ci, classToken++, superVirtuals);
            entries.add(entry);
            localVirtualMethods.put(ci.thisClass(), entry.virtualMethods());
        }

        return new TokenMap(pkg.packageName(), List.copyOf(entries));
    }

    private static List<TokenMap.MethodEntry> resolveSuperVirtuals(
            ClassInfo ci,
            Map<String, List<TokenMap.MethodEntry>> localVirtuals,
            Function<String, List<TokenMap.MethodEntry>> externalResolver) {

        if (ci.superClass() == null) {
            return List.of();
        }
        // java/lang/Object falls through to the external resolver, which returns
        // equals() at token 0. This ensures Object subclasses inherit token 0
        // for equals() and start new virtual methods at token 1 (JCVM spec §4.3).

        // Try local first (same-package class already processed)
        List<TokenMap.MethodEntry> local = localVirtuals.get(ci.superClass());
        if (local != null) return local;

        // External superclass — use the resolver
        List<TokenMap.MethodEntry> external = externalResolver.apply(ci.superClass());
        return external != null ? external : List.of();
    }

    private static List<ClassInfo> sortClasses(List<ClassInfo> classes) {
        // Interfaces first, then concrete classes.
        // Interfaces: sorted by name (no inheritance ordering needed).
        // Concrete classes: topological sort (superclass before subclass) per JCVM spec §4.3,
        // with name-based tiebreaking for deterministic ordering when multiple classes
        // have their superclass already emitted.
        List<ClassInfo> interfaces = new ArrayList<>();
        List<ClassInfo> concrete = new ArrayList<>();

        for (ClassInfo ci : classes) {
            if (ci.isInterface()) {
                interfaces.add(ci);
            } else {
                concrete.add(ci);
            }
        }

        interfaces.sort(Comparator.comparing(ClassInfo::thisClass));
        concrete = topologicalSort(concrete);

        List<ClassInfo> result = new ArrayList<>(classes.size());
        result.addAll(interfaces);
        result.addAll(concrete);
        return result;
    }

    /**
     * Topological sort: emit classes whose superclass is external or already emitted.
     * Within each "ready" batch, sort by name for deterministic ordering.
     */
    private static List<ClassInfo> topologicalSort(List<ClassInfo> classes) {
        Map<String, ClassInfo> byName = new HashMap<>();
        for (ClassInfo ci : classes) byName.put(ci.thisClass(), ci);

        List<ClassInfo> result = new ArrayList<>(classes.size());
        var emitted = new HashSet<String>();

        while (result.size() < classes.size()) {
            List<ClassInfo> ready = new ArrayList<>();
            for (ClassInfo ci : classes) {
                if (emitted.contains(ci.thisClass())) continue;
                String sup = ci.superClass();
                if (sup == null || !byName.containsKey(sup) || emitted.contains(sup)) {
                    ready.add(ci);
                }
            }
            ready.sort(Comparator.comparing(ClassInfo::thisClass));
            for (ClassInfo ci : ready) {
                result.add(ci);
                emitted.add(ci.thisClass());
            }
        }

        return result;
    }

    private static TokenMap.ClassEntry buildClassEntry(ClassInfo ci, int classToken,
                                                        List<TokenMap.MethodEntry> superVirtuals) {
        // Start with inherited virtual methods
        List<TokenMap.MethodEntry> virtualMethods = new ArrayList<>(superVirtuals);
        int nextVirtualToken = superVirtuals.stream()
                .mapToInt(TokenMap.MethodEntry::token)
                .max()
                .orElse(-1) + 1;

        List<TokenMap.MethodEntry> staticMethods = new ArrayList<>();
        int staticIdx = 0;

        for (MethodInfo mi : ci.methods()) {
            // Skip static initializers — they don't get tokens per JCVM spec §4.3.7.8
            if (mi.isStaticInitializer()) continue;

            // Constructors and static methods both get static method tokens
            // per JCVM spec §4.3.7.8: constructors are invoked via invokespecial
            // which uses StaticMethodRef entries (tag 6).
            // Per §6.14 Table 6-39, private methods and package-private static/constructors
            // are NOT exported and get token 0xFF in the Descriptor — they don't receive
            // real tokens and are excluded from the Export component.
            if (mi.isConstructor() || mi.isStatic() || mi.isPrivate()) {
                boolean exported = !mi.isPrivate()
                        && ((mi.accessFlags() & 0x0001) != 0 || (mi.accessFlags() & 0x0004) != 0);
                if (exported) {
                    staticMethods.add(new TokenMap.MethodEntry(
                            mi.name(), mi.descriptor(), staticIdx++));
                }
            } else {
                // Check if this overrides a superclass method
                boolean isOverride = virtualMethods.stream()
                        .anyMatch(sv -> sv.name().equals(mi.name())
                                && sv.descriptor().equals(mi.descriptor()));

                if (!isOverride) {
                    // New virtual method — assign next available token
                    virtualMethods.add(new TokenMap.MethodEntry(
                            mi.name(), mi.descriptor(), nextVirtualToken++));
                }
                // Override: already in virtualMethods with correct inherited token
            }
        }

        List<TokenMap.FieldEntry> instanceFields = new ArrayList<>();
        List<TokenMap.FieldEntry> staticFields = new ArrayList<>();
        int instFieldIdx = 0;
        int staticFieldIdx = 0;

        for (FieldInfo fi : ci.fields()) {
            if (fi.isCompileTimeConstant()) {
                // Compile-time constants are inlined by javac — they don't need
                // tokens, storage, or export entries (JCVM §6.10, §6.14)
                continue;
            }
            if (fi.isStatic()) {
                boolean exported = (fi.accessFlags() & 0x0001) != 0
                        || (fi.accessFlags() & 0x0004) != 0;
                if (exported) {
                    staticFields.add(new TokenMap.FieldEntry(
                            fi.name(), fi.descriptor(), staticFieldIdx++));
                }
            } else {
                instanceFields.add(new TokenMap.FieldEntry(
                        fi.name(), fi.descriptor(), instFieldIdx++));
            }
        }

        return new TokenMap.ClassEntry(
                ci.thisClass(), classToken,
                List.copyOf(virtualMethods), List.copyOf(staticMethods),
                List.copyOf(instanceFields), List.copyOf(staticFields)
        );
    }
}
