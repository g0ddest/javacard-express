package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.PackageRef;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Converter options that cannot be represented in a CAP or export file are rejected when they
 * are configured, instead of producing a malformed file (fail-closed).
 *
 * <ul>
 *   <li>The package has a name: it locates the CAP components ({@code <package>/javacard/},
 *       JCVM 3.1 §4.1.3) and names the package in its export file (§4.1.1, §5.6.1). Classes of
 *       the unnamed package used to give JAR entries with a leading {@code '/'} and an export file
 *       for the package {@code ""}.</li>
 *   <li>The fully qualified name has at most 255 bytes in UTF-8 (§2.2.4.1.3; u1
 *       {@code name_length} of {@code package_name_info}, §6.4).</li>
 *   <li>Major and minor package versions are u1 items (§4.5, §6.4, §5.6.1): 0 to 255.</li>
 * </ul>
 */
class BuilderValidationTest {

    private static final Path CLASSES = Path.of("target/test-classes");

    @TempDir
    Path probe;

    @Test
    void unnamedPackageIsRejected_jcvm31_4_1_3_5_6_1() {
        assertThatThrownBy(() -> Converter.builder().packageName(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unnamed package");
    }

    @Test
    void appletInTheUnnamedPackageCannotBeConverted_jcvm31_4_1_3() {
        JavaSources.compile(probe, Map.of("RootApplet", """
                import javacard.framework.*;
                public class RootApplet extends Applet {
                    public static void install(byte[] b, short o, byte l) { new RootApplet().register(); }
                    public void process(APDU apdu) { }
                }
                """));

        assertThatThrownBy(() -> Converter.builder()
                .classesDirectory(probe)
                .packageName("")
                .packageAid("A000000FFE40")
                .applet("RootApplet", "A000000FFE4001")
                .build().convert())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unnamed package");
    }

    @ParameterizedTest
    @ValueSource(strings = {"com..example", ".com.example", "com.example.", "com.1st", "com.exa-mple", "com example",
            "com/example/"})
    void malformedPackageNamesAreRejected(String name) {
        assertThatThrownBy(() -> Converter.builder().packageName(name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid package name '" + name + "'");
    }

    @Test
    void packageNameOfMoreThan255Utf8BytesIsRejected_jcvm31_2_2_4_1_3() {
        String name255 = "p".repeat(255);
        String name256 = "p".repeat(256);
        // 128 two-byte UTF-8 characters: 128 characters, 256 bytes
        String twoByteChars = "é".repeat(128);

        assertThatCode(() -> Converter.builder().packageName(name255)).doesNotThrowAnyException();
        assertThatThrownBy(() -> Converter.builder().packageName(name256))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 bytes");
        assertThatThrownBy(() -> Converter.builder().packageName(twoByteChars))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 bytes");
    }

    @Test
    void slashSeparatedPackageNameIsTheSamePackage() throws Exception {
        ConverterResult dotted = Converter.builder()
                .classesDirectory(CLASSES).packageName("com.example.multiclass")
                .build().convert();
        ConverterResult slashed = Converter.builder()
                .classesDirectory(CLASSES).packageName("com/example/multiclass")
                .build().convert();

        String generated = HexFormat.of().withUpperCase()
                .formatHex(Converter.Builder.generateAid("com.example.multiclass"));
        assertThat(CapInspector.headerPackage(slashed.capFile()).aidHex()).isEqualTo(generated);
        assertThat(CapInspector.components(slashed.capFile()))
                .containsOnlyKeys(CapInspector.components(dotted.capFile()).keySet());
        assertThat(ExportFileReader.read(slashed.exportFile()).packageName()).isEqualTo("com/example/multiclass");
    }

    @ParameterizedTest
    @CsvSource({"256, 0", "1, 256", "-1, 0", "0, -1"})
    void packageVersionOutsideU1IsRejected_jcvm31_4_5_6_4(int major, int minor) {
        assertThatThrownBy(() -> Converter.builder().packageVersion(major, minor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 to 255");
    }

    @Test
    void packageVersion255Point255IsWrittenUnchanged_jcvm31_6_4() throws Exception {
        ConverterResult result = Converter.builder()
                .classesDirectory(CLASSES).packageName("com.example").packageAid("A00000006203")
                .packageVersion(255, 255)
                .applet("com.example.TestApplet", "A0000000620301")
                .build().convert();

        assertThat(CapInspector.headerPackage(result.capFile())).isEqualTo(new PackageRef(255, 255, "A00000006203"));
        assertThat(ExportFileReader.read(result.exportFile()).majorVersion()).isEqualTo(255);
    }
}
