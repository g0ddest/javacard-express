package name.velikodniy.jcexpress.converter;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static name.velikodniy.jcexpress.converter.CapTestUtils.COMPONENTS;
import static name.velikodniy.jcexpress.converter.CapTestUtils.extractComponents;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Oracle compatibility mode ({@code oracleCompatibility(true)})
 * produces byte-identical CAP output to Oracle's converter for all test applets
 * and JavaCard versions.
 *
 * <p>Oracle compatibility mode replicates the Oracle converter's dispatch table
 * off-by-one bug in the Class component (JCVM spec §6.9 Table 6-16), resulting
 * in fully byte-identical output across all 10 CAP components.
 *
 * @see Converter.Builder#oracleCompatibility(boolean)
 */
class OracleCompatModeTest {

    record AppletConfig(String label, String packageName, String packageAid,
                        String appletClass, String appletAid, String oracleRefFile) {}

    static Stream<AppletConfig> appletConfigs() {
        return Stream.of(
                new AppletConfig("TestApplet",
                        "com.example", "A000000062010101",
                        "com.example.TestApplet", "A00000006201010101",
                        "oracle-TestApplet-jc305.cap"),
                new AppletConfig("InheritanceApplet",
                        "com.example.inherit", "A000000062060101",
                        "com.example.inherit.InheritanceApplet", "A00000006206010101",
                        "oracle-InheritanceApplet.cap"),
                new AppletConfig("InterfaceApplet",
                        "com.example.iface", "A000000062040101",
                        "com.example.iface.InterfaceApplet", "A00000006204010101",
                        "oracle-InterfaceApplet.cap"),
                new AppletConfig("ExceptionApplet",
                        "com.example.exception", "A000000062050101",
                        "com.example.exception.ExceptionApplet", "A00000006205010101",
                        "oracle-ExceptionApplet.cap"),
                new AppletConfig("MultiClassApplet",
                        "com.example.multiclass", "A000000062030101",
                        "com.example.multiclass.MultiClassApplet", "A00000006203010101",
                        "oracle-MultiClassApplet.cap")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("appletConfigs")
    void oracleCompatModeProducesByteIdenticalOutput(AppletConfig config) throws Exception {
        InputStream refStream = getClass().getResourceAsStream("/reference/" + config.oracleRefFile());
        Assumptions.assumeTrue(refStream != null,
                "Oracle reference not found: " + config.oracleRefFile());

        byte[] oracleBytes;
        try (refStream) { oracleBytes = refStream.readAllBytes(); }

        ConverterResult result = Converter.builder()
                .classesDirectory(Path.of("target/test-classes"))
                .packageName(config.packageName())
                .packageAid(config.packageAid())
                .packageVersion(1, 0)
                .applet(config.appletClass(), config.appletAid())
                .oracleCompatibility(true)
                .build()
                .convert();

        Map<String, byte[]> ours = extractComponents(result.capFile());
        Map<String, byte[]> oracle = extractComponents(oracleBytes);

        for (String name : COMPONENTS) {
            byte[] ourComp = ours.get(name);
            byte[] oracleComp = oracle.get(name);
            if (ourComp == null || oracleComp == null) continue;

            assertThat(ourComp)
                    .as("%s byte-identical for %s (oracle compat)", name, config.label())
                    .isEqualTo(oracleComp);
        }
    }

    @ParameterizedTest(name = "JC {0}")
    @EnumSource(JavaCardVersion.class)
    void testAppletAllVersionsByteIdentical(JavaCardVersion version) throws Exception {
        String refFile = switch (version) {
            case V2_1_2 -> "oracle-TestApplet-jc212.cap";
            case V2_2_1 -> "oracle-TestApplet-jc221.cap";
            case V2_2_2 -> "oracle-TestApplet-jc222.cap";
            case V3_0_3 -> "oracle-TestApplet-jc303.cap";
            case V3_0_4 -> "oracle-TestApplet-jc304.cap";
            case V3_0_5 -> "oracle-TestApplet-jc305.cap";
            case V3_1_0 -> "oracle-TestApplet-jc310.cap";
            case V3_2_0 -> "oracle-TestApplet-jc320.cap";
        };

        InputStream refStream = getClass().getResourceAsStream("/reference/" + refFile);
        Assumptions.assumeTrue(refStream != null, "Oracle reference not found: " + refFile);

        byte[] oracleBytes;
        try (refStream) { oracleBytes = refStream.readAllBytes(); }

        ConverterResult result = Converter.builder()
                .classesDirectory(Path.of("target/test-classes"))
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .oracleCompatibility(true)
                .javaCardVersion(version)
                .build()
                .convert();

        Map<String, byte[]> ours = extractComponents(result.capFile());
        Map<String, byte[]> oracle = extractComponents(oracleBytes);

        for (String name : COMPONENTS) {
            byte[] ourComp = ours.get(name);
            byte[] oracleComp = oracle.get(name);
            if (ourComp == null || oracleComp == null) continue;

            assertThat(ourComp)
                    .as("%s byte-identical for TestApplet %s (oracle compat)", name, version)
                    .isEqualTo(oracleComp);
        }
    }

}
