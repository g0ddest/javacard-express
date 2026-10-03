package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.ConverterResult;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.input.PackageScanner;
import name.velikodniy.jcexpress.converter.resolve.BuiltinExports;
import name.velikodniy.jcexpress.converter.resolve.ExportedTypes;
import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFile.ClassExport;
import name.velikodniy.jcexpress.converter.token.ExportFile.FieldExport;
import name.velikodniy.jcexpress.converter.token.ExportFile.MethodExport;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import name.velikodniy.jcexpress.converter.token.TokenAssigner;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Content of the export files the converter generates (JCVM 3.1 Chapter 5), checked on the
 * library fixture {@code com.example.exp.lib} and on probe packages.
 */
class ExportFileContentTest {

    private static final Path CLASSES = Path.of("target/test-classes");
    private static final String LIB = "com/example/exp/lib/";

    @TempDir
    Path probe;

    private static ExportFile library(JavaCardVersion version) throws Exception {
        ConverterResult result = Converter.builder()
                .classesDirectory(CLASSES)
                .packageName("com.example.exp.lib")
                .packageAid("A0000000FF01")
                .packageVersion(1, 2)
                .javaCardVersion(version)
                .generateExport(true)
                .build().convert();
        return ExportFileReader.read(result.exportFile());
    }

    private static ClassExport cls(ExportFile ef, String simpleName) {
        return ef.findClass(simpleName);
    }

    @Test
    void libraryPackageHasAccLibraryItsVersionAndAid_jcvm31_5_6_1() throws Exception {
        ExportFile ef = library(JavaCardVersion.V3_0_5);

        assertThat(ef.packageName()).isEqualTo("com/example/exp/lib");
        assertThat(ef.packageFlags()).isEqualTo(ExportFile.ACC_LIBRARY);
        assertThat(ef.majorVersion()).isEqualTo(1);
        assertThat(ef.minorVersion()).isEqualTo(2);
        assertThat(HexFormat.of().withUpperCase().formatHex(ef.aid())).isEqualTo("A0000000FF01");
        assertThat(ef.formatMajor()).isEqualTo(2);
        assertThat(ef.formatMinor()).isEqualTo(1);
    }

    @Test
    void libraryExportsEveryPublicTypeButNoPackageVisibleOne_jcvm31_5_5() throws Exception {
        assertThat(library(JavaCardVersion.V3_0_5).classes()).extracting(ClassExport::name)
                .containsExactlyInAnyOrder(LIB + "LibService", LIB + "LibError", LIB + "LibUtil");
    }

    @Test
    void shareableInterfaceIsFlaggedAndListsOnlyItsOwnMethods_jcvm31_5_7() throws Exception {
        ClassExport service = cls(library(JavaCardVersion.V3_0_5), "LibService");

        // ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT | ACC_SHAREABLE
        assertThat(service.accessFlags()).isEqualTo(0x0E01);
        assertThat(service.supers()).containsExactly("java/lang/Object");
        assertThat(service.interfaces()).containsExactly("javacard/framework/Shareable");
        assertThat(service.fields()).isEmpty();
        // interfaces do not inherit java.lang.Object's methods (§4.3.7.7, §5.7)
        assertThat(service.methods()).extracting(MethodExport::name, MethodExport::descriptor,
                MethodExport::accessFlags).containsExactly(tuple("ping", "(S)S", 0x0401));
    }

    @Test
    void classListsEveryPublicSuperclassAndInheritedVirtualMethod_jcvm31_5_7_5_9() throws Exception {
        ClassExport error = cls(library(JavaCardVersion.V3_0_5), "LibError");

        assertThat(error.accessFlags()).isEqualTo(0x0001);
        assertThat(error.supers()).containsExactly("java/lang/Object", "java/lang/Throwable",
                "java/lang/Exception", "java/lang/RuntimeException", "javacard/framework/CardRuntimeException");
        assertThat(error.interfaces()).isEmpty();
        // constructor: static method token, no ACC_STATIC; inherited virtual methods keep the API tokens
        assertThat(error.methods()).extracting(MethodExport::name, MethodExport::descriptor,
                MethodExport::token, MethodExport::accessFlags).containsExactly(
                tuple("<init>", "(S)V", 0, 0x0001),
                tuple("equals", "(Ljava/lang/Object;)Z", 0, 0x0001),
                tuple("getReason", "()S", 1, 0x0001),
                tuple("setReason", "(S)V", 2, 0x0001));
    }

    @Test
    void fieldsAreThePublicAndProtectedOnesIncludingInstanceFieldsAndConstants_jcvm31_5_8() throws Exception {
        ClassExport util = cls(library(JavaCardVersion.V3_0_5), "LibUtil");

        assertThat(util.fields()).extracting(FieldExport::name, FieldExport::descriptor,
                FieldExport::accessFlags, FieldExport::constantValue).containsExactlyInAnyOrder(
                tuple("counter", "S", 0x0001, null),
                tuple("flag", "B", 0x0004, null),
                tuple("MAGIC", "S", 0x0019, 0x1234),
                tuple("table", "[B", 0x0009, null));
        assertThat(util.fields()).filteredOn(f -> f.name().equals("MAGIC"))
                .extracting(FieldExport::token).containsExactly(ExportFile.CONSTANT_FIELD_TOKEN);
        assertThat(util.fields()).filteredOn(f -> !f.name().equals("MAGIC"))
                .extracting(FieldExport::token).allMatch(t -> t < ExportFile.CONSTANT_FIELD_TOKEN);
    }

    @Test
    void methodsArePublicOrProtectedAndConstructorsAreNotStatic_jcvm31_5_9() throws Exception {
        ClassExport util = cls(library(JavaCardVersion.V3_0_5), "LibUtil");

        assertThat(util.methods()).extracting(MethodExport::name, MethodExport::descriptor,
                MethodExport::accessFlags).containsExactlyInAnyOrder(
                tuple("<init>", "()V", 0x0001),
                tuple("twice", "(S)S", 0x0009),
                tuple("equals", "(Ljava/lang/Object;)Z", 0x0001),
                tuple("next", "()S", 0x0001));
        assertThat(util.methods()).filteredOn(m -> m.name().equals("equals"))
                .extracting(MethodExport::token).containsExactly(0);
    }

    @Test
    void format2_3ListsReferencedPackagesAndCap22Counts_jcvm31_5_5_5_7() throws Exception {
        ExportFile ef = library(JavaCardVersion.V3_1_0);

        assertThat(ef.formatMinor()).isEqualTo(3);
        assertThat(ef.referencedPackages()).extracting(ExportFile.PackageReference::name,
                ExportFile.PackageReference::majorVersion, ExportFile.PackageReference::minorVersion)
                .containsExactly(tuple("java/lang", 1, 0), tuple("javacard/framework", 1, 8));
        assertThat(cls(ef, "LibService").cap22InheritableCount()).isZero();
        assertThat(cls(ef, "LibError").cap22InheritableCount()).isEqualTo(3);
        ClassExport util = cls(ef, "LibUtil");
        int virtualTokens = (int) util.methods().stream().filter(m -> !m.isStaticOrConstructor()).count();
        assertThat(util.cap22InheritableCount()).isEqualTo(virtualTokens);
    }

    @Test
    void appletPackageExportsOnlyItsPublicShareableInterfaces_jcvm31_5_5() throws Exception {
        JavaSources.compile(probe, Map.of(
                "com.acme.sio.SioService", """
                        package com.acme.sio;
                        public interface SioService extends javacard.framework.Shareable { short ping(short x); }
                        """,
                "com.acme.sio.Helper", """
                        package com.acme.sio;
                        public class Helper { public static short one() { return 1; } }
                        """,
                "com.acme.sio.SioServer", """
                        package com.acme.sio;
                        import javacard.framework.*;
                        public class SioServer extends Applet implements SioService {
                            public static void install(byte[] b, short o, byte l) { new SioServer().register(); }
                            public void process(APDU apdu) { }
                            public short ping(short x) { return Helper.one(); }
                            public Shareable getShareableInterfaceObject(AID client, byte p) { return this; }
                        }
                        """));

        ConverterResult result = Converter.builder()
                .classesDirectory(probe).packageName("com.acme.sio").packageAid("A000000FFE20")
                .applet("com.acme.sio.SioServer", "A000000FFE2001")
                .build().convert();
        ExportFile ef = ExportFileReader.read(result.exportFile());

        assertThat(ef.isLibrary()).isFalse();
        assertThat(ef.classes()).extracting(ClassExport::name, ClassExport::accessFlags)
                .containsExactly(tuple("com/acme/sio/SioService", 0x0E01));
    }

    @Test
    void exportedDescriptorNamingAPackageVisibleClassIsRejected_jcvm31_5_9() {
        compileBadExportProbe();

        assertThatThrownBy(() -> Converter.builder()
                .classesDirectory(probe).packageName("com.acme.badexp").packageAid("A000000FFE21")
                .build().convert())
                .isInstanceOf(ConverterException.class)
                // the subset check rejects such an API first (JCVM 3.1 §2.2.1.1.6, bullet 4)
                .hasMessageContaining("the signature of the public method use of public class "
                        + "com/acme/badexp/Api uses the package-visible type com/acme/badexp/Secret");
    }

    @Test
    void exportFileWriterRejectsADescriptorNamingAPackageVisibleClass_jcvm31_5_9() throws Exception {
        // second line of defence behind the subset check: the export file writer itself
        compileBadExportProbe();
        PackageInfo info = PackageScanner.scan(probe, "com.acme.badexp");
        List<ImportedPackage> imports = BuiltinExports.allBuiltinImports(0, JavaCardVersion.V3_0_5);
        TokenMap tokens = TokenAssigner.assign(info, new ExportedTypes(imports));
        ExportInput input = new ExportInput("com/acme/badexp", HexFormat.of().parseHex("A000000FFE21"),
                1, 0, true, info.classes(), tokens, imports, JavaCardVersion.V3_0_5);

        assertThatThrownBy(() -> ExportFileWriter.write(input))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("com.acme.badexp.Api.use is exported but its descriptor "
                        + "(Lcom/acme/badexp/Secret;)V names the package-visible class com.acme.badexp.Secret");
    }

    private void compileBadExportProbe() {
        JavaSources.compile(probe, Map.of("com.acme.badexp.Api", """
                package com.acme.badexp;
                public class Api { public static void use(Secret s) { } }
                class Secret { }
                """));
    }
}
