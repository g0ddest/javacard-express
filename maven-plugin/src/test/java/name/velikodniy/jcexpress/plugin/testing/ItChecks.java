package name.velikodniy.jcexpress.plugin.testing;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Helpers for the {@code verify.groovy} scripts of the integration tests in {@code src/it}
 * (maven-invoker-plugin puts the test class path on the script class path).
 */
public final class ItChecks {

    private ItChecks() {
    }

    /**
     * Reads the Maven output of an integration-test build.
     *
     * @param basedir the IT project directory
     * @return the content of {@code build.log}
     */
    public static String buildLog(File basedir) {
        return read(basedir.toPath().resolve("build.log"));
    }

    /**
     * Fails unless the Maven output of an integration-test build contains every fragment.
     *
     * @param basedir   the IT project directory
     * @param fragments expected text fragments
     */
    public static void assertLogContains(File basedir, String... fragments) {
        String log = buildLog(basedir);
        for (String fragment : fragments) {
            if (!log.contains(fragment)) {
                throw new AssertionError("build.log of " + basedir.getName() + " lacks: " + fragment);
            }
        }
    }

    /**
     * Fails if the Maven output of an integration-test build contains any of the fragments.
     *
     * @param basedir   the IT project directory
     * @param fragments unexpected text fragments
     */
    public static void assertLogLacks(File basedir, String... fragments) {
        String log = buildLog(basedir);
        for (String fragment : fragments) {
            int at = log.indexOf(fragment);
            if (at >= 0) {
                int lineStart = log.lastIndexOf('\n', at) + 1;
                int lineEnd = log.indexOf('\n', at);
                throw new AssertionError("build.log of " + basedir.getName() + " contains: "
                        + log.substring(lineStart, lineEnd < 0 ? log.length() : lineEnd));
            }
        }
    }

    /**
     * Reads a CAP file produced by an integration-test build and checks the structural rules of
     * {@link CapInvariants}.
     *
     * @param basedir      the IT project directory
     * @param relativePath path below the project, e.g. {@code target/hello-1.0.cap}
     * @return the parsed CAP file
     */
    public static CapFile cap(File basedir, String relativePath) {
        Path file = basedir.toPath().resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new AssertionError("Expected CAP file " + file + " was not produced");
        }
        CapFile cap = CapFile.read(file);
        CapInvariants.check(cap);
        return cap;
    }

    /**
     * Runs the Oracle off-card verifier on a CAP file if an SDK is installed, and writes its
     * output to {@code verifycap.log} in the IT directory.
     *
     * @param basedir     the IT project directory
     * @param mode        {@code report} (log only), {@code strict} (fail on errors) or {@code skip}
     * @param sdkHome     SDK location ({@code build/oracle-sdks/jc305u3_kit})
     * @param cap         the CAP file
     * @param exportFiles export files the verifier needs besides the API exports
     * @return {@code true} if the verifier ran and passed, {@code false} otherwise
     */
    public static boolean verifycap(File basedir, String mode, String sdkHome, File cap, File... exportFiles) {
        if ("skip".equals(mode)) {
            return false;
        }
        OracleVerifier verifier = OracleVerifier.at(sdkHome);
        Path log = basedir.toPath().resolve("verifycap.log");
        if (verifier == null) {
            write(log, "verifycap skipped: no Oracle SDK at " + sdkHome);
            return false;
        }
        List<Path> exps = Arrays.stream(exportFiles).map(File::toPath).toList();
        OracleVerifier.Result result = verifier.verify(cap.toPath(), exps);
        write(log, cap + "\n" + result.output());
        System.out.println("[verifycap " + (result.passed() ? "PASS" : "FAIL") + "] " + cap.getName()
                + (result.passed() ? "" : "\n" + result.output()));
        if ("strict".equals(mode) && !result.passed()) {
            throw new AssertionError("Oracle verifycap rejected " + cap + ":\n" + result.output());
        }
        return result.passed();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.writeString(file, text + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
