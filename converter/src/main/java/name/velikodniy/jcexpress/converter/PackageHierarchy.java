package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;
import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The classes and interfaces of the package being converted, together with the imported types as
 * their export files describe them: an export entry lists every public superclass in
 * {@code supers[]} and every public superinterface in {@code interfaces[]} (JCVM 3.1 §5.7).
 */
final class PackageHierarchy {

    private final Map<String, ClassInfo> own = new HashMap<>();
    private final Map<String, ExportFile> exports = new HashMap<>();

    /**
     * Creates the hierarchy view.
     *
     * @param pkg     the package being converted
     * @param imports candidate imports, whose export files describe the imported types
     */
    PackageHierarchy(PackageInfo pkg, List<ImportedPackage> imports) {
        for (ClassInfo ci : pkg.classes()) own.put(ci.thisClass(), ci);
        for (ImportedPackage imp : imports) exports.put(imp.exportFile().packageName(), imp.exportFile());
    }

    /**
     * Returns the class or interface of the package with the given internal name.
     *
     * @param name internal name, may be {@code null}
     * @return the class, or {@code null} if it is not a class of the package
     */
    ClassInfo own(String name) {
        return own.get(name);
    }

    /**
     * Returns the export entry of an imported class or interface.
     *
     * @param name internal name
     * @return the entry, empty if no imported export file lists the type
     */
    Optional<ExportFile.ClassExport> exported(String name) {
        int slash = name.lastIndexOf('/');
        ExportFile ef = exports.get(slash < 0 ? "" : name.substring(0, slash));
        if (ef == null) return Optional.empty();
        String simple = name.substring(slash + 1);
        return ef.classes().stream()
                .filter(c -> c.name().equals(name) || c.name().equals(simple))
                .findFirst();
    }

    /**
     * Returns whether a type is the target type or extends or implements it, directly or through
     * superclasses and superinterfaces of the package or of imported packages.
     *
     * @param type   internal name of a class or interface
     * @param target internal name of the supertype
     * @return {@code true} if {@code type} is a subtype of {@code target}
     */
    boolean isSubtypeOf(String type, String target) {
        Deque<String> work = new ArrayDeque<>(List.of(type));
        Set<String> seen = new HashSet<>();
        while (!work.isEmpty()) {
            String name = work.pop();
            if (target.equals(name)) return true;
            if (!seen.add(name)) continue;
            ClassInfo ci = own.get(name);
            if (ci != null) {
                if (ci.superClass() != null) work.push(ci.superClass());
                work.addAll(ci.interfaces());
            } else {
                exported(name).ifPresent(e -> {
                    work.addAll(e.supers());
                    work.addAll(e.interfaces());
                });
            }
        }
        return false;
    }
}
