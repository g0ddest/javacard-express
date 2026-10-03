package name.velikodniy.jcexpress.converter.testutil;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Locates the Oracle-generated reference CAP files that the black-box comparison tests read.
 *
 * <p>The files are output of the Oracle Java Card SDK converter (OTN licence, not redistributable).
 * They are never part of the repository: {@code tools/oracle/generate-oracle-refs.sh} writes them into
 * the git-ignored directory {@code build/oracle-refs/} of a developer's checkout, from the converter's own
 * test applets and a locally installed SDK. Tests that need a file call {@link #require(String)}, which
 * skips the test (JUnit assumption) when the file has not been generated.
 *
 * <p>Lookup order: the directory named by the system property {@value #PROPERTY}; otherwise
 * {@code ../build/oracle-refs} (Maven runs module tests in the module directory) and
 * {@code build/oracle-refs} (tests started from the repository root).
 */
public final class OracleReferences {

    /** System property that overrides the reference directory. */
    public static final String PROPERTY = "jcx.oracle.refs";

    private static final List<Path> DEFAULT_DIRECTORIES =
            List.of(Path.of("..", "build", "oracle-refs"), Path.of("build", "oracle-refs"));

    private OracleReferences() {
    }

    /**
     * Returns the directory that holds the generated reference files.
     *
     * @return the directory named by {@value #PROPERTY}, else the first existing default directory, else
     *         the first default directory (which then does not exist)
     */
    public static Path directory() {
        String configured = System.getProperty(PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return DEFAULT_DIRECTORIES.stream().filter(Files::isDirectory).findFirst()
                .orElse(DEFAULT_DIRECTORIES.getFirst());
    }

    /**
     * Reads a reference file if it has been generated.
     *
     * @param fileName file name inside the reference directory, e.g. {@code oracle-TestApplet-jc305.cap}
     * @return the file content, or empty when the file does not exist
     */
    public static Optional<byte[]> find(String fileName) {
        Path file = directory().resolve(fileName);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read Oracle reference " + file, e);
        }
    }

    /**
     * Reads a reference file, or skips the calling test when it has not been generated.
     *
     * @param fileName file name inside the reference directory
     * @return the file content
     */
    public static byte[] require(String fileName) {
        Optional<byte[]> content = find(fileName);
        Assumptions.assumeTrue(content.isPresent(), () -> "Oracle reference " + fileName + " not generated in "
                + directory().toAbsolutePath().normalize() + " (run tools/oracle/generate-oracle-refs.sh)");
        return content.orElseThrow();
    }
}
