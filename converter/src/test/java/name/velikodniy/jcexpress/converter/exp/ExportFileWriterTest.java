package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.input.ClassFileReader;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import name.velikodniy.jcexpress.converter.token.TokenAssigner;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportFileWriterTest {

    @Test
    void shouldProduceReadableExportFile() throws Exception {
        // Load a test class
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/TestApplet.class"));
        PackageInfo pkg = new PackageInfo("com.example", List.of(ci));
        TokenMap tokenMap = TokenAssigner.assign(pkg);

        byte[] aid = {(byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01};

        // Write export file
        byte[] expBytes = ExportFileWriter.write(tokenMap, pkg.classes(), aid, 1, 0);
        assertThat(expBytes).isNotEmpty();

        // Read it back — round trip
        ExportFile ef = ExportFileReader.read(expBytes);
        assertThat(ef.packageName()).isEqualTo("com/example");
        assertThat(ef.aid()).containsExactly((byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01);
        assertThat(ef.majorVersion()).isEqualTo(1);
        assertThat(ef.minorVersion()).isEqualTo(0);
    }

    @Test
    void shouldContainExportedClasses() throws Exception {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/TestApplet.class"));
        PackageInfo pkg = new PackageInfo("com.example", List.of(ci));
        TokenMap tokenMap = TokenAssigner.assign(pkg);

        byte[] aid = {(byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01};
        byte[] expBytes = ExportFileWriter.write(tokenMap, pkg.classes(), aid, 1, 0);

        ExportFile ef = ExportFileReader.read(expBytes);
        assertThat(ef.classes()).isNotEmpty();
        assertThat(ef.classes().getFirst().name()).isEqualTo("TestApplet");
    }

    @Test
    void shouldContainExportedMethods() throws Exception {
        ClassInfo ci = ClassFileReader.readFile(
                Path.of("target/test-classes/com/example/TestApplet.class"));
        PackageInfo pkg = new PackageInfo("com.example", List.of(ci));
        TokenMap tokenMap = TokenAssigner.assign(pkg);

        byte[] aid = {(byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01};
        byte[] expBytes = ExportFileWriter.write(tokenMap, pkg.classes(), aid, 1, 0);

        ExportFile ef = ExportFileReader.read(expBytes);
        ExportFile.ClassExport testApplet = ef.findClass("TestApplet");

        // Should have methods (process and install at minimum)
        assertThat(testApplet.methods()).isNotEmpty();
    }
}
