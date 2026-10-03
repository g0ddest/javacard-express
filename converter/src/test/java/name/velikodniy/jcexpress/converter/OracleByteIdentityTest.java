package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.testutil.OracleReferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static name.velikodniy.jcexpress.converter.CapTestUtils.COMPONENTS;
import static name.velikodniy.jcexpress.converter.CapTestUtils.extractComponents;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compares the DEFAULT output of the converter (the mode the Maven plugin uses) with CAP files
 * produced by Oracle's converter for the same applets (black-box reference output, skipped when
 * the reference files are not available).
 *
 * <p>Both converters follow the {@code class_info} layout of JCVM 3.1 §6.9.2, so every component
 * is byte-identical, including the Class component. The deprecated
 * {@code oracleCompatibility(true)} option has no effect.
 *
 * <p>CryptoApplet: the constant pool order differs from Oracle's. JCVM 3.1 §6.8 does not
 * prescribe an order of constant pool entries (only that catch types are not at index 0), so the
 * Constant Pool, Method (CP indices in operands) and Descriptor ({@code constant_pool_types})
 * components may differ; the constant pool must hold the same entries.
 */
class OracleByteIdentityTest {

    record AppletConfig(String label, String packageName, String packageAid,
                        String appletClass, String appletAid, String oracleRefFile,
                        Set<String> cpOrderDependent) {}

    private static final Set<String> CP_ORDER_DEPENDENT =
            Set.of("ConstantPool.cap", "Method.cap", "Descriptor.cap");

    static Stream<AppletConfig> appletConfigs() {
        return Stream.of(
                applet("TestApplet", "com.example", "TestApplet", "A000000062010101",
                        "oracle-TestApplet-jc305.cap", Set.of()),
                applet("InheritanceApplet", "com.example.inherit", "InheritanceApplet", "A000000062060101",
                        "oracle-InheritanceApplet.cap", Set.of()),
                applet("InterfaceApplet", "com.example.iface", "InterfaceApplet", "A000000062040101",
                        "oracle-InterfaceApplet.cap", Set.of()),
                applet("ExceptionApplet", "com.example.exception", "ExceptionApplet", "A000000062050101",
                        "oracle-ExceptionApplet.cap", Set.of()),
                applet("MultiClassApplet", "com.example.multiclass", "MultiClassApplet", "A000000062030101",
                        "oracle-MultiClassApplet.cap", Set.of()),
                applet("CryptoApplet", "com.example.crypto", "CryptoApplet", "A000000062070101",
                        "oracle-CryptoApplet.cap", CP_ORDER_DEPENDENT));
    }

    private static AppletConfig applet(String label, String pkg, String simpleName, String pkgAid,
                                       String reference, Set<String> cpOrderDependent) {
        return new AppletConfig(label, pkg, pkgAid, pkg + "." + simpleName, pkgAid + "01", reference,
                cpOrderDependent);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("appletConfigs")
    void defaultModeMatchesOracleOutput(AppletConfig config) throws Exception {
        byte[] oracleBytes = reference(config.oracleRefFile());

        ConverterResult result = Converter.builder()
                .classesDirectory(Path.of("target/test-classes"))
                .packageName(config.packageName())
                .packageAid(config.packageAid())
                .packageVersion(1, 0)
                .applet(config.appletClass(), config.appletAid())
                .build()
                .convert();

        Map<String, byte[]> ours = extractComponents(result.capFile());
        Map<String, byte[]> oracle = extractComponents(oracleBytes);
        assertThat(ours.keySet()).as("components of %s", config.label()).isEqualTo(oracle.keySet());
        for (String name : COMPONENTS) {
            if (!oracle.containsKey(name)) {
                continue;
            }
            if (config.cpOrderDependent().contains(name)) {
                assertThat(ours.get(name)).as("%s size for %s", name, config.label())
                        .hasSameSizeAs(oracle.get(name));
            } else {
                assertThat(ours.get(name)).as("%s byte-identical for %s", name, config.label())
                        .isEqualTo(oracle.get(name));
            }
        }
        if (config.cpOrderDependent().contains("ConstantPool.cap")) {
            assertThat(cpEntries(ours.get("ConstantPool.cap")))
                    .as("same constant pool entries in another order for %s", config.label())
                    .containsExactlyInAnyOrderElementsOf(cpEntries(oracle.get("ConstantPool.cap")));
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
        byte[] oracleBytes = reference(refFile);

        ConverterResult result = Converter.builder()
                .classesDirectory(Path.of("target/test-classes"))
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .javaCardVersion(version)
                .build()
                .convert();

        Map<String, byte[]> ours = extractComponents(result.capFile());
        Map<String, byte[]> oracle = extractComponents(oracleBytes);
        for (String name : COMPONENTS) {
            if (oracle.containsKey(name)) {
                assertThat(ours.get(name))
                        .as("%s byte-identical for TestApplet %s", name, version)
                        .isEqualTo(oracle.get(name));
            }
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedOracleCompatibilityOptionHasNoEffect() throws Exception {
        Converter.Builder base = Converter.builder()
                .classesDirectory(Path.of("target/test-classes"))
                .packageName("com.example.inherit")
                .packageAid("A000000062060101")
                .applet("com.example.inherit.InheritanceApplet", "A00000006206010101");
        byte[] defaults = base.build().convert().capFile();
        byte[] compat = base.oracleCompatibility(true).build().convert().capFile();

        assertThat(extractComponents(compat).get("Class.cap"))
                .isEqualTo(extractComponents(defaults).get("Class.cap"));
    }

    private static byte[] reference(String file) {
        return OracleReferences.require(file);
    }

    private static List<String> cpEntries(byte[] component) {
        int count = ((component[3] & 0xFF) << 8) | (component[4] & 0xFF);
        List<String> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int o = 5 + 4 * i;
            entries.add(String.format("%02x%02x%02x%02x", component[o], component[o + 1], component[o + 2],
                    component[o + 3]));
        }
        return entries;
    }
}
