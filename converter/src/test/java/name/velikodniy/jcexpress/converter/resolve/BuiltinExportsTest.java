package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the built-in Java Card API linking data ({@link BuiltinExports}): completeness
 * (constructors, inherited methods, packages), class flags and per-version gating.
 */
class BuiltinExportsTest {

    private static Map<String, ImportedPackage> imports(JavaCardVersion v) {
        return BuiltinExports.allBuiltinImports(0, v).stream()
                .collect(Collectors.toMap(i -> i.exportFile().packageName(), Function.identity()));
    }

    private static ExportFile.ClassExport cls(JavaCardVersion v, String pkg, String name) {
        return imports(v).get(pkg).exportFile().findClass(name);
    }

    private static Optional<ExportFile.MethodExport> method(ExportFile.ClassExport c, String name, String desc) {
        return c.methods().stream().filter(m -> m.name().equals(name) && m.descriptor().equals(desc)).findFirst();
    }

    // ── JCVM 3.1 §4.5.2: the Import component must not require a newer minor version than the card has ──

    @ParameterizedTest(name = "{0}: framework {1}, lang {2}, security {3}, crypto {4}")
    @CsvSource({
            "V2_1_2, 1.0, 1.0, 1.1, 1.1",
            "V2_2_1, 1.2, 1.0, 1.2, 1.2",
            "V2_2_2, 1.3, 1.0, 1.3, 1.3",
            "V3_0_3, 1.4, 1.0, 1.4, 1.4",
            "V3_0_4, 1.5, 1.0, 1.5, 1.5",
            "V3_0_5, 1.6, 1.0, 1.6, 1.6",
            "V3_1_0, 1.8, 1.0, 1.7, 1.7",
            "V3_2_0, 1.9, 1.0, 1.8, 1.8"
    })
    void importVersionsMatchTheApiOfTheTargetVersion_jcvm31_4_5_2(JavaCardVersion v, String framework,
                                                                   String lang, String security, String crypto) {
        Map<String, ImportedPackage> imports = imports(v);
        assertThat(version(imports.get("javacard/framework"))).isEqualTo(framework);
        assertThat(version(imports.get("java/lang"))).isEqualTo(lang);
        assertThat(version(imports.get("javacard/security"))).isEqualTo(security);
        assertThat(version(imports.get("javacardx/crypto"))).isEqualTo(crypto);
    }

    private static String version(ImportedPackage p) {
        return p.majorVersion() + "." + p.minorVersion();
    }

    @Test
    void coreImportsComeFirstInTheConventionalOrder() {
        List<ImportedPackage> list = BuiltinExports.allBuiltinImports(5, JavaCardVersion.V3_0_5);
        assertThat(list).extracting(i -> i.exportFile().packageName()).startsWith(
                "javacard/framework", "java/lang", "javacard/security", "javacardx/crypto");
        assertThat(list).extracting(ImportedPackage::token)
                .containsExactlyElementsOf(IntStream.range(5, 5 + list.size()).boxed().toList());
    }

    // ── JCVM 3.1 §5.7: constructors and inherited public instance methods are part of a class entry ──

    @Test
    void exceptionConstructorsAreAvailable_jcvm31_5_7() {
        var iso = cls(JavaCardVersion.V3_0_5, "javacard/framework", "ISOException");
        assertThat(method(iso, "<init>", "(S)V")).hasValueSatisfying(m -> assertThat(m.token()).isZero());
        var npe = cls(JavaCardVersion.V3_0_5, "java/lang", "NullPointerException");
        assertThat(method(npe, "<init>", "()V")).isPresent();
        var crypto = cls(JavaCardVersion.V3_0_5, "javacard/security", "CryptoException");
        assertThat(method(crypto, "<init>", "(S)V")).isPresent();
    }

    @Test
    void inheritedPublicMethodsAreListedWithTheSuperclassToken_jcvm31_5_7() {
        for (String ex : List.of("APDUException", "PINException", "SystemException", "TransactionException")) {
            var c = cls(JavaCardVersion.V3_0_5, "javacard/framework", ex);
            assertThat(method(c, "getReason", "()S")).as(ex).hasValueSatisfying(m -> assertThat(m.token()).isEqualTo(1));
            assertThat(method(c, "equals", "(Ljava/lang/Object;)Z")).as(ex)
                    .hasValueSatisfying(m -> assertThat(m.token()).isZero());
        }
        var user = cls(JavaCardVersion.V3_0_5, "javacard/framework", "UserException");
        assertThat(method(user, "getReason", "()S")).isPresent();
    }

    @Test
    void supersAndInterfacesAreKept_jcvm31_5_7() {
        var iso = cls(JavaCardVersion.V3_0_5, "javacard/framework", "ISOException");
        assertThat(iso.supers()).contains("javacard/framework/CardRuntimeException", "java/lang/RuntimeException",
                "java/lang/Exception", "java/lang/Throwable", "java/lang/Object");
        var ecPriv = cls(JavaCardVersion.V3_0_5, "javacard/security", "ECPrivateKey");
        assertThat(ecPriv.interfaces()).contains("javacard/security/Key", "javacard/security/ECKey",
                "javacard/security/PrivateKey");
    }

    // ── JCVM 3.1 §5.7 Table 5-3: class flags ──

    @Test
    void classFlagsIncludeAbstractFinalAndShareable_jcvm31_5_7() {
        assertThat(cls(JavaCardVersion.V3_0_5, "javacard/framework", "Shareable").accessFlags()).isEqualTo(0x0E01);
        assertThat(cls(JavaCardVersion.V3_0_5, "javacard/framework", "JCSystem").accessFlags()).isEqualTo(0x0011);
        assertThat(cls(JavaCardVersion.V3_0_5, "javacard/security", "Key").accessFlags()).isEqualTo(0x0601);
        assertThat(cls(JavaCardVersion.V3_0_5, "javacard/framework", "Applet").accessFlags()).isEqualTo(0x0401);
        assertThat(cls(JavaCardVersion.V3_0_5, "javacard/framework", "Shareable").isShareable()).isTrue();
    }

    // ── Packages beyond the four core ones ──

    @Test
    void optionalPackagesAreAvailableFromTheirFirstVersion() {
        assertThat(imports(JavaCardVersion.V2_2_1)).doesNotContainKey("javacardx/apdu");
        ImportedPackage apdu = imports(JavaCardVersion.V2_2_2).get("javacardx/apdu");
        assertThat(apdu).isNotNull();
        assertThat(HexFormat.of().withUpperCase().formatHex(apdu.aid())).isEqualTo("A0000000620209");
        assertThat(version(apdu)).isEqualTo("1.0");
        assertThat(apdu.exportFile().findClass("ExtendedLength").token()).isZero();
        assertThat(imports(JavaCardVersion.V3_0_5)).containsKeys("javacardx/framework/util",
                "javacardx/framework/tlv", "javacardx/framework/math", "javacardx/apdu/util", "javacardx/security");
        assertThat(imports(JavaCardVersion.V3_2_0)).containsKeys("javacardx/security/cert",
                "javacardx/security/derivation", "javacardx/framework/nio");
    }

    // ── Version gating (JCVM 3.1 §4.3.5: tokens resolve only against the target platform's API) ──

    @Test
    void membersIntroducedLaterAreNotOfferedForOlderTargets() {
        var util222 = cls(JavaCardVersion.V2_2_2, "javacard/framework", "Util");
        var util305 = cls(JavaCardVersion.V3_0_5, "javacard/framework", "Util");
        assertThat(method(util222, "arrayFill", "([BSSB)S")).isEmpty();
        assertThat(method(util305, "arrayFill", "([BSSB)S")).isPresent();

        var sig222 = cls(JavaCardVersion.V2_2_2, "javacard/security", "Signature");
        assertThat(method(sig222, "getInstance", "(BBBZ)Ljavacard/security/Signature;")).isEmpty();

        assertThat(imports(JavaCardVersion.V3_0_5).get("javacard/framework").exportFile().classes())
                .noneMatch(c -> c.simpleName().equals("Resources"));
        assertThat(method(cls(JavaCardVersion.V3_0_5, "javacard/framework", "JCSystem"),
                "isArrayView", "(Ljava/lang/Object;)Z")).isEmpty();
        assertThat(method(cls(JavaCardVersion.V3_1_0, "javacard/framework", "JCSystem"),
                "isArrayView", "(Ljava/lang/Object;)Z")).isPresent();
    }

    @Test
    void reportsTheVersionThatIntroducedAMember() {
        assertThat(BuiltinExports.introducedIn("javacard/framework/Util", "arrayFill", "([BSSB)S"))
                .contains(JavaCardVersion.V3_0_5);
        assertThat(BuiltinExports.introducedIn("javacardx/apdu/ExtendedLength", null, null))
                .contains(JavaCardVersion.V2_2_2);
        assertThat(BuiltinExports.introducedIn("javacard/framework/Util", "noSuchMethod", "()V")).isEmpty();
        assertThat(BuiltinExports.introducedIn("com/acme/Foo", null, null)).isEmpty();
    }

    // ── Internal consistency of the data for every version (JCVM 3.1 §4.3.7) ──

    @ParameterizedTest
    @EnumSource(JavaCardVersion.class)
    void classAndStaticMethodTokensAreConsecutiveFromZero_jcvm31_4_3_7(JavaCardVersion v) {
        for (ImportedPackage imp : BuiltinExports.allBuiltinImports(0, v)) {
            List<Integer> classTokens = imp.exportFile().classes().stream()
                    .map(ExportFile.ClassExport::token).sorted().toList();
            assertThat(classTokens).as(imp.exportFile().packageName())
                    .containsExactlyElementsOf(IntStream.range(0, classTokens.size()).boxed().toList());
            for (var c : imp.exportFile().classes()) {
                List<Integer> statics = c.methods().stream().filter(ExportFile.MethodExport::isStaticOrConstructor)
                        .map(ExportFile.MethodExport::token).sorted().toList();
                assertThat(statics).as(c.name() + " static tokens in " + v)
                        .containsExactlyElementsOf(IntStream.range(0, statics.size()).boxed().toList());
            }
        }
    }

    @Test
    void legacyGetExportReturnsTheJc305Api() {
        ExportFile fw = BuiltinExports.getExport("javacard/framework");
        assertThat(fw).isNotNull();
        assertThat(fw.majorVersion()).isEqualTo(1);
        assertThat(fw.minorVersion()).isEqualTo(6);
        assertThat(BuiltinExports.getExport("com/acme/none")).isNull();
    }
}
