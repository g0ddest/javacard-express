package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterResult;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.testutil.ExportFileRules;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every export file the converter generates for the test packages satisfies the rules of
 * JCVM 3.1 Chapter 5 and the token numbering of §4.3.7, as checked by the independent
 * {@link ExportFileRules}.
 */
class GeneratedExportFilesTest {

    private static final Path CLASSES = Path.of("target/test-classes");

    private static List<String> violations(ConverterResult result, boolean library, JavaCardVersion version)
            throws Exception {
        return ExportFileRules.check(ExportFileReader.read(result.exportFile()), library,
                version.exportFormatMinor());
    }

    @ParameterizedTest
    @EnumSource(JavaCardVersion.class)
    void libraryExportFileConforms(JavaCardVersion version) throws Exception {
        ConverterResult result = Converter.builder()
                .classesDirectory(CLASSES)
                .packageName("com.example.exp.lib")
                .packageAid("A0000000FF01")
                .packageVersion(1, 2)
                .javaCardVersion(version)
                .build().convert();

        assertThat(violations(result, true, version))
                .as("violations of the library export file for %s", version)
                .isEmpty();
    }

    static Stream<Arguments> appletPackages() {
        return Stream.of(
                Arguments.of("com.example", "A00000006203", "com.example.TestApplet"),
                Arguments.of("com.example.iface", "A00000006204", "com.example.iface.InterfaceApplet"),
                Arguments.of("com.example.multiclass", "A00000006205", "com.example.multiclass.MultiClassApplet"),
                Arguments.of("com.example.inherit", "A00000006206", "com.example.inherit.InheritanceApplet"));
    }

    @ParameterizedTest
    @MethodSource("appletPackages")
    void appletPackageExportFileConforms(String pkg, String aid, String applet) throws Exception {
        for (JavaCardVersion version : List.of(JavaCardVersion.V3_0_5, JavaCardVersion.V3_1_0)) {
            ConverterResult result = Converter.builder()
                    .classesDirectory(CLASSES)
                    .packageName(pkg)
                    .packageAid(aid)
                    .applet(applet, aid + "01")
                    .javaCardVersion(version)
                    .build().convert();

            assertThat(violations(result, false, version)).as("%s for %s", pkg, version).isEmpty();
        }
    }
}
