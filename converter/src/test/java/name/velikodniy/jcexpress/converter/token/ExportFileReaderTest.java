package name.velikodniy.jcexpress.converter.token;

import name.velikodniy.jcexpress.converter.testutil.ExpFixture;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Cls;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Field;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Method;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture.Pkg;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests {@link ExportFileReader} against spec-conformant export files built by the independent
 * {@link ExpFixture} writer (JCVM 3.1 Chapter 5).
 */
class ExportFileReaderTest {

    private static final Pkg LIB = new Pkg("com/acme/lib", ExportFile.ACC_LIBRARY, 1, 2, "A0000000FF01");
    private static final Pkg FRAMEWORK = new Pkg("javacard/framework", 1, 1, 6, "A0000000620101");

    private static final Cls LIB_ERROR = new Cls(1, 0x0001, "com/acme/lib/LibError",
            List.of("javacard/framework/CardRuntimeException", "java/lang/RuntimeException",
                    "java/lang/Exception", "java/lang/Throwable", "java/lang/Object"),
            List.of(),
            List.of(),
            List.of(new Method(0, 0x0001, "<init>", "(S)V"),
                    new Method(0, 0x0001, "equals", "(Ljava/lang/Object;)Z"),
                    new Method(1, 0x0001, "getReason", "()S"),
                    new Method(2, 0x0001, "setReason", "(S)V")),
            3);

    private static final Cls LIB_UTIL = new Cls(2, 0x0001, "com/acme/lib/LibUtil",
            List.of("java/lang/Object"), List.of(),
            List.of(new Field(0xFF, 0x0019, "MAGIC", "S", 0x1234),
                    new Field(0, 0x0009, "table", "[B", null),
                    new Field(0, 0x0001, "counter", "S", null)),
            List.of(new Method(0, 0x0009, "twice", "(S)S")),
            1);

    private static final Cls LIB_SERVICE = new Cls(0, 0x0E01, "com/acme/lib/LibService",
            List.of("java/lang/Object"), List.of("javacard/framework/Shareable"),
            List.of(), List.of(new Method(0, 0x0401, "ping", "(S)S")), 0);

    private static byte[] lib21() {
        return ExpFixture.write(2, 1, LIB, List.of(), List.of(LIB_SERVICE, LIB_ERROR, LIB_UTIL));
    }

    private static byte[] lib23() {
        return ExpFixture.write(2, 3, LIB, List.of(FRAMEWORK), List.of(LIB_SERVICE, LIB_ERROR, LIB_UTIL));
    }

    // ── JCVM 3.1 §5.5 / §5.6.1: package version vs. export file format version ──

    @Test
    void packageVersionComesFromConstantPackageNotFromFileHeader_jcvm31_5_6_1() throws IOException {
        ExportFile ef = ExportFileReader.read(lib21());

        assertThat(ef.majorVersion()).as("package major (CONSTANT_Package)").isEqualTo(1);
        assertThat(ef.minorVersion()).as("package minor (CONSTANT_Package)").isEqualTo(2);
        assertThat(ef.formatMajor()).as("format major (header)").isEqualTo(2);
        assertThat(ef.formatMinor()).as("format minor (header)").isEqualTo(1);
        assertThat(ef.isLibrary()).isTrue();
        assertThat(ef.packageName()).isEqualTo("com/acme/lib");
        assertThat(ef.aid()).containsExactly(0xA0, 0x00, 0x00, 0x00, 0xFF, 0x01);
    }

    @Test
    void readsClassesMethodsAndFieldsOfFormat21_jcvm31_5_7() throws IOException {
        ExportFile ef = ExportFileReader.read(lib21());

        assertThat(ef.classes()).extracting(ExportFile.ClassExport::name)
                .containsExactly("com/acme/lib/LibService", "com/acme/lib/LibError", "com/acme/lib/LibUtil");
        ExportFile.ClassExport error = ef.findClass("LibError");
        assertThat(error.token()).isEqualTo(1);
        assertThat(error.supers()).containsExactly("javacard/framework/CardRuntimeException",
                "java/lang/RuntimeException", "java/lang/Exception", "java/lang/Throwable", "java/lang/Object");
        assertThat(error.methods()).containsExactly(
                new ExportFile.MethodExport("<init>", "(S)V", 0, 0x0001),
                new ExportFile.MethodExport("equals", "(Ljava/lang/Object;)Z", 0, 0x0001),
                new ExportFile.MethodExport("getReason", "()S", 1, 0x0001),
                new ExportFile.MethodExport("setReason", "(S)V", 2, 0x0001));
    }

    @Test
    void keepsInterfacesAndShareableFlag_jcvm31_5_7() throws IOException {
        ExportFile.ClassExport service = ExportFileReader.read(lib21()).findClass("com/acme/lib/LibService");

        assertThat(service.isInterface()).isTrue();
        assertThat(service.isShareable()).isTrue();
        assertThat(service.interfaces()).containsExactly("javacard/framework/Shareable");
    }

    @Test
    void readsConstantValueAttributeOfCompileTimeConstants_jcvm31_5_10_1() throws IOException {
        ExportFile.ClassExport util = ExportFileReader.read(lib21()).findClass("LibUtil");

        assertThat(util.fields()).containsExactly(
                new ExportFile.FieldExport("MAGIC", "S", 0xFF, 0x0019, 0x1234),
                new ExportFile.FieldExport("table", "[B", 0, 0x0009, null),
                new ExportFile.FieldExport("counter", "S", 0, 0x0001, null));
    }

    // ── JCVM 3.1 §5.5 / §5.7: format 2.3 items ──

    @Test
    void readsFormat23WithReferencedPackagesAndCap22Counts_jcvm31_5_5() throws IOException {
        ExportFile ef = ExportFileReader.read(lib23());

        assertThat(ef.formatMajor()).isEqualTo(2);
        assertThat(ef.formatMinor()).isEqualTo(3);
        assertThat(ef.majorVersion()).isEqualTo(1);
        assertThat(ef.minorVersion()).isEqualTo(2);
        assertThat(ef.classes()).hasSize(3);
        assertThat(ef.findClass("LibUtil").fields()).hasSize(3);
        assertThat(ef.findClass("LibError").methods()).hasSize(4);
        assertThat(ef.referencedPackages()).containsExactly(
                new ExportFile.PackageReference("javacard/framework",
                        java.util.HexFormat.of().parseHex("A0000000620101"), 1, 6));
        assertThat(ef.classes()).extracting(ExportFile.ClassExport::cap22InheritableCount)
                .containsExactly(0, 3, 1);
    }

    @Test
    void format21AndFormat23DescribeTheSameApi() throws IOException {
        ExportFile a = ExportFileReader.read(lib21());
        ExportFile b = ExportFileReader.read(lib23());

        // format 2.1 has no CAP22 count: the reader derives it from the public virtual methods
        assertThat(b.classes()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("cap22InheritableCount")
                .isEqualTo(a.classes());
        assertThat(a.classes()).extracting(ExportFile.ClassExport::cap22InheritableCount)
                .containsExactly(0, 3, 0);
    }

    // ── Robustness: unsupported formats and malformed input fail with a clear message ──

    @Test
    void rejectsInvalidMagic() {
        byte[] bad = {0x00, 0x00, 0x00, 0x00, 1, 2};
        assertThatThrownBy(() -> ExportFileReader.read(bad))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("magic");
    }

    @Test
    void rejectsExportFormatMajorOtherThan2_jcvm31_5_5() {
        byte[] data = lib21();
        data[5] = 1; // header major_version
        assertThatThrownBy(() -> ExportFileReader.read(data))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("format 1.1")
                .hasMessageContaining("not supported");
    }

    @Test
    void rejectsExportFormatNewerThan23_jcvm31_5_5() {
        byte[] data = lib23();
        data[4] = 4; // header minor_version
        assertThatThrownBy(() -> ExportFileReader.read(data))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("format 2.4");
    }

    @Test
    void rejectsTruncatedFileWithOffset() {
        byte[] data = lib21();
        byte[] truncated = Arrays.copyOf(data, data.length - 3);
        assertThatThrownBy(() -> ExportFileReader.read(truncated))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("truncated")
                .hasMessageContaining("offset");
    }

    @Test
    void rejectsTrailingBytes() {
        byte[] data = lib21();
        byte[] padded = Arrays.copyOf(data, data.length + 2);
        assertThatThrownBy(() -> ExportFileReader.read(padded))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("trailing");
    }

    @Test
    void rejectsFormat21FileLabelledAsFormat23() {
        byte[] data = lib21();
        data[4] = 3; // claims format 2.3 without the 2.3 items
        assertThatThrownBy(() -> ExportFileReader.read(data))
                .isInstanceOf(IOException.class);
    }

    @Test
    void rejectsPackageAidOutside5To16Bytes_jcvm31_5_6_1() {
        Pkg shortAid = new Pkg("com/acme/lib", 1, 1, 0, "A0000000");
        byte[] data = ExpFixture.write(2, 1, shortAid, List.of(), List.of());
        assertThatThrownBy(() -> ExportFileReader.read(data))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("AID");
    }
}
