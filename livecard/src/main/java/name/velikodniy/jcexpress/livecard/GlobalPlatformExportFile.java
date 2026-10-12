package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.converter.ConverterResult;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The export file of the GlobalPlatform Card API package {@code org.globalplatform} for an off-card verifier. The
 * converter links the package from its built-in data ({@link GlobalPlatformApi}), but a verifier reads the export
 * file of every package a CAP file imports from a file (JCVM 3.1 chapter 5), and a Java Card development kit has
 * none for this package. So the file is written from the same data, for the version the CAP file imports.
 */
final class GlobalPlatformExportFile {

    private static final String PACKAGE = "org.globalplatform";

    private GlobalPlatformExportFile() {
    }

    /**
     * Writes the export file when a CAP file imports {@code org.globalplatform} from the converter's built-in data.
     * A package converted with an export file of its own for that package needs none: the verifier gets that file
     * with the package's export path.
     *
     * @param result    the conversion result
     * @param target    the conversion target, which decides the export file format
     * @param directory a directory of the run for these files only, not the export path root of the run's own
     *                  packages, which importers get whole; the file goes below {@code org.globalplatform-<version>/}
     *                  of it
     * @return the export path entry that holds the file in the layout {@code <package>/javacard/<name>.exp}, or
     *         empty when the CAP file does not import the package from the built-in data
     * @throws LiveCardException if the file cannot be written
     */
    static Optional<Path> forVerifier(ConverterResult result, JavaCardVersion target, Path directory) {
        Optional<ConverterResult.Import> imported = result.imports().stream()
                .filter(candidate -> candidate.packageName().equals(PACKAGE) && candidate.builtIn()).findFirst();
        if (imported.isEmpty()) {
            return Optional.empty();
        }
        String version = imported.get().version();
        Path entry = directory.resolve(PACKAGE + "-" + version);
        Path file = entry.resolve("org/globalplatform/javacard/globalplatform.exp");
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, GlobalPlatformApi.exportFileBytes(version, target));
        } catch (IOException e) {
            throw new LiveCardException("Cannot save the export file of " + PACKAGE + " " + version + " for the"
                    + " off-card verifier in " + file + ": " + e.getMessage(), e);
        }
        return Optional.of(entry);
    }
}
