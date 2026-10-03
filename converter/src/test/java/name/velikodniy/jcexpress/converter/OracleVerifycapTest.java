package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.TestFixtures.Fixture;
import name.velikodniy.jcexpress.converter.capcheck.CapImage;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs Oracle's off-card verifier (black box) on the DEFAULT output of the converter - the mode
 * the Maven plugin uses - for every converter test package.
 *
 * <p>The test needs a Java Card 3.0.5 development kit at {@code build/oracle-sdks/jc305u3_kit}
 * (not part of the repository; it is skipped otherwise). The kit's verifier understands CAP
 * format 2.1, so the packages are converted for {@link JavaCardVersion#V3_0_5}. The structural
 * rules of JCVM 3.1 Chapter 6 are checked without the kit, in every build, by
 * {@link name.velikodniy.jcexpress.converter.capcheck.CapInvariantsTest}.
 *
 * <p>Packages listed in {@link #KNOWN_FAILURES} fail for reasons outside the CAP structure
 * written by the Class/Descriptor/StaticField/Export generators (bytecode translation, the
 * export file writer). For them the test asserts the documented error, so a fix shows up as a
 * test failure that asks to remove the entry.
 */
class OracleVerifycapTest {

    private static final Path SDK_PATH = Path.of("../build/oracle-sdks/jc305u3_kit");
    private static final String PASSED = "Verification completed with 0 warnings and 0 errors";

    /**
     * Label to the verifier message expected until the cause (owned elsewhere) is fixed. Empty
     * since the converter branches were integrated: every test package verifies.
     */
    private static final Map<String, String> KNOWN_FAILURES = Map.of();

    @TempDir
    Path tmp;

    static boolean oracleSdkAvailable() {
        return Files.isDirectory(SDK_PATH.resolve("lib"));
    }

    static Stream<Fixture> packages() {
        return Stream.concat(TestFixtures.APPLETS.stream(), Stream.of(TestFixtures.LIBRARY));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packages")
    @EnabledIf("oracleSdkAvailable")
    void defaultModeCapPassesOracleVerifier(Fixture fixture) throws Exception {
        ConverterResult result = fixture.convert();

        String output = verify(result);

        String knownFailure = KNOWN_FAILURES.get(fixture.label());
        if (knownFailure == null) {
            assertThat(output).as("verifycap %s:%n%s", fixture.label(), output).contains(PASSED);
        } else {
            assertThat(output).as("known failure of %s (remove it from KNOWN_FAILURES once it verifies):%n%s",
                    fixture.label(), output).contains(knownFailure).doesNotContain(PASSED);
        }
    }

    /** Runs the verifier on the CAP file (and the package's own export file if it exports). */
    private String verify(ConverterResult result) throws IOException, InterruptedException {
        Path cap = Files.write(tmp.resolve("package.cap"), result.capFile());
        Path sdk = SDK_PATH.toAbsolutePath().normalize();
        Path exports = sdk.resolve("api_export_files");
        List<String> cmd = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djc.home=" + sdk, "-cp", classpath(sdk),
                "com.sun.javacard.offcardverifier.Verifier",
                exports.resolve("java/lang/javacard/lang.exp").toString(),
                exports.resolve("javacard/framework/javacard/framework.exp").toString(),
                exports.resolve("javacard/security/javacard/security.exp").toString(),
                exports.resolve("javacardx/crypto/javacard/crypto.exp").toString()));
        if (CapImage.parse(result.capFile()).has(CapImage.TAG_EXPORT)) {
            cmd.add(Files.write(tmp.resolve("package.exp"), result.exportFile()).toString());
        }
        cmd.add(cap.toString());
        Path log = tmp.resolve("verifier.log");
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean finished = process.waitFor(2, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
        }
        assertThat(finished).as("verifier finished within 2 minutes").isTrue();
        return Files.readString(log, StandardCharsets.UTF_8);
    }

    private static String classpath(Path sdk) throws IOException {
        try (Stream<Path> jars = Files.list(sdk.resolve("lib"))) {
            return String.join(File.pathSeparator,
                    jars.filter(p -> p.toString().endsWith(".jar")).sorted().map(Path::toString).toList());
        }
    }
}
