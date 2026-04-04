package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassFileReader;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import name.velikodniy.jcexpress.converter.token.TokenAssigner;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExportFileWriterV2Test {

    private ExportFile roundTripped;

    @BeforeAll
    void writeAndReadBack() throws Exception {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/TestApplet.class"));
        PackageInfo pkg = new PackageInfo("com.example", List.of(ci));
        TokenMap tokenMap = TokenAssigner.assign(pkg);

        byte[] aid = {(byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01};
        byte[] expBytes = ExportFileWriter.write(
                tokenMap, pkg.classes(), aid, 1, 0, JavaCardVersion.V3_0_5);

        roundTripped = ExportFileReader.read(expBytes);
    }

    @Test
    void headerHasExportFormatVersion() {
        assertThat(roundTripped.majorVersion()).isEqualTo(2);
        assertThat(roundTripped.minorVersion()).isEqualTo(1);
    }

    @Test
    void packageNamePreserved() {
        assertThat(roundTripped.packageName()).isEqualTo("com/example");
    }

    @Test
    void aidPreserved() {
        assertThat(roundTripped.aid())
                .containsExactly((byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01);
    }

    @Test
    void classesExported() {
        assertThat(roundTripped.classes()).isNotEmpty();
        assertThat(roundTripped.findClass("TestApplet")).isNotNull();
    }

    @Test
    void methodsExported() {
        ExportFile.ClassExport cls = roundTripped.findClass("TestApplet");
        assertThat(cls.methods()).isNotEmpty();
        assertThat(cls.methods())
                .extracting(ExportFile.MethodExport::name)
                .contains("install", "<init>");
    }

    @Test
    void methodTokensAreCorrect() {
        ExportFile.ClassExport cls = roundTripped.findClass("TestApplet");
        ExportFile.MethodExport init = cls.methods().stream()
                .filter(m -> "<init>".equals(m.name())).findFirst().orElseThrow();
        ExportFile.MethodExport install = cls.methods().stream()
                .filter(m -> "install".equals(m.name())).findFirst().orElseThrow();
        assertThat(init.token()).isEqualTo(0);
        assertThat(install.token()).isEqualTo(1);
    }
}
