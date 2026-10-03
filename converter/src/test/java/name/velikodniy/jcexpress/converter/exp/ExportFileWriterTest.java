package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassFileReader;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.resolve.BuiltinExports;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFile.ClassExport;
import name.velikodniy.jcexpress.converter.token.ExportFile.FieldExport;
import name.velikodniy.jcexpress.converter.token.ExportFile.MethodExport;
import name.velikodniy.jcexpress.converter.token.ExportFile.PackageReference;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import name.velikodniy.jcexpress.converter.token.TokenAssigner;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Serialization of export files (JCVM 3.1 §5.5-§5.10): known-answer layouts derived by hand
 * from the specification, and round trips through the reader.
 */
class ExportFileWriterTest {

    private static final byte[] P_AID = HexFormat.of().parseHex("A000000001");
    private static final byte[] Q_AID = HexFormat.of().parseHex("A000000002");

    private static String hex(byte[] b) {
        return HexFormat.of().withUpperCase().formatHex(b);
    }

    @Test
    void emptyLibraryHasTheLayoutOfJcvm31_5_5() {
        ExportFile model = new ExportFile("p", P_AID, 1, 0, List.of(), ExportFile.ACC_LIBRARY, 2, 1);

        assertThat(hex(ExportFileWriter.write(model))).isEqualTo(
                "00FACADE" + "01" + "02"                          // magic, format minor 1, major 2
                        + "0002"                                  // constant_pool_count
                        + "01" + "0001" + "70"                    // [0] CONSTANT_Utf8 "p"
                        + "0D" + "01" + "0000" + "00" + "01" + "05" + "A000000001" // [1] CONSTANT_Package
                        + "0001"                                  // this_package
                        + "00");                                  // export_class_count
    }

    @Test
    void format2_3HasReferencedPackagesAndCap22Counts_jcvm31_5_5_5_7() {
        ClassExport c = new ClassExport("p/C", 0, 0x0001,
                List.of(new MethodExport("m", "()V", 0, 0x0001)), List.of(), List.of("q/B"), List.of(), 1);
        ExportFile model = new ExportFile("p", P_AID, 1, 0, List.of(c), ExportFile.ACC_LIBRARY, 2, 3,
                List.of(new PackageReference("q", Q_AID, 2, 1)));

        assertThat(hex(ExportFileWriter.write(model))).isEqualTo(
                "00FACADE" + "03" + "02" + "000A"
                        + "01" + "0001" + "70"                                     // [0] "p"
                        + "0D" + "01" + "0000" + "00" + "01" + "05" + "A000000001" // [1] package p 1.0
                        + "01" + "0001" + "71"                                     // [2] "q"
                        + "0D" + "00" + "0002" + "01" + "02" + "05" + "A000000002" // [3] package q 2.1
                        + "01" + "0003" + "702F43"                                 // [4] "p/C"
                        + "07" + "0004"                                            // [5] Classref p/C
                        + "01" + "0003" + "712F42"                                 // [6] "q/B"
                        + "07" + "0006"                                            // [7] Classref q/B
                        + "01" + "0001" + "6D"                                     // [8] "m"
                        + "01" + "0003" + "282956"                                 // [9] "()V"
                        + "0001"                                                   // this_package
                        + "01" + "0003"                                            // referenced_packages
                        + "01"                                                     // export_class_count
                        + "00" + "0001" + "0005"                                   // token, flags, name
                        + "0001" + "0007"                                          // supers
                        + "00"                                                     // interfaces
                        + "0000"                                                   // fields
                        + "0001" + "00" + "0001" + "0008" + "0009"                 // methods
                        + "01");                                                   // CAP22 count
    }

    private static ExportFile richModel(int formatMinor) {
        ClassExport iface = new ClassExport("p/I", 0, 0x0E01,
                List.of(new MethodExport("ping", "(S)S", 0, 0x0401)), List.of(),
                List.of("java/lang/Object"), List.of("javacard/framework/Shareable"));
        ClassExport cls = new ClassExport("p/C", 1, 0x0011,
                List.of(new MethodExport("<init>", "()V", 0, 0x0001),
                        new MethodExport("make", "(S)Lp/C;", 1, 0x0009),
                        new MethodExport("equals", "(Ljava/lang/Object;)Z", 0, 0x0001),
                        new MethodExport("ping", "(S)S", 1, 0x0001)),
                List.of(new FieldExport("count", "S", 0, 0x0001),
                        new FieldExport("LIMIT", "S", 0xFF, 0x0019, -2),
                        new FieldExport("table", "[B", 0, 0x0009)),
                List.of("java/lang/Object"), List.of("javacard/framework/Shareable", "p/I"));
        List<PackageReference> refs = formatMinor >= 3
                ? List.of(new PackageReference("java/lang", HexFormat.of().parseHex("A0000000620001"), 1, 0),
                new PackageReference("javacard/framework", HexFormat.of().parseHex("A0000000620101"), 1, 8))
                : List.of();
        return new ExportFile("p", P_AID, 1, 2, List.of(iface, cls), ExportFile.ACC_LIBRARY, 2, formatMinor, refs);
    }

    @Test
    void format2_1ModelReadsBackUnchanged() throws Exception {
        ExportFile model = richModel(1);

        assertThat(ExportFileReader.read(ExportFileWriter.write(model))).isEqualTo(model);
    }

    @Test
    void format2_3ModelReadsBackUnchanged() throws Exception {
        ExportFile model = richModel(3);

        ExportFile read = ExportFileReader.read(ExportFileWriter.write(model));

        assertThat(read).isEqualTo(model);
        assertThat(read.findClass("C").cap22InheritableCount()).isEqualTo(2);
    }

    @Test
    void constantPoolEntriesAreShared_jcvm31_5_6() {
        byte[] exp = ExportFileWriter.write(richModel(1));

        int count = ((exp[6] & 0xFF) << 8) | (exp[7] & 0xFF);
        // "p" + package; p/I, p/C, Object, Shareable as Utf8 + Classref; method names ping, <init>,
        // make, equals and descriptors (S)S, ()V, (S)Lp/C;, (Ljava/lang/Object;)Z, each once;
        // field names count, LIMIT, table and descriptors S, [B; "ConstantValue" and one Integer
        assertThat(count).isEqualTo(2 + 8 + 4 + 4 + 3 + 2 + 2);
    }

    @Test
    void unnamedPackageHasNoExportFile_jcvm31_5_6_1() throws Exception {
        ClassInfo ci = ClassFileReader.readFile(Path.of("target/test-classes/com/example/TestApplet.class"));
        PackageInfo pkg = new PackageInfo("com.example", List.of(ci));
        TokenMap tokenMap = TokenAssigner.assign(pkg);

        // CONSTANT_Package_info.name_index must denote "a valid Java package name" (§5.6.1)
        assertThatThrownBy(() -> new ExportInput("", HexFormat.of().parseHex("A000000062FE01"), 1, 0, true,
                pkg.classes(), tokenMap, List.of(), JavaCardVersion.V3_0_5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unnamed package");
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedTokenMapApiDescribesThePackageAsALibrary() throws Exception {
        ClassInfo ci = ClassFileReader.readFile(Path.of("target/test-classes/com/example/TestApplet.class"));
        PackageInfo pkg = new PackageInfo("com.example", List.of(ci));
        // inherited virtual methods keep the tokens of the API superclass (JCVM 3.1 §4.3.7.6)
        TokenMap tokenMap = TokenAssigner.assign(pkg, name -> BuiltinExports.getExport("javacard/framework")
                .findClass(name).methods().stream().filter(m -> !m.isStaticOrConstructor())
                .map(m -> new TokenMap.MethodEntry(m.name(), m.descriptor(), m.token())).toList());

        ExportFile ef = ExportFileReader.read(ExportFileWriter.write(tokenMap, pkg.classes(),
                HexFormat.of().parseHex("A000000062FE01"), 1, 0, JavaCardVersion.V3_0_5));

        assertThat(ef.packageName()).isEqualTo("com/example");
        assertThat(ef.isLibrary()).isTrue();
        assertThat(ef.majorVersion()).isEqualTo(1);
        assertThat(ef.minorVersion()).isZero();
        assertThat(ef.formatMinor()).isEqualTo(1);
        assertThat(ef.findClass("TestApplet").supers())
                .containsExactly("java/lang/Object", "javacard/framework/Applet");
        assertThat(ef.findClass("TestApplet").methods()).extracting(MethodExport::name)
                .contains("<init>", "install", "process");
    }
}
