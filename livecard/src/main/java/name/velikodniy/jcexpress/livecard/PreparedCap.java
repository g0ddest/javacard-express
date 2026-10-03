package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.ConverterResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A CAP file ready to load: converted by this project's converter for the configured Java Card version, in
 * memory, every time; checked by the configured off-card verifier (unless {@code verifierSdk=none}); and saved
 * next to the transcripts for inspection. A saved file is never loaded: the copy of an earlier run is deleted
 * before converting, so a failed conversion leaves no CAP file behind that could be mistaken for the current one.
 *
 * <p>A package with an Export component also gets its export file, saved below {@code exp/} of the transcript
 * directory in the export path layout {@code <package>/javacard/<name>.exp} (JCVM 3.0.5 4.1.1), where the packages
 * that import it find it.</p>
 *
 * @param cap          the CAP file bytes
 * @param warnings     the converter's warnings
 * @param capFile      where the CAP file was saved
 * @param verification the verification result, or a note that none was configured
 * @param exportPath   the export path entry with the package's export file, or null without Export component
 */
record PreparedCap(byte[] cap, List<String> warnings, Path capFile, String verification, Path exportPath) {

    PreparedCap {
        cap = cap.clone();
        warnings = List.copyOf(warnings);
    }

    @Override
    public byte[] cap() {
        return cap.clone();
    }

    /**
     * Converts, verifies and saves a package.
     *
     * @param pkg      the package
     * @param config   the settings (target version, transcript directory)
     * @param verifier the off-card verifier, or null
     * @return the prepared CAP file
     * @throws LiveCardException if conversion or verification fails (nothing has been sent to the card)
     */
    static PreparedCap prepare(AppletPackage pkg, LiveCardConfig config, CapVerifier verifier) {
        Path file = config.transcriptDir().resolve("cap").resolve(pkg.packageName() + ".cap");
        Path exportRoot = config.transcriptDir().resolve("exp");
        Path exportFile = exportRoot.resolve(pkg.packageName().replace('.', '/')).resolve("javacard")
                .resolve(pkg.packageName().substring(pkg.packageName().lastIndexOf('.') + 1) + ".exp");
        deleteEarlier(file);
        deleteEarlier(exportFile);
        ConverterResult result;
        try {
            result = pkg.converter(config.javaCardVersion()).build().convert();
        } catch (ConverterException | RuntimeException e) {
            throw new LiveCardException("Conversion of " + pkg.packageName() + " failed; nothing was loaded: "
                    + e.getMessage(), e);
        }
        if (pkg.exportComponent()) {
            save(exportFile, result.exportFile());
        }
        Path exportPath = pkg.exportComponent() ? exportRoot : null;
        String verification = verify(pkg, result.capFile(), verifier, exportPath);
        save(file, result.capFile());
        return new PreparedCap(result.capFile(), result.warnings(), file, verification, exportPath);
    }

    private static String verify(AppletPackage pkg, byte[] cap, CapVerifier verifier, Path exportPath) {
        if (verifier == null) {
            return "not verified (verifierSdk=none)";
        }
        List<Path> entries = new ArrayList<>();
        if (exportPath != null) {
            entries.add(exportPath);
        }
        entries.addAll(pkg.exportPath());
        CapVerifier.Result result = verifier.verify(cap, entries);
        if (!result.passed()) {
            throw new LiveCardException("The CAP file of " + pkg.packageName() + " fails the off-card verifier of "
                    + verifier.kit() + "; nothing was loaded:\n" + result.output());
        }
        return "passed the off-card verifier of " + verifier.kit().getFileName();
    }

    private static void deleteEarlier(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new LiveCardException("Cannot delete the file of an earlier run " + file + ": " + e.getMessage(), e);
        }
    }

    private static void save(Path file, byte[] content) {
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, content);
        } catch (IOException e) {
            throw new LiveCardException("Cannot save " + file + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PreparedCap other && Arrays.equals(cap, other.cap) && warnings.equals(other.warnings)
                && capFile.equals(other.capFile) && verification.equals(other.verification)
                && Objects.equals(exportPath, other.exportPath);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(cap);
    }

    @Override
    public String toString() {
        return "PreparedCap[" + cap.length + " bytes, " + capFile + ", " + verification + "]";
    }
}
