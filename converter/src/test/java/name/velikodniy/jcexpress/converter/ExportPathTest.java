package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.resolve.BuiltinExports;
import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.PackageRef;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Cls;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Method;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Pkg;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Export files of imported packages (JCVM 3.1 §4.3.3): given explicitly, found on the export
 * path under {@code <package>/javacard/<last>.exp} (§4.1.1, §5.1, §5.2), and taking precedence over
 * the built-in API data; their package version is what the Import component records (§4.5.2).
 */
class ExportPathTest {

    private static final Path CLASSES = Path.of("target/test-classes");
    private static final String LIB_AID = "A0000000FF01";
    private static final String FRAMEWORK_AID = "A0000000620101";
    private static final String LIB_ENTRY = "com/example/exp/lib/javacard/lib.exp";

    @TempDir
    Path dir;

    private static byte[] libExp(String aidHex) {
        Cls libUtil = new Cls(0, 0x0001, "com/example/exp/lib/LibUtil", List.of("java/lang/Object"), List.of(),
                List.of(), List.of(new Method(0, 0x0001, "<init>", "()V"), new Method(1, 0x0009, "twice", "(S)S"),
                new Method(0, 0x0001, "equals", "(Ljava/lang/Object;)Z"), new Method(1, 0x0001, "next", "()S")), 0);
        return ExpFixture.write(2, 1, new Pkg("com/example/exp/lib", 1, 1, 2, aidHex), List.of(), List.of(libUtil));
    }

    private static Converter.Builder client() {
        return Converter.builder()
                .classesDirectory(CLASSES)
                .packageName("com.example.exp.client")
                .packageAid("A0000000FF02")
                .applet("com.example.exp.client.ClientApplet", "A0000000FF0201");
    }

    private Path write(String relative, byte[] data) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        return Files.write(file, data);
    }

    @Test
    void exportFileIsFoundInAnExportPathDirectory_jcvm31_5_2() throws Exception {
        write("exp/" + LIB_ENTRY, libExp(LIB_AID));

        ConverterResult result = client().exportPath(dir.resolve("exp")).build().convert();

        assertThat(CapInspector.imports(result.capFile())).contains(new PackageRef(1, 2, LIB_AID));
    }

    @Test
    void exportFileIsFoundInAnExportPathJar_jcvm31_5_2() throws Exception {
        Path jar = dir.resolve("lib-exports.jar");
        try (OutputStream out = Files.newOutputStream(jar); var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(LIB_ENTRY));
            zip.write(libExp(LIB_AID));
            zip.closeEntry();
        }

        ConverterResult result = client().exportPath(jar).build().convert();

        assertThat(CapInspector.imports(result.capFile())).contains(new PackageRef(1, 2, LIB_AID));
    }

    @Test
    void firstExportPathEntryWins() throws Exception {
        write("a/" + LIB_ENTRY, libExp(LIB_AID));
        write("b/" + LIB_ENTRY, ExpFixture.write(2, 1, new Pkg("com/example/exp/lib", 1, 1, 7, LIB_AID),
                List.of(), List.of()));

        ConverterResult result = client().exportPath(dir.resolve("a"), dir.resolve("b")).build().convert();

        assertThat(CapInspector.imports(result.capFile())).contains(new PackageRef(1, 2, LIB_AID));
    }

    @Test
    void userExportFileOverridesTheBuiltInApiOfTheSamePackage() throws Exception {
        Path framework15 = write("framework.exp",
                ExpFixture.write(BuiltinExports.getExport("javacard/framework", JavaCardVersion.V3_0_4)));

        ConverterResult result = Converter.builder()
                .classesDirectory(CLASSES)
                .packageName("com.example")
                .packageAid("A00000006203")
                .applet("com.example.TestApplet", "A0000000620301")
                .importExportFile(framework15)
                .build()
                .convert();

        assertThat(CapInspector.imports(result.capFile())).contains(new PackageRef(1, 5, FRAMEWORK_AID));
    }

    @Test
    void twoExportFilesForOnePackageAreRejected() throws Exception {
        Path a = write("a.exp", libExp(LIB_AID));
        Path b = write("b.exp", libExp(LIB_AID));

        assertThatThrownBy(() -> client().importExportFile(a).importExportFile(b).build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("Two export files were supplied for package com.example.exp.lib");
    }

    @Test
    void missingExportPathEntryIsRejected() {
        Path missing = dir.resolve("does-not-exist");

        assertThatThrownBy(() -> client().exportPath(missing).build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("Export path entry does not exist: " + missing);
    }

    @Test
    void exportFileGivenAsExportPathEntryIsRejectedWithAHint_jcvm31_5_2() throws Exception {
        Path lib = write("lib.exp", libExp(LIB_AID));

        assertThatThrownBy(() -> client().exportPath(lib).build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("Export path entry " + lib + " is an export file")
                .hasMessageContaining("importExportFile");
    }

    @Test
    void exportFileOfAnotherPackageAtThePackageLocationIsRejected() throws Exception {
        write("exp/" + LIB_ENTRY, ExpFixture.write(2, 1, new Pkg("com/example/other", 1, 1, 0, "A0000000FF09"),
                List.of(), List.of()));

        assertThatThrownBy(() -> client().exportPath(dir.resolve("exp")).build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("describes package com.example.other, expected com.example.exp.lib");
    }

    @Test
    void twoPackagesWithTheSameAidAreRejected_jcvm31_4_2_2_3() throws Exception {
        Path lib = write("lib.exp", libExp(FRAMEWORK_AID));

        assertThatThrownBy(() -> client().importExportFile(lib).build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("have the same AID " + FRAMEWORK_AID);
    }

    @Test
    void missingExportFileOfAReferencedPackageIsReportedWithTheReference() {
        assertThatThrownBy(() -> client().build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("com.example.exp.client.ClientApplet.process(Ljavacard/framework/APDU;)V")
                .hasMessageContaining("no export file was supplied for package com.example.exp.lib");
    }
}
