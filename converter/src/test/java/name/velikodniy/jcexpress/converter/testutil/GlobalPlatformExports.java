package name.velikodniy.jcexpress.converter.testutil;

import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Locates the export files of the GlobalPlatform Card API package {@code org.globalplatform} that a developer
 * installed locally, for <em>black-box</em> comparison tests and for the table generator. The files are
 * GlobalPlatform's; nothing of them is stored in the repository, and tests that use this class are skipped when
 * the files are absent (for example on CI).
 *
 * <p>They are looked up under {@code ../build/globalplatform-api} (relative to the converter module; {@code build/}
 * is ignored by git) or in the directory given by the system property {@value #PROPERTY}, one directory per package
 * version in the export path layout of JCVM 3.1 §5.2:
 * {@code <version>/org/globalplatform/javacard/globalplatform.exp}, for example {@code 1.5/org/...}.
 */
public final class GlobalPlatformExports {

    /** System property that names the directory with the export files. */
    public static final String PROPERTY = "jcx.gpapi.exports";

    private static final String ENTRY = "org/globalplatform/javacard/globalplatform.exp";
    private static final Pattern VERSION = Pattern.compile("\\d{1,3}\\.\\d{1,3}");

    private GlobalPlatformExports() {}

    /**
     * Returns the directory that holds one directory per package version.
     *
     * @return the configured directory, or the default below {@code build/}
     */
    public static Path root() {
        return Path.of(System.getProperty(PROPERTY, "../build/globalplatform-api"));
    }

    /**
     * Tells whether export files are installed.
     *
     * @return whether {@link #root()} holds the export file of at least one version
     */
    public static boolean available() {
        return !versionDirectories(root()).isEmpty();
    }

    /**
     * Reads the export files below {@link #root()}.
     *
     * @return the export file of each installed version, in ascending version order
     */
    public static Map<String, ExportFile> read() {
        return read(root());
    }

    /**
     * Reads the export files below a directory.
     *
     * @param root the directory with one directory per version
     * @return the export file of each version found, in ascending version order
     * @throws UncheckedIOException if a file cannot be read
     */
    public static Map<String, ExportFile> read(Path root) {
        Map<String, ExportFile> exports = new LinkedHashMap<>();
        for (Path directory : versionDirectories(root)) {
            try {
                exports.put(directory.getFileName().toString(), ExportFileReader.readFile(directory.resolve(ENTRY)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return exports;
    }

    /**
     * Returns the export file of one version as GlobalPlatform ships it.
     *
     * @param version the package version, e.g. {@code "1.5"}
     * @return the path of the file below {@link #root()}
     */
    public static Path file(String version) {
        return root().resolve(version).resolve(ENTRY);
    }

    private static List<Path> versionDirectories(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(root)) {
            return entries.filter(dir -> VERSION.matcher(dir.getFileName().toString()).matches())
                    .filter(dir -> Files.isRegularFile(dir.resolve(ENTRY)))
                    .sorted(Comparator.comparingInt(GlobalPlatformExports::major)
                            .thenComparingInt(GlobalPlatformExports::minor))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int major(Path directory) {
        String name = directory.getFileName().toString();
        return Integer.parseInt(name.substring(0, name.indexOf('.')));
    }

    private static int minor(Path directory) {
        String name = directory.getFileName().toString();
        return Integer.parseInt(name.substring(name.indexOf('.') + 1));
    }
}
