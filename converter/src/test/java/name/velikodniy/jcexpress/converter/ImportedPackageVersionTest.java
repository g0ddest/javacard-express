package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.PackageRef;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Cls;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Method;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Pkg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for JCVM 3.1 §4.5.2 / §6.7: the Import component records the version of the
 * imported <em>package</em> (CONSTANT_Package_info of its export file), never the export file
 * format version.
 */
class ImportedPackageVersionTest {

    private static final Path CLASSES = Path.of("target/test-classes");
    private static final String LIB_AID = "A0000000FF01";

    @TempDir
    Path tempDir;

    private static Cls libUtil() {
        return new Cls(0, 0x0001, "com/example/exp/lib/LibUtil", List.of("java/lang/Object"), List.of(),
                List.of(),
                List.of(new Method(0, 0x0001, "<init>", "()V"),
                        new Method(1, 0x0009, "twice", "(S)S"),
                        new Method(0, 0x0001, "equals", "(Ljava/lang/Object;)Z"),
                        new Method(1, 0x0001, "next", "()S")),
                2);
    }

    private ConverterResult convertClient(Path libExp) throws ConverterException {
        return Converter.builder()
                .classesDirectory(CLASSES)
                .packageName("com.example.exp.client")
                .packageAid("A0000000FF02")
                .applet("com.example.exp.client.ClientApplet", "A0000000FF0201")
                .importExportFile(libExp)
                .build()
                .convert();
    }

    @ParameterizedTest(name = "export file format 2.{0}")
    @ValueSource(ints = {1, 3})
    void importComponentRecordsPackageVersionOfUserExportFile_jcvm31_4_5_2(int formatMinor) throws Exception {
        byte[] exp = ExpFixture.write(2, formatMinor,
                new Pkg("com/example/exp/lib", 1, 1, 2, LIB_AID), List.of(), List.of(libUtil()));
        Path expFile = Files.write(tempDir.resolve("lib.exp"), exp);

        ConverterResult result = convertClient(expFile);

        assertThat(CapInspector.imports(result.capFile()))
                .contains(new PackageRef(1, 2, LIB_AID))
                .noneMatch(p -> p.aidHex().equals(LIB_AID) && p.major() == 2);
    }

    @Test
    void libraryConvertedAtVersion12IsImportedAsVersion12() throws Exception {
        ConverterResult lib = Converter.builder()
                .classesDirectory(CLASSES)
                .packageName("com.example.exp.lib")
                .packageAid(LIB_AID)
                .packageVersion(1, 2)
                .generateExport(true)
                .build()
                .convert();
        Path expFile = Files.write(tempDir.resolve("lib.exp"), lib.exportFile());

        ConverterResult client = convertClient(expFile);

        assertThat(CapInspector.imports(client.capFile())).contains(new PackageRef(1, 2, LIB_AID));
    }
}
