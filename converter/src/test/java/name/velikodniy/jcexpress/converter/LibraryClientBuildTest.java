package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.resolve.ImportLoader;
import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.ExternalRef;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.PackageRef;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A library package and an applet package that imports it, both converted by this converter:
 * the library's generated export file (JCVM 3.1 Chapter 5) is found on the export path at
 * {@code <package>/javacard/<last>.exp} (§4.1.1, §5.2), the client records the library's package
 * version (§4.5.2) and links against the tokens the export file describes (§4.3.3).
 */
class LibraryClientBuildTest {

    private static final Path CLASSES = Path.of("target/test-classes");
    private static final String LIB_AID = "A0000000FF01";
    private static final int STATIC_METHOD_REF = 6;

    @TempDir
    Path exportPath;

    @ParameterizedTest
    @EnumSource(value = JavaCardVersion.class, names = {"V2_2_2", "V3_0_4", "V3_0_5", "V3_1_0", "V3_2_0"})
    void clientLinksAgainstTheGeneratedLibraryExportFile(JavaCardVersion version) throws Exception {
        ConverterResult library = Converter.builder()
                .classesDirectory(CLASSES).packageName("com.example.exp.lib")
                .packageAid(LIB_AID).packageVersion(1, 2)
                .javaCardVersion(version).generateExport(true)
                .build().convert();
        Path expFile = exportPath.resolve(ImportLoader.exportFileEntry("com/example/exp/lib"));
        Files.createDirectories(expFile.getParent());
        Files.write(expFile, library.exportFile());

        ConverterResult client = Converter.builder()
                .classesDirectory(CLASSES).packageName("com.example.exp.client")
                .packageAid("A0000000FF02").applet("com.example.exp.client.ClientApplet", "A0000000FF0201")
                .javaCardVersion(version).exportPath(exportPath)
                .build().convert();

        ExportFile.ClassExport util = ExportFileReader.read(library.exportFile()).findClass("LibUtil");
        int twice = util.methods().stream().filter(m -> m.name().equals("twice")).findFirst().orElseThrow().token();
        assertThat(CapInspector.imports(client.capFile())).contains(new PackageRef(1, 2, LIB_AID));
        assertThat(CapInspector.externalRefs(client.capFile()))
                .contains(new ExternalRef(STATIC_METHOD_REF, LIB_AID, util.token(), twice));
    }
}
