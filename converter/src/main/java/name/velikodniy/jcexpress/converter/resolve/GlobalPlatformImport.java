package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Chooses the version of {@code org.globalplatform} that a CAP file imports when the converter's built-in data
 * describes the package ({@link GlobalPlatformApi}) and no version was fixed.
 *
 * <p>The Import component records the version of the export file a CAP file was linked against (JCVM 3.1 §6.7),
 * and a card resolves the import only if its own package has the same major and an equal or higher minor version
 * (§4.5.2). So a CAP file that uses only what version 1.0 has, but records 1.7, cannot be loaded onto the many
 * cards that hold an older package. The converter therefore links against the lowest version that has every
 * class and method the package references: later versions only add elements and keep all tokens (§4.4), so the
 * CAP file is the same as one linked against any later version, apart from the version it records.
 */
public final class GlobalPlatformImport {

    private GlobalPlatformImport() {}

    /**
     * Replaces the built-in candidate import of {@code org.globalplatform} by the lowest version that links as
     * much of the package as the newest one. Nothing changes when a version was fixed, when an export file was
     * supplied for the package, or when the classes do not reference the package.
     *
     * @param fixedVersion the version fixed with {@code Converter.Builder.globalPlatformApi}, or {@code null}
     * @param candidates   candidate imports of {@link ImportLoader}, with the newest version of the built-in data
     * @param refs         references of the package's class files
     * @param classes      classes of the package being converted
     * @param version      target Java Card version
     * @return the candidates, with the built-in import of {@code org.globalplatform} in the chosen version
     */
    public static List<ImportedPackage> select(String fixedVersion, List<ImportedPackage> candidates,
                                               List<Reference> refs, List<ClassInfo> classes,
                                               JavaCardVersion version) {
        int index = builtinIndex(candidates);
        if (fixedVersion != null || index < 0 || refs.stream().noneMatch(GlobalPlatformImport::isToThePackage)) {
            return candidates;
        }
        Set<Reference> unavoidable = LinkChecker.unlinkable(refs, candidates, classes, version);
        for (String candidate : GlobalPlatformApi.versions()) {
            List<ImportedPackage> withCandidate = replaced(candidates, index, candidate);
            if (LinkChecker.unlinkable(refs, withCandidate, classes, version).equals(unavoidable)) {
                return withCandidate;
            }
        }
        return candidates;
    }

    /**
     * Every use of the package shows up as a reference owned by one of its classes: a member of the package
     * inherited through a class of the converted package comes with that class's superclass or interface
     * reference.
     */
    private static boolean isToThePackage(Reference reference) {
        return GlobalPlatformApi.PACKAGE_NAME.equals(reference.ownerPackage());
    }

    private static int builtinIndex(List<ImportedPackage> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            if (GlobalPlatformApi.isBuiltin(candidates.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static List<ImportedPackage> replaced(List<ImportedPackage> candidates, int index, String version) {
        ImportedPackage current = candidates.get(index);
        var export = GlobalPlatformApi.export(version);
        List<ImportedPackage> result = new ArrayList<>(candidates);
        result.set(index, new ImportedPackage(current.token(), export.aid(), export.majorVersion(),
                export.minorVersion(), export));
        return result;
    }
}
