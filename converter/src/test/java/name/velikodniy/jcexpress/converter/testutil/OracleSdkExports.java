package name.velikodniy.jcexpress.converter.testutil;

import name.velikodniy.jcexpress.converter.JavaCardVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Locates the API export files of locally installed Oracle Java Card Development Kits for
 * <em>black-box</em> comparison tests. Nothing is copied into the repository: tests that use
 * this class are skipped when the kits are absent (for example on CI).
 *
 * <p>The kits are looked up under {@code ../build/oracle-sdks} (relative to the converter
 * module) or the directory given by the system property {@code jcx.oracle.sdks}. For JC 3.1 and
 * 3.2 the export files are read from the kit's {@code lib/tools.jar}.
 */
public final class OracleSdkExports {

    private OracleSdkExports() {}

    /** Root directory that contains the {@code jcNNN_kit} directories. */
    public static Path root() {
        return Path.of(System.getProperty("jcx.oracle.sdks", "../build/oracle-sdks"));
    }

    /**
     * Returns {@code true} if the export files of every supported version are available.
     *
     * @return whether all eight kits are installed
     */
    public static boolean allAvailable() {
        for (JavaCardVersion v : JavaCardVersion.values()) {
            if (!Files.exists(location(v).container())) return false;
        }
        return true;
    }

    /**
     * Reads all public API export files of the kit that corresponds to {@code version}
     * (implementation packages {@code com/sun/...} are skipped).
     *
     * @param version the Java Card version
     * @return export file bytes keyed by their path inside the export directory
     */
    public static Map<String, byte[]> read(JavaCardVersion version) {
        Location loc = location(version);
        try {
            if (loc.container().toString().endsWith(".jar")) {
                try (FileSystem fs = FileSystems.newFileSystem(loc.container())) {
                    return collect(fs.getPath(loc.inner()));
                }
            }
            return collect(loc.container());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<String, byte[]> collect(Path dir) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".exp")).toList()) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                if (!rel.startsWith("com/sun/")) {
                    result.put(rel, Files.readAllBytes(p));
                }
            }
        }
        return result;
    }

    private record Location(Path container, String inner) {}

    private static Location location(JavaCardVersion version) {
        Path r = root();
        return switch (version) {
            case V2_1_2 -> new Location(r.resolve("jc212_kit/api21_export_files"), "");
            case V2_2_1 -> new Location(r.resolve("jc221_kit/api_export_files"), "");
            case V2_2_2 -> new Location(r.resolve("jc222_kit/api_export_files"), "");
            case V3_0_3 -> new Location(r.resolve("jc303_kit/api_export_files"), "");
            case V3_0_4 -> new Location(r.resolve("jc304_kit/api_export_files"), "");
            case V3_0_5 -> new Location(r.resolve("jc305u3_kit/api_export_files"), "");
            case V3_1_0 -> new Location(r.resolve("jc310r20210706_kit/lib/tools.jar"), "/api_export_files_3.1.0");
            case V3_2_0 -> new Location(r.resolve("jc320v25.1_kit/lib/tools.jar"), "/api_export_files_3.2.0");
        };
    }
}
