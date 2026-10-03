package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The imported class that declares a field or static method named through an imported class.
 *
 * <p>javac names the class used in the source as the owner of a member reference (JLS 13.1):
 * {@code Sub.hello()} names {@code Sub} even when {@code hello} is declared by its superclass
 * {@code Base}, which may belong to another package. An export entry lists only the fields and
 * static methods its class declares (JCVM 3.1 §5.8, §5.9), while a CONSTANT_StaticFieldref or
 * CONSTANT_StaticMethodref must name the class that defines the member (§6.8.3) and a
 * CONSTANT_InstanceFieldref the class that declares the field (§6.8.2). The search follows the
 * public superclasses of the export entry ({@code supers[]}, §5.7, listed in any order) from the
 * nearest to the farthest, so that a member hiding one of a superclass wins, as in Java.
 *
 * @param pkg the imported package of the declaring class
 * @param cls the export entry of the declaring class
 */
record DeclaringExport(ImportedPackage pkg, ExportFile.ClassExport cls) {

    /**
     * Finds the class that declares a member named through {@code named}.
     *
     * @param imports  imported packages whose export files describe the superclasses
     * @param namedPkg package of the named class
     * @param named    export entry of the named class
     * @param declares whether an export entry declares the member
     * @return the declaring class, or the named class when no superclass declares the member (the
     *         caller then reports the member as missing from the named class)
     */
    static DeclaringExport find(List<ImportedPackage> imports, ImportedPackage namedPkg,
                                ExportFile.ClassExport named, Predicate<ExportFile.ClassExport> declares) {
        if (declares.test(named)) {
            return new DeclaringExport(namedPkg, named);
        }
        List<DeclaringExport> supers = new ArrayList<>();
        for (String s : named.supers()) {
            lookup(imports, s).ifPresent(supers::add);
        }
        // a class has more public superclasses than any of its superclasses: nearest first
        supers.sort(Comparator.comparingInt((DeclaringExport d) -> d.cls().supers().size()).reversed());
        return supers.stream().filter(d -> declares.test(d.cls())).findFirst()
                .orElse(new DeclaringExport(namedPkg, named));
    }

    private static Optional<DeclaringExport> lookup(List<ImportedPackage> imports, String internalName) {
        int slash = internalName.lastIndexOf('/');
        String pkg = slash < 0 ? "" : internalName.substring(0, slash);
        String simple = internalName.substring(slash + 1);
        for (ImportedPackage imp : imports) {
            if (imp.exportFile().packageName().replace('.', '/').equals(pkg)) {
                return imp.exportFile().classes().stream()
                        .filter(c -> c.name().equals(internalName) || c.name().equals(simple))
                        .findFirst()
                        .map(c -> new DeclaringExport(imp, c));
            }
        }
        return Optional.empty();
    }
}
