package name.velikodniy.jcexpress.converter.translate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs Oracle's off-card verifier (JC 3.0.5u3 {@code verifycap}) as a black box on a CAP file.
 *
 * <p>Only used by tests that are enabled when the SDK is present at
 * {@code build/oracle-sdks/jc305u3_kit} (it is not redistributable and never committed). The
 * verifier is invoked as an external process; no Oracle code is linked or inspected.
 */
public final class OracleVerifier {

    private static final Path SDK = Path.of("../build/oracle-sdks/jc305u3_kit");
    private static final String[] API_EXPORTS = {
            "java/lang/javacard/lang.exp",
            "javacard/framework/javacard/framework.exp",
            "javacard/security/javacard/security.exp",
            "javacardx/crypto/javacard/crypto.exp"
    };

    private OracleVerifier() {}

    /** True if the Oracle SDK is available for black-box verification. */
    public static boolean available() {
        return Files.isDirectory(SDK.resolve("lib"));
    }

    /**
     * Verifies a CAP file and returns the verifier output.
     * The output contains {@code "0 errors"} when the CAP passed verification.
     */
    public static String verify(byte[] cap) throws IOException, InterruptedException {
        return verify(cap, new Path[0]);
    }

    /**
     * Verifies a CAP file that imports packages beyond the four core API packages, with their
     * export files.
     *
     * @param cap          CAP file bytes
     * @param extraExports export files of the other imported packages
     * @return the verifier output
     */
    public static String verify(byte[] cap, Path... extraExports) throws IOException, InterruptedException {
        Path capFile = Files.createTempFile("jcx-verify-", ".cap");
        try {
            Files.write(capFile, cap);
            Path sdk = SDK.toAbsolutePath().normalize();
            List<String> cmd = new ArrayList<>(List.of(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-Djc.home=" + sdk, "-cp", classpath(sdk),
                    "com.sun.javacard.offcardverifier.Verifier"));
            for (String exp : API_EXPORTS) {
                cmd.add(sdk.resolve("api_export_files").resolve(exp).toString());
            }
            for (Path exp : extraExports) {
                cmd.add(exp.toAbsolutePath().toString());
            }
            cmd.add(capFile.toString());
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            return out;
        } finally {
            Files.deleteIfExists(capFile);
        }
    }

    private static String classpath(Path sdk) throws IOException {
        try (Stream<Path> jars = Files.list(sdk.resolve("lib"))) {
            return String.join(":", jars.filter(j -> j.toString().endsWith(".jar"))
                    .map(Path::toString).sorted().toList());
        }
    }
}
