package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Kind;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Reference;
import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks, before translation, that every reference to an imported package can be linked:
 * the package has an export file, and the class or member is listed in it (JCVM 3.1 §4.3.3,
 * §4.3.5). For the built-in API this is a check against the API of the <em>target</em>
 * platform (§4.5.2): a CAP file for Java Card 2.2.2 must not reference
 * {@code Util.arrayFill}, which only Java Card 3.0.5 and later provide.
 *
 * <p>All problems are reported together, each with the class, method and source line where the
 * reference occurs, and for API elements with the Java Card version that introduced them.
 */
public final class LinkChecker {

    private static final String OBJECT = "java/lang/Object";

    private final Map<String, ImportedPackage> imports = new HashMap<>();
    private final Map<String, ClassInfo> internal = new HashMap<>();
    private final Set<String> ownPackages = new HashSet<>();
    private final JavaCardVersion version;
    private final Set<String> problems = new LinkedHashSet<>();

    private LinkChecker(List<ImportedPackage> imports, List<ClassInfo> classes, JavaCardVersion version) {
        for (ImportedPackage imp : imports) this.imports.put(imp.exportFile().packageName(), imp);
        for (ClassInfo ci : classes) {
            internal.put(ci.thisClass(), ci);
            ownPackages.add(packageOf(ci.thisClass()));
        }
        this.version = version;
    }

    /**
     * Checks all references.
     *
     * @param refs    references of the package's class files
     * @param imports candidate imports (built-in and user-supplied export files)
     * @param classes classes of the package being converted
     * @param version target Java Card version
     * @throws ConverterException listing every reference that cannot be linked
     */
    public static void check(List<Reference> refs, List<ImportedPackage> imports, List<ClassInfo> classes,
                             JavaCardVersion version) throws ConverterException {
        LinkChecker checker = new LinkChecker(imports, classes, version);
        for (Reference r : refs) checker.check(r);
        if (!checker.problems.isEmpty()) {
            throw new ConverterException("Cannot link " + checker.problems.size()
                    + " reference(s) against the export files for Java Card " + version.specVersion()
                    + ":\n  - " + String.join("\n  - ", checker.problems));
        }
    }

    private void check(Reference r) {
        if (!internal.containsKey(r.owner())) {
            checkExternal(r, r.owner());
        } else if (isMethod(r)) {
            checkInheritedMethod(r);
        }
    }

    /**
     * A method referenced through a class of this package may be inherited from an imported
     * class or interface; it must then be linkable there.
     */
    private void checkInheritedMethod(Reference r) {
        Set<String> externals = new LinkedHashSet<>();
        if (declaredInPackage(r, externals) || externals.isEmpty()) return;
        for (String e : externals) {
            Optional<ExportFile.ClassExport> cls = findClass(e);
            if (cls.isPresent() && hasMember(cls.get(), r)) return;
        }
        String declarer = externals.iterator().next();
        ImportedPackage imp = imports.get(packageOf(declarer));
        if (imp != null && findClass(declarer).isPresent()) report(r, missingMember(declarer, r, imp));
    }

    /**
     * Searches the classes and interfaces of this package reachable from the reference owner
     * (superclasses and superinterfaces); collects the imported types where the search leaves
     * the package.
     */
    private boolean declaredInPackage(Reference r, Set<String> externals) {
        Deque<String> work = new ArrayDeque<>(List.of(r.owner()));
        Set<String> visited = new HashSet<>();
        while (!work.isEmpty()) {
            String name = work.removeFirst();
            if (!visited.add(name)) continue;
            ClassInfo ci = internal.get(name);
            if (ci == null) {
                externals.add(name);
                continue;
            }
            if (ci.methods().stream().anyMatch(m -> m.name().equals(r.name())
                    && m.descriptor().equals(r.descriptor()))) return true;
            if (ci.superClass() != null) work.addLast(ci.superClass());
            work.addAll(ci.interfaces());
        }
        return false;
    }

    private static boolean isMethod(Reference r) {
        return r.kind().isMember() && r.kind() != Kind.STATIC_FIELD && r.kind() != Kind.INSTANCE_FIELD;
    }

    private void checkExternal(Reference r, String owner) {
        String pkg = packageOf(owner);
        if (ownPackages.contains(pkg)) {
            // a package never imports itself (JCVM 3.1 §6.7): the class file is missing
            report(r, "class " + dotted(owner) + " of the package being converted (" + dotted(pkg)
                    + ") has no class file in the classes directory");
            return;
        }
        ImportedPackage imp = imports.get(pkg);
        if (imp == null) {
            report(r, missingPackage(pkg));
            return;
        }
        Optional<ExportFile.ClassExport> cls = findClass(owner);
        if (cls.isEmpty()) {
            report(r, missingClass(owner, imp));
        } else if (r.kind().isMember() && !hasMember(cls.get(), r)) {
            report(r, missingMember(owner, r, imp));
        }
    }

    private boolean hasMember(ExportFile.ClassExport cls, Reference r) {
        if (declaresMember(cls, r)) return true;
        if ("<init>".equals(r.name())) return false;
        List<String> inherited = new ArrayList<>(cls.supers());
        inherited.addAll(cls.interfaces());
        if (cls.isInterface()) inherited.add(OBJECT);
        for (String s : inherited) {
            Optional<ExportFile.ClassExport> sc = findClass(s);
            if (sc.isPresent() && declaresMember(sc.get(), r)) return true;
        }
        return false;
    }

    private static boolean declaresMember(ExportFile.ClassExport cls, Reference r) {
        if (r.kind() == Kind.STATIC_FIELD || r.kind() == Kind.INSTANCE_FIELD) {
            return cls.fields().stream().anyMatch(f -> f.name().equals(r.name()));
        }
        return cls.methods().stream()
                .anyMatch(m -> m.name().equals(r.name()) && m.descriptor().equals(r.descriptor()));
    }

    private Optional<ExportFile.ClassExport> findClass(String className) {
        ImportedPackage imp = imports.get(packageOf(className));
        if (imp == null) return Optional.empty();
        String simple = className.substring(className.lastIndexOf('/') + 1);
        return imp.exportFile().classes().stream()
                .filter(c -> c.name().equals(className) || c.name().equals(simple))
                .findFirst();
    }

    // ── messages ──

    private String missingPackage(String pkg) {
        Optional<JavaCardVersion> since = BuiltinExports.packageIntroducedIn(pkg);
        if (since.isPresent()) {
            return "package " + dotted(pkg) + " is not part of the Java Card " + version.specVersion()
                    + " API (it was introduced in Java Card " + since.get().specVersion() + ")";
        }
        if (isJavaSePackage(pkg)) {
            // JCVM 3.1 §2.2.1.4: none of the Java core API classes are supported, except a subset of
            // java.lang; javac also emits such calls itself (e.g. Objects.requireNonNull)
            return "there is no export file for package " + dotted(pkg) + ": the Java SE library is not part"
                    + " of the Java Card platform (JCVM 3.1 §2.2.1.4)";
        }
        return "no export file was supplied for package " + dotted(pkg)
                + " (use importExportFile or exportPath)";
    }

    /** Packages of the Java SE platform namespace; the Java Card API ones are built in. */
    private static boolean isJavaSePackage(String pkg) {
        return pkg.startsWith("java/") || pkg.startsWith("javax/");
    }

    private String missingClass(String owner, ImportedPackage imp) {
        Optional<JavaCardVersion> since = builtin(imp) ? BuiltinExports.introducedIn(owner, null, null)
                : Optional.empty();
        String what = dotted(owner) + " is not available in " + describe(imp);
        return since.map(v -> what + "; it was introduced in Java Card " + v.specVersion())
                .orElse(what + " (not exported by that package)");
    }

    private String missingMember(String owner, Reference r, ImportedPackage imp) {
        String member = dotted(owner) + "." + r.name() + (r.descriptor().startsWith("(") ? r.descriptor() : "");
        Optional<JavaCardVersion> since = builtin(imp) ? introducedInHierarchy(owner, r) : Optional.empty();
        String what = member + " is not available in " + describe(imp);
        return since.map(v -> what + "; it was introduced in Java Card " + v.specVersion())
                .orElse(what + " (no such member in the export file)");
    }

    private Optional<JavaCardVersion> introducedInHierarchy(String owner, Reference r) {
        List<String> candidates = new ArrayList<>(List.of(owner));
        findClass(owner).ifPresent(c -> {
            candidates.addAll(c.supers());
            candidates.addAll(c.interfaces());
        });
        String desc = r.kind() == Kind.STATIC_FIELD || r.kind() == Kind.INSTANCE_FIELD ? null : r.descriptor();
        return candidates.stream()
                .map(c -> BuiltinExports.introducedIn(c, r.name(), desc))
                .flatMap(Optional::stream)
                .min(Enum::compareTo);
    }

    private boolean builtin(ImportedPackage imp) {
        ExportFile ef = BuiltinExports.getExport(imp.exportFile().packageName(), version);
        return ef != null && ef.equals(imp.exportFile());
    }

    private String describe(ImportedPackage imp) {
        String pkg = dotted(imp.exportFile().packageName()) + " " + imp.majorVersion() + "." + imp.minorVersion();
        return builtin(imp) ? "the Java Card " + version.specVersion() + " API (" + pkg + ")"
                : "the export file of " + pkg;
    }

    private void report(Reference r, String message) {
        problems.add(r.location() + ": " + message);
    }

    private static String packageOf(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? "" : internalName.substring(0, slash);
    }

    private static String dotted(String internalName) {
        return internalName.replace('/', '.');
    }
}
