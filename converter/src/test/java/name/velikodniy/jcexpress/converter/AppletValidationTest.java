package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.AppletRef;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JCVM 3.1 §6.6: every applet of the Applet component is a non-abstract subclass of
 * {@code javacard.framework.Applet} defined in the package, and its install_method_offset
 * designates its {@code static install(byte[], short, byte)} method. Configuration mistakes must
 * stop the conversion instead of producing an applet whose entry point is an arbitrary method.
 */
class AppletValidationTest {

    private static final Path CLASSES = Path.of("target/test-classes");

    @TempDir
    Path probeClasses;

    private static Converter.Builder testApplet(String appletClass) {
        return Converter.builder()
                .classesDirectory(CLASSES)
                .packageName("com.example")
                .packageAid("A00000006203")
                .applet(appletClass, "A0000000620301");
    }

    @Test
    void misspelledAppletClassIsRejected_jcvm31_6_6() {
        assertThatThrownBy(() -> testApplet("com.example.TestAplet").build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("applet class com.example.TestAplet is not a class of package com.example");
    }

    @Test
    void classThatIsNotAnAppletIsRejected_jcvm31_6_6() {
        assertThatThrownBy(() -> Converter.builder()
                .classesDirectory(CLASSES)
                .packageName("com.example.multiclass")
                .packageAid("A00000006204")
                .applet("com.example.multiclass.Helper", "A0000000620401")
                .build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("applet class com.example.multiclass.Helper does not extend "
                        + "javacard.framework.Applet");
    }

    @Test
    void abstractAppletClassIsRejected_jcvm31_6_6() {
        assertThatThrownBy(() -> Converter.builder()
                .classesDirectory(CLASSES)
                .packageName("com.example.abstract_")
                .packageAid("A00000006205")
                .applet("com.example.abstract_.AbstractBase", "A0000000620501")
                .build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("applet class com.example.abstract_.AbstractBase is abstract");
    }

    @Test
    void appletWithoutInstallMethodIsRejected_jcvm31_6_6() {
        JavaSources.compile(probeClasses, Map.of("com.acme.noinst.NoInstall", """
                package com.acme.noinst;
                import javacard.framework.*;
                public class NoInstall extends Applet {
                    public void process(APDU apdu) { }
                }
                """));

        assertThatThrownBy(() -> Converter.builder()
                .classesDirectory(probeClasses)
                .packageName("com.acme.noinst")
                .packageAid("A000000FFE02")
                .applet("com.acme.noinst.NoInstall", "A000000FFE0201")
                .build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("applet class com.acme.noinst.NoInstall has no public static void "
                        + "install(byte[], short, byte) method");
    }

    @Test
    void installMethodOffsetDesignatesTheInstallMethod_jcvm31_6_6() throws Exception {
        // Base is public: a package-visible superclass of a public class must not declare public
        // members (JCVM 3.1 §2.2.1.1.6), and install must be public
        JavaSources.compile(probeClasses, Map.of("com.acme.multi.A", """
                package com.acme.multi;
                import javacard.framework.*;
                public class A extends Base {
                    public void process(APDU apdu) { }
                }
                """, "com.acme.multi.Base", """
                package com.acme.multi;
                import javacard.framework.*;
                public abstract class Base extends Applet {
                    public static void install(byte[] b, short o, byte l) { new A().register(); }
                    void helper() { }
                }
                """));

        ConverterResult result = Converter.builder()
                .classesDirectory(probeClasses)
                .packageName("com.acme.multi")
                .packageAid("A000000FFE03")
                .applet("com.acme.multi.A", "A000000FFE0301")
                .build().convert();

        AppletRef applet = CapInspector.applets(result.capFile()).getFirst();
        // install(byte[], short, byte) is static: 3 argument words (JCVM 3.1 §6.10 nargs)
        assertThat(CapInspector.methodNargs(result.capFile(), applet.installMethodOffset())).isEqualTo(3);
    }

    @Test
    void appletsAreListedInRegistrationOrder() throws Exception {
        JavaSources.compile(probeClasses, Map.of("com.acme.three.A1", """
                package com.acme.three;
                import javacard.framework.*;
                public class A1 extends Applet {
                    public static void install(byte[] b, short o, byte l) { new A1().register(); }
                    public void process(APDU apdu) { }
                }
                class A2 extends A1 {
                    public static void install(byte[] b, short o, byte l) { new A2().register(); }
                }
                class A3 extends A1 {
                    public static void install(byte[] b, short o, byte l) { new A3().register(); }
                }
                """));
        Converter.Builder builder = Converter.builder()
                .classesDirectory(probeClasses)
                .packageName("com.acme.three")
                .packageAid("A000000FFE04")
                .applet("com.acme.three.A3", "A000000FFE0403")
                .applet("com.acme.three.A1", "A000000FFE0401")
                .applet("com.acme.three.A2", "A000000FFE0402");

        List<AppletRef> applets = CapInspector.applets(builder.build().convert().capFile());

        assertThat(applets).extracting(AppletRef::aidHex)
                .containsExactly("A000000FFE0403", "A000000FFE0401", "A000000FFE0402");
        assertThat(applets).extracting(AppletRef::installMethodOffset).doesNotHaveDuplicates();
    }

    // ── AIDs (JCVM 3.1 §4.2, §6.4, §6.6) ──

    @Test
    void appletRidMustEqualThePackageRid_jcvm31_6_6() {
        assertThatThrownBy(() -> Converter.builder()
                .classesDirectory(CLASSES).packageName("com.example").packageAid("A00000006203")
                .applet("com.example.TestApplet", "B000000001FF01").build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("has RID B000000001, but the package RID is A000000062");
    }

    @Test
    void aidLengthsMustBe5To16Bytes_jcvm31_4_2_1() {
        assertThatThrownBy(() -> Converter.builder()
                .classesDirectory(CLASSES).packageName("com.example").packageAid("A0000000")
                .applet("com.example.TestApplet", "A000000062030102030405060708090A0B").build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("package AID A0000000 is 4 bytes long")
                .hasMessageContaining("is 17 bytes long");
    }

    @Test
    void appletAidsMustBeUniqueAndDifferFromThePackageAid_jcvm31_4_2_2() {
        assertThatThrownBy(() -> Converter.builder()
                .classesDirectory(CLASSES).packageName("com.example.abstract_").packageAid("A00000006205")
                .applet("com.example.abstract_.ConcreteApplet", "A00000006205")
                .build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("has the package AID A00000006205");
    }

    @Test
    void duplicateAppletAidsAreRejected_jcvm31_4_2_2_2() throws Exception {
        JavaSources.compile(probeClasses, Map.of("com.acme.dup.A1", """
                package com.acme.dup;
                import javacard.framework.*;
                public class A1 extends Applet {
                    public static void install(byte[] b, short o, byte l) { new A1().register(); }
                    public void process(APDU apdu) { }
                }
                class A2 extends A1 {
                    public static void install(byte[] b, short o, byte l) { new A2().register(); }
                }
                """));

        assertThatThrownBy(() -> Converter.builder()
                .classesDirectory(probeClasses).packageName("com.acme.dup").packageAid("A000000FFE05")
                .applet("com.acme.dup.A1", "A000000FFE0501").applet("com.acme.dup.A2", "A000000FFE0501")
                .build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("applets com.acme.dup.A1 and com.acme.dup.A2 have the same AID A000000FFE0501");
    }

    @Test
    void packageMustNotReuseTheAidOfAnImportedPackage_jcvm31_4_2_2_3() {
        assertThatThrownBy(() -> Converter.builder()
                .classesDirectory(CLASSES).packageName("com.example").packageAid("A0000000620101")
                .applet("com.example.TestApplet", "A000000062010101").build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("package AID A0000000620101 is the AID of the imported package javacard.framework");
    }

    @Test
    void aidOfAKnownPackageThatIsNotImportedIsReportedAsWarning_jcvm31_4_2_2_3() throws Exception {
        // A000000062010101 is javacard.framework.service, which TestApplet does not import
        ConverterResult result = Converter.builder()
                .classesDirectory(CLASSES).packageName("com.example").packageAid("A000000062010101")
                .applet("com.example.TestApplet", "A00000006201010101").build().convert();

        assertThat(result.warnings()).singleElement().asString()
                .contains("package AID A000000062010101 is the AID of the package javacard.framework.service");
    }

    @Test
    void generatedPackageAidIsReportedAsWarning_jcvm31_4_2_1() throws Exception {
        ConverterResult result = Converter.builder()
                .classesDirectory(CLASSES).packageName("com.example.multiclass")
                .build().convert();

        String generated = java.util.HexFormat.of().withUpperCase()
                .formatHex(Converter.Builder.generateAid("com.example.multiclass"));
        assertThat(result.warnings()).singleElement().asString()
                .contains("no package AID was configured; using " + generated);
        assertThat(CapInspector.headerPackage(result.capFile()).aidHex()).isEqualTo(generated);
    }

    @Test
    void malformedHexAidIsRejectedByTheBuilder() {
        assertThatThrownBy(() -> Converter.builder().packageAid("A00000006"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid AID 'A00000006'");
    }
}
