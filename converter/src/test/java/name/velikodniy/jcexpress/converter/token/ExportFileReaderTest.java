package name.velikodniy.jcexpress.converter.token;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportFileReaderTest {

    @Test
    void shouldReadMinimalExportFile() throws IOException {
        byte[] data = buildMinimalExportFile();
        ExportFile ef = ExportFileReader.read(data);

        assertThat(ef.packageName()).isEqualTo("com/example");
        assertThat(ef.aid()).containsExactly(0xA0, 0x00, 0x00, 0x00, 0x62, 0x01);
        assertThat(ef.majorVersion()).isEqualTo(1);
        assertThat(ef.minorVersion()).isEqualTo(0);
        assertThat(ef.classes()).hasSize(1);

        ExportFile.ClassExport cls = ef.classes().getFirst();
        assertThat(cls.name()).isEqualTo("MyApplet");
        assertThat(cls.token()).isZero();
        assertThat(cls.methods()).hasSize(1);
        assertThat(cls.methods().getFirst().name()).isEqualTo("process");
        assertThat(cls.methods().getFirst().token()).isZero();
        assertThat(cls.fields()).isEmpty();
    }

    @Test
    void shouldRejectInvalidMagic() {
        byte[] bad = {0x00, 0x00, 0x00, 0x00};
        assertThatThrownBy(() -> ExportFileReader.read(bad))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("magic");
    }

    @Test
    void shouldReadExportFileWithFields() throws IOException {
        byte[] data = buildExportFileWithField();
        ExportFile ef = ExportFileReader.read(data);

        assertThat(ef.classes()).hasSize(1);
        ExportFile.ClassExport cls = ef.classes().getFirst();
        assertThat(cls.fields()).hasSize(1);
        assertThat(cls.fields().getFirst().name()).isEqualTo("MAX_SIZE");
        assertThat(cls.fields().getFirst().token()).isEqualTo(0);
    }

    /**
     * Builds a minimal valid .exp binary with one class and one method.
     * Constant pool layout:
     *   [1] UTF8 "com/example"
     *   [2] PACKAGE (nameIdx=1, aid=A00000006201)
     *   [3] UTF8 "MyApplet"
     *   [4] CLASSREF (nameIdx=3)
     *   [5] UTF8 "process"
     *   [6] UTF8 "(Ljavacard/framework/APDU;)V"
     */
    private byte[] buildMinimalExportFile() throws IOException {
        var baos = new ByteArrayOutputStream();
        var out = new DataOutputStream(baos);

        // Magic
        out.writeInt(ExportFileReader.EXP_MAGIC);
        // Minor, major version
        out.writeByte(0); // minor
        out.writeByte(1); // major

        // Constant pool count (7 entries, index 0 unused)
        out.writeShort(7);

        // CP[1]: UTF8 "com/example"
        writeUtf8(out, "com/example");
        // CP[2]: PACKAGE (flags=0, nameIdx=1, minor=0, major=1, aid)
        out.writeByte(13); // tag
        out.writeByte(0);  // flags
        out.writeShort(1); // name index
        out.writeByte(0);  // minor
        out.writeByte(1);  // major
        byte[] aid = {(byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01};
        out.writeByte(aid.length);
        out.write(aid);
        // CP[3]: UTF8 "MyApplet"
        writeUtf8(out, "MyApplet");
        // CP[4]: CLASSREF (nameIdx=3)
        out.writeByte(7);  // tag
        out.writeShort(3); // name index
        // CP[5]: UTF8 "process"
        writeUtf8(out, "process");
        // CP[6]: UTF8 "(Ljavacard/framework/APDU;)V"
        writeUtf8(out, "(Ljavacard/framework/APDU;)V");

        // this_package = CP[2]
        out.writeShort(2);

        // export_class_count = 1
        out.writeByte(1);

        // class_export_info
        out.writeShort(0);    // token
        out.writeShort(0x21); // access_flags (public + super)
        out.writeShort(4);    // name_index → CP[4] (CLASSREF)
        out.writeShort(0);    // supers count
        out.writeByte(0);     // interfaces count
        out.writeShort(0);    // field count
        out.writeShort(1);    // method count
        // method: token=0, flags=public, name=process, desc=(LAPDU;)V
        out.writeShort(0);    // token
        out.writeShort(0x01); // access_flags (public)
        out.writeShort(5);    // name → CP[5]
        out.writeShort(6);    // descriptor → CP[6]

        out.flush();
        return baos.toByteArray();
    }

    /**
     * Builds an .exp file with one class that has one static field.
     */
    private byte[] buildExportFileWithField() throws IOException {
        var baos = new ByteArrayOutputStream();
        var out = new DataOutputStream(baos);

        out.writeInt(ExportFileReader.EXP_MAGIC);
        out.writeByte(0); out.writeByte(1);

        // CP: 8 entries
        out.writeShort(8);
        writeUtf8(out, "com/example");        // [1]
        out.writeByte(13); out.writeByte(0);   // [2] PACKAGE
        out.writeShort(1); out.writeByte(0); out.writeByte(1);
        out.writeByte(6); out.write(new byte[]{(byte)0xA0, 0, 0, 0, 0x62, 1});
        writeUtf8(out, "Constants");           // [3]
        out.writeByte(7); out.writeShort(3);   // [4] CLASSREF
        writeUtf8(out, "MAX_SIZE");            // [5]
        writeUtf8(out, "S");                   // [6]
        writeUtf8(out, "process");             // [7] (not used, but valid)

        out.writeShort(2); // this_package
        out.writeByte(1);  // 1 class

        // class
        out.writeShort(0);    // token
        out.writeShort(0x21); // flags
        out.writeShort(4);    // name → CLASSREF[4]
        out.writeShort(0);    // supers
        out.writeByte(0);     // interfaces
        out.writeShort(1);    // 1 field
        // field: MAX_SIZE S
        out.writeShort(0);    // token
        out.writeShort(0x19); // public static final
        out.writeShort(5);    // name
        out.writeShort(6);    // descriptor
        out.writeShort(0);    // 0 methods

        out.flush();
        return baos.toByteArray();
    }

    private void writeUtf8(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeByte(1); // tag
        out.writeShort(bytes.length);
        out.write(bytes);
    }
}
