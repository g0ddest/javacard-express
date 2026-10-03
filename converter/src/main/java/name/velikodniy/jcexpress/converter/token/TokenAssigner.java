package name.velikodniy.jcexpress.converter.token;

import name.velikodniy.jcexpress.converter.check.Violation;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Assigns the tokens of a Java Card package (<b>Stage 3</b> of the converter pipeline) following
 * JCVM 3.1 §4.3.7.
 *
 * <ul>
 *   <li><b>Class tokens</b> (§4.3.7.2): only public classes and interfaces get tokens,
 *       consecutive from zero; package-visible ones get none ({@link TokenMap#NO_TOKEN}).
 *       Public shareable interfaces get the lowest tokens because the Export component of an
 *       applet package lists exactly those interfaces, indexed by class token (§6.13); other
 *       public interfaces and then public classes follow in Class component order.</li>
 *   <li><b>Virtual method tokens</b> (§4.3.7.6): public/protected methods use the public
 *       namespace, package-visible methods the private namespace (high bit set); overriding
 *       methods keep the inherited token; private methods get none.</li>
 *   <li><b>Interface method tokens</b> (§4.3.7.7): numbered from zero per interface, including the
 *       methods inherited from superinterfaces; interfaces inherit nothing from {@code Object}.</li>
 *   <li><b>Static field, static method and constructor tokens</b> (§4.3.7.3, §4.3.7.4): public and
 *       protected members only, consecutive from zero in declaration order.</li>
 *   <li><b>Instance field tokens</b> (§4.3.7.5): grouped by visibility and type, two tokens for
 *       {@code int}.</li>
 * </ul>
 *
 * <p>The {@link TokenMap} lists the classes in Class component order (§6.9), not token order.
 * Problems that make a valid assignment impossible (token ranges of Table 4-2, a public method
 * overriding a package-visible one, interfaces with code, an interface method without a virtual
 * method of an implementing class) are reported together in a {@link TokenAssignmentException}.
 *
 * @see TokenMap
 * @see ImportedTypes
 */
public final class TokenAssigner {

    private TokenAssigner() {}

    /**
     * Assigns tokens for a package whose classes extend only {@code java.lang.Object} and
     * implement no imported interfaces (no imported type information is available).
     *
     * @param pkg the package
     * @return the token assignment
     * @throws TokenAssignmentException if the package violates a token rule
     */
    public static TokenMap assign(PackageInfo pkg) {
        return assign(pkg, ImportedTypes.of(name -> List.of()));
    }

    /**
     * Assigns tokens using a lookup of the virtual methods of imported superclasses and
     * superinterfaces.
     *
     * @param pkg                        the package
     * @param superVirtualMethodResolver imported class or interface name to its public virtual
     *                                   methods with tokens (empty if unknown)
     * @return the token assignment
     * @throws TokenAssignmentException if the package violates a token rule
     */
    public static TokenMap assign(PackageInfo pkg,
                                  Function<String, List<TokenMap.MethodEntry>> superVirtualMethodResolver) {
        return assign(pkg, ImportedTypes.of(superVirtualMethodResolver));
    }

    /**
     * Assigns tokens using the export information of the imported packages.
     *
     * @param pkg      the package
     * @param imported imported class and interface information (export files)
     * @return the token assignment, classes in Class component order
     * @throws TokenAssignmentException if the package violates a token rule
     */
    public static TokenMap assign(PackageInfo pkg, ImportedTypes imported) {
        List<ClassInfo> order = ClassOrder.componentOrder(pkg.classes());
        List<Violation> violations = new ArrayList<>();
        Map<String, Integer> classTokens = classTokens(order, imported, violations);
        Map<String, TokenMap.ClassEntry> done = new HashMap<>();
        List<TokenMap.ClassEntry> entries = new ArrayList<>(order.size());
        for (ClassInfo ci : order) {
            List<List<TokenMap.MethodEntry>> interfaceMethods = superInterfaceMethods(ci, done, imported);
            List<TokenMap.MethodEntry> virtuals = ci.isInterface()
                    ? VirtualTokens.forInterface(ci, interfaceMethods, violations)
                    : VirtualTokens.forClass(ci, inheritedVirtuals(ci, done, imported), violations);
            if (!ci.isInterface()) {
                VirtualTokens.requireInterfaceMethods(ci, virtuals, interfaceMethods, violations);
            }
            TokenMap.ClassEntry entry = new TokenMap.ClassEntry(ci.thisClass(),
                    classTokens.get(ci.thisClass()), virtuals,
                    MemberTokens.staticMethods(ci, violations),
                    MemberTokens.instanceFields(ci, violations),
                    MemberTokens.staticFields(ci, violations));
            done.put(ci.thisClass(), entry);
            entries.add(entry);
        }
        if (!violations.isEmpty()) {
            throw new TokenAssignmentException(violations);
        }
        return new TokenMap(pkg.packageName(), List.copyOf(entries));
    }

    /**
     * Returns, for every abstract class of the package, the methods of its interfaces that it neither
     * declares nor inherits as a public or protected virtual method. Java lets an abstract class leave
     * them to its subclasses (JLS 8.1.1.1), but its {@code implemented_interface_info} maps every
     * interface method to a virtual method of the class (JCVM 3.1 §6.9.2.5), so the converter declares
     * them {@code public abstract} in the class before assigning tokens. A class inherits the methods
     * declared this way in its superclasses.
     *
     * @param pkg      the package
     * @param imported imported class and interface information (export files)
     * @return internal class name to the methods to declare, in interface and token order; classes
     *         that need none are absent
     */
    public static Map<String, List<TokenMap.MethodEntry>> undeclaredInterfaceMethods(PackageInfo pkg,
                                                                                     ImportedTypes imported) {
        List<Violation> reportedByAssign = new ArrayList<>();
        Map<String, TokenMap.ClassEntry> done = new HashMap<>();
        Map<String, List<TokenMap.MethodEntry>> undeclared = new LinkedHashMap<>();
        for (ClassInfo ci : ClassOrder.componentOrder(pkg.classes())) {
            List<List<TokenMap.MethodEntry>> interfaceMethods = superInterfaceMethods(ci, done, imported);
            List<TokenMap.MethodEntry> virtuals = ci.isInterface()
                    ? VirtualTokens.forInterface(ci, interfaceMethods, reportedByAssign)
                    : VirtualTokens.forClass(ci, inheritedVirtuals(ci, done, imported), reportedByAssign);
            if (!ci.isInterface() && ci.isAbstract()) {
                List<TokenMap.MethodEntry> missing = VirtualTokens.unimplemented(virtuals, interfaceMethods);
                if (!missing.isEmpty()) {
                    undeclared.put(ci.thisClass(), missing);
                    virtuals = new ArrayList<>(virtuals);
                    virtuals.addAll(missing); // public entries: subclasses inherit the declarations
                }
            }
            done.put(ci.thisClass(), new TokenMap.ClassEntry(ci.thisClass(), TokenMap.NO_TOKEN, virtuals,
                    List.of(), List.of(), List.of()));
        }
        return undeclared;
    }

    /**
     * Returns whether an interface is shareable: it is {@code javacard.framework.Shareable} or
     * extends it directly or indirectly (JCVM 3.1 §6.9.2.1, §6.13).
     *
     * @param name     internal name of the interface
     * @param internal interfaces of the package being converted, by internal name
     * @param imported information about imported interfaces
     * @return {@code true} if the interface is shareable
     */
    public static boolean isShareableInterface(String name, Map<String, ClassInfo> internal,
                                               ImportedTypes imported) {
        ClassInfo ci = internal.get(name);
        if (ci == null) {
            return imported.isShareable(name);
        }
        return ci.interfaces().stream().anyMatch(s -> isShareableInterface(s, internal, imported));
    }

    /** Class tokens per §4.3.7.2 (see the class documentation for the order). */
    private static Map<String, Integer> classTokens(List<ClassInfo> order, ImportedTypes imported,
                                                    List<Violation> violations) {
        Map<String, ClassInfo> byName = new HashMap<>();
        order.forEach(ci -> byName.put(ci.thisClass(), ci));
        List<ClassInfo> ranked = new ArrayList<>();
        order.stream().filter(ci -> isPublic(ci) && ci.isInterface()
                && isShareableInterface(ci.thisClass(), byName, imported)).forEach(ranked::add);
        order.stream().filter(ci -> isPublic(ci) && !ranked.contains(ci)).forEach(ranked::add);
        Map<String, Integer> tokens = new HashMap<>();
        order.forEach(ci -> tokens.put(ci.thisClass(), TokenMap.NO_TOKEN));
        for (int i = 0; i < ranked.size(); i++) {
            tokens.put(ranked.get(i).thisClass(), i);
        }
        if (ranked.size() > TokenMap.NO_TOKEN) {
            violations.add(new Violation(ranked.getLast().thisClass(), "class",
                    "more than 255 public classes and interfaces (JCVM 3.1 §4.3.7.2)"));
        }
        return tokens;
    }

    private static boolean isPublic(ClassInfo ci) {
        return (ci.accessFlags() & 0x0001) != 0;
    }

    private static List<TokenMap.MethodEntry> inheritedVirtuals(ClassInfo ci, Map<String, TokenMap.ClassEntry> done,
                                                                ImportedTypes imported) {
        if (ci.superClass() == null) {
            return List.of();
        }
        TokenMap.ClassEntry internal = done.get(ci.superClass());
        return internal != null ? internal.virtualMethods() : imported.virtualMethods(ci.superClass());
    }

    private static List<List<TokenMap.MethodEntry>> superInterfaceMethods(ClassInfo ci,
            Map<String, TokenMap.ClassEntry> done, ImportedTypes imported) {
        List<List<TokenMap.MethodEntry>> result = new ArrayList<>();
        for (String sup : ci.interfaces()) {
            TokenMap.ClassEntry internal = done.get(sup);
            result.add(internal != null ? internal.virtualMethods() : imported.virtualMethods(sup));
        }
        return result;
    }
}
