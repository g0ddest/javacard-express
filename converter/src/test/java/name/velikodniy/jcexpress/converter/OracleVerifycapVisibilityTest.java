package name.velikodniy.jcexpress.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates CAP files and verifies them with Oracle's off-card verifier (verifycap).
 * Uses oracleCompatibility mode since verifycap expects the Oracle dispatch table layout.
 *
 * <p>This test requires the Oracle SDK at build/oracle-sdks/jc305u3_kit.
 */
class OracleVerifycapVisibilityTest {

    private static final Path SDK_PATH = Path.of("../build/oracle-sdks/jc305u3_kit");
    private static final Path CLASSES_DIR = Path.of("target/test-classes");

    static boolean oracleSdkAvailable() {
        return Files.isDirectory(SDK_PATH.resolve("lib"));
    }

    @Test
    @EnabledIf("oracleSdkAvailable")
    void testAppletPassesOracleVerifycap() throws Exception {
        ConverterResult result = Converter.builder()
                .classesDirectory(CLASSES_DIR)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .oracleCompatibility(true)
                .build()
                .convert();

        String output = runVerifycap(result.capFile());
        System.out.println("=== TestApplet verifycap ===");
        System.out.println(output);
        assertThat(output).as("TestApplet verifycap").contains("0 errors");
    }

    @Test
    @EnabledIf("oracleSdkAvailable")
    void visibilityAppletPassesOracleVerifycap() throws Exception {
        ConverterResult result = Converter.builder()
                .classesDirectory(CLASSES_DIR)
                .packageName("com.example.visibility")
                .packageAid("A000000062010102")
                .packageVersion(1, 0)
                .applet("com.example.visibility.VisibilityApplet", "A00000006201010201")
                .oracleCompatibility(true)
                .build()
                .convert();

        String output = runVerifycap(result.capFile());
        System.out.println("=== VisibilityApplet verifycap ===");
        System.out.println(output);
        assertThat(output).as("VisibilityApplet verifycap").contains("0 errors");
    }

    private String runVerifycap(byte[] capData) throws Exception {
        Path capFile = Files.createTempFile("test-applet-", ".cap");
        Files.write(capFile, capData);
        try {
            Path sdkAbsolute = SDK_PATH.toAbsolutePath().normalize();
            List<Path> jars = new ArrayList<>();
            try (var stream = Files.list(sdkAbsolute.resolve("lib"))) {
                stream.filter(p -> p.toString().endsWith(".jar")).forEach(jars::add);
            }
            String classpath = String.join(":", jars.stream().map(Path::toString).toList());

            Path exportDir = sdkAbsolute.resolve("api_export_files");
            List<String> cmd = new ArrayList<>();
            cmd.add("java");
            cmd.add("-Djc.home=" + sdkAbsolute);
            cmd.add("-cp");
            cmd.add(classpath);
            cmd.add("com.sun.javacard.offcardverifier.Verifier");
            cmd.add(exportDir.resolve("java/lang/javacard/lang.exp").toString());
            cmd.add(exportDir.resolve("javacard/framework/javacard/framework.exp").toString());
            cmd.add(capFile.toString());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().reduce("", (a, b) -> a + "\n" + b).trim();
            }
            process.waitFor();
            return output;
        } finally {
            Files.deleteIfExists(capFile);
        }
    }
}
