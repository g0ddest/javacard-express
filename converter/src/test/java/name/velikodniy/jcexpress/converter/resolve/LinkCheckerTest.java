package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassFileReader;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Kind;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Reference;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Messages of the link check (JCVM 3.1 §4.3.3, §4.3.5): every reference that cannot be linked is
 * reported with its location, and the reason says what to do about it.
 */
class LinkCheckerTest {

    private static final String WHERE = "com.acme.p.C.make()V (C.java:5)";

    private static void check(JavaCardVersion version, Reference... refs) throws ConverterException {
        LinkChecker.check(List.of(refs), BuiltinExports.allBuiltinImports(0, version), List.of(), version);
    }

    private static Reference call(String owner, String name, String descriptor) {
        return new Reference(Kind.STATIC_METHOD, owner, name, descriptor, WHERE);
    }

    @Test
    void javaSePackageIsReportedAsNotPartOfTheJavaCardPlatform_jcvm31_2_2_1_4() {
        // javac emits Objects.requireNonNull itself, e.g. for outer.new Inner()
        Reference ref = call("java/util/Objects", "requireNonNull", "(Ljava/lang/Object;)Ljava/lang/Object;");

        assertThatThrownBy(() -> check(JavaCardVersion.V3_0_5, ref))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining(WHERE + ": there is no export file for package java.util: the Java SE"
                        + " library is not part of the Java Card platform (JCVM 3.1 §2.2.1.4)")
                .hasMessageNotContaining("use importExportFile");
    }

    @Test
    void javaxPackageIsReportedAsNotPartOfTheJavaCardPlatform_jcvm31_2_2_1_4() {
        Reference ref = call("javax/crypto/Cipher", "getInstance", "(Ljava/lang/String;)Ljavax/crypto/Cipher;");

        assertThatThrownBy(() -> check(JavaCardVersion.V3_0_5, ref))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("there is no export file for package javax.crypto: the Java SE library is"
                        + " not part of the Java Card platform");
    }

    @Test
    void otherPackageWithoutExportFileAsksForItsExportFile_jcvm31_4_3_3() {
        Reference ref = call("com/acme/lib/Lib", "run", "()V");

        assertThatThrownBy(() -> check(JavaCardVersion.V3_0_5, ref))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining(WHERE + ": no export file was supplied for package com.acme.lib"
                        + " (use importExportFile or exportPath)");
    }

    @Test
    void javaCardApiPackageOfALaterVersionNamesThatVersion_jcvm31_4_5_2() {
        Reference ref = new Reference(Kind.INTERFACE, "javacardx/apdu/ExtendedLength", null, null, "com.acme.p.C");

        assertThatThrownBy(() -> check(JavaCardVersion.V2_2_1, ref))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("com.acme.p.C: package javacardx.apdu is not part of the Java Card 2.2.1 API"
                        + " (it was introduced in Java Card 2.2.2)");
    }

    @Test
    void everyProblemIsReportedInOneException() {
        assertThatThrownBy(() -> check(JavaCardVersion.V3_0_5,
                call("java/util/Arrays", "fill", "([BB)V"),
                call("java/lang/Math", "max", "(II)I")))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("Cannot link 2 reference(s) against the export files for Java Card 3.0.5")
                .hasMessageContaining("package java.util")
                .hasMessageContaining("java.lang.Math is not available in the Java Card 3.0.5 API (java.lang 1.0)");
    }

    @Test
    void missingClassOfThePackageBeingConvertedIsNotReportedAsAMissingExportFile() throws Exception {
        // e.g. a class file deleted from the classes directory: the package itself is never imported
        ClassInfo applet = ClassFileReader.readFile(Path.of("target/test-classes/com/example/TestApplet.class"));
        Reference ref = call("com/example/Missing", "run", "()V");

        assertThatThrownBy(() -> LinkChecker.check(List.of(ref), BuiltinExports.allBuiltinImports(0,
                JavaCardVersion.V3_0_5), List.of(applet), JavaCardVersion.V3_0_5))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining(WHERE + ": class com.example.Missing of the package being converted"
                        + " (com.example) has no class file in the classes directory")
                .hasMessageNotContaining("no export file was supplied");
    }

    @Test
    void linkableApiReferencesPass() {
        assertThatCode(() -> check(JavaCardVersion.V3_0_5,
                call("javacard/framework/Util", "arrayFill", "([BSSB)S")))
                .doesNotThrowAnyException();
    }
}
