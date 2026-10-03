package name.velikodniy.jcexpress.plugin.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs the off-card verifier of an Oracle Java Card SDK (3.0.5) as a black box on a CAP file.
 *
 * <p>The SDK is never redistributed nor inspected: the verifier is started as an external
 * process with the SDK's own API export files, and only its textual verdict is used. Tests that
 * use this class are skipped when no SDK is installed.
 */
public final class OracleVerifier {

    private static final String MAIN_CLASS = "com.sun.javacard.offcardverifier.Verifier";
    private static final Pattern ERRORS = Pattern.compile("(\\d+) errors?");
    private static final List<String> API_EXPORTS = List.of(
            "java/lang/javacard/lang.exp",
            "javacard/framework/javacard/framework.exp",
            "javacard/security/javacard/security.exp",
            "javacardx/crypto/javacard/crypto.exp");

    private final Path sdk;

    private OracleVerifier(Path sdk) {
        this.sdk = sdk;
    }

    /**
     * Returns a verifier for the SDK at the given location, if one is installed there.
     *
     * @param sdkHome SDK directory (e.g. {@code build/oracle-sdks/jc305u3_kit}), may be {@code null}
     * @return the verifier, or {@code null} if the SDK is absent
     */
    public static OracleVerifier at(String sdkHome) {
        if (sdkHome == null || sdkHome.isBlank()) {
            return null;
        }
        Path sdk = Path.of(sdkHome).toAbsolutePath().normalize();
        return Files.isDirectory(sdk.resolve("lib")) && Files.isDirectory(sdk.resolve("api_export_files"))
                ? new OracleVerifier(sdk) : null;
    }

    /**
     * Verifies a CAP file.
     *
     * @param cap         the CAP file
     * @param exportFiles export files of the package itself (needed when the CAP has an Export
     *                    component) and of imported non-API packages
     * @return the verdict
     */
    public Result verify(Path cap, List<Path> exportFiles) {
        List<String> command = new ArrayList<>(List.of(javaExecutable(), "-Djc.home=" + sdk,
                "-cp", classpath(), MAIN_CLASS));
        API_EXPORTS.forEach(exp -> command.add(sdk.resolve("api_export_files").resolve(exp).toString()));
        exportFiles.forEach(exp -> command.add(exp.toAbsolutePath().toString()));
        command.add(cap.toAbsolutePath().toString());
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("verifier timed out on " + cap);
            }
            return new Result(output.strip());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private String classpath() {
        try (Stream<Path> jars = Files.list(sdk.resolve("lib"))) {
            return String.join(java.io.File.pathSeparator, jars
                    .filter(p -> p.toString().endsWith(".jar")).sorted().map(Path::toString).toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    /**
     * Verifier output.
     *
     * @param output complete console output of the verifier
     */
    public record Result(String output) {

        /** @return {@code true} if the verifier reported zero errors */
        public boolean passed() {
            Matcher m = ERRORS.matcher(output);
            int errors = -1;
            while (m.find()) {
                errors = Integer.parseInt(m.group(1));
            }
            return errors == 0;
        }
    }
}
