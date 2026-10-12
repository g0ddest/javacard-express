package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassFileReader;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Kind;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Reference;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
    void problemsListEveryReferenceWithThePackageItIsAboutAndTheMessageOfTheCheck_jcvm31_4_3_5() {
        Reference util = call("java/util/Objects", "requireNonNull", "(Ljava/lang/Object;)Ljava/lang/Object;");
        Reference lib = call("com/acme/lib/Lib", "run", "()V");
        Reference fine = call("javacard/framework/Util", "arrayFillNonAtomic", "([BSSB)S");

        List<LinkChecker.Problem> problems = LinkChecker.problems(List.of(util, lib, fine),
                BuiltinExports.allBuiltinImports(0, JavaCardVersion.V3_0_5), List.of(), JavaCardVersion.V3_0_5);

        assertThat(problems).extracting(LinkChecker.Problem::reference, LinkChecker.Problem::packageName)
                .containsExactly(tuple(util, "java/util"), tuple(lib, "com/acme/lib"));
        assertThat(problems.getFirst().message()).isEqualTo("there is no export file for package java.util: the"
                + " Java SE library is not part of the Java Card platform (JCVM 3.1 §2.2.1.4)");
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

    // ── the built-in data of org.globalplatform has its own versions ──

    /** The API of a Java Card version plus an export file of org.globalplatform. */
    private static List<ImportedPackage> withGlobalPlatform(JavaCardVersion version, ExportFile globalPlatform) {
        List<ImportedPackage> imports = new ArrayList<>(BuiltinExports.allBuiltinImports(0, version));
        imports.add(new ImportedPackage(imports.size(), globalPlatform.aid(), globalPlatform.majorVersion(),
                globalPlatform.minorVersion(), globalPlatform));
        return imports;
    }

    @Test
    void globalPlatformMethodOfALaterVersionNamesTheVersionsOfThatApi_jcvm31_4_5_2() {
        Reference ref = call("org/globalplatform/GPSystem", "getCardInfo", "([BSS)S");
        List<ImportedPackage> imports = withGlobalPlatform(JavaCardVersion.V3_0_4, GlobalPlatformApi.export("1.0"));

        assertThatThrownBy(() -> LinkChecker.check(List.of(ref), imports, List.of(), JavaCardVersion.V3_0_4))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("Cannot link 1 reference(s) against the export files for Java Card 3.0.4 and"
                        + " version 1.0 of the GlobalPlatform Card API:")
                .hasMessageContaining(WHERE + ": org.globalplatform.GPSystem.getCardInfo([BSS)S is not available in"
                        + " version 1.0 of the GlobalPlatform Card API (org.globalplatform 1.0); it was introduced in"
                        + " version 1.7 (the version is fixed with globalPlatformApi: set it to 1.7 or later, or leave"
                        + " it unset to import the lowest version that has everything the package uses)")
                .hasMessageNotContaining("introduced in Java Card");
    }

    @Test
    void globalPlatformClassOfALaterVersionNamesTheVersionThatIntroducedIt_jcvm31_4_5_2() {
        Reference ref = new Reference(Kind.INTERFACE, "org/globalplatform/Personalization", null, null,
                "com.acme.p.C");
        List<ImportedPackage> imports = withGlobalPlatform(JavaCardVersion.V3_0_5, GlobalPlatformApi.export("1.1"));

        assertThatThrownBy(() -> LinkChecker.check(List.of(ref), imports, List.of(), JavaCardVersion.V3_0_5))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("com.acme.p.C: org.globalplatform.Personalization is not available in version"
                        + " 1.1 of the GlobalPlatform Card API (org.globalplatform 1.1); it was introduced in version"
                        + " 1.2");
    }

    @Test
    void globalPlatformElementOfNoVersionIsReportedAsSuch_jcvm31_4_3_5() {
        Reference ref = call("org/globalplatform/GPSystem", "unofficial", "()V");
        List<ImportedPackage> imports = withGlobalPlatform(JavaCardVersion.V3_0_5, GlobalPlatformApi.export("1.8"));

        assertThatThrownBy(() -> LinkChecker.check(List.of(ref), imports, List.of(), JavaCardVersion.V3_0_5))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("org.globalplatform.GPSystem.unofficial()V is not available in version 1.8 of"
                        + " the GlobalPlatform Card API (org.globalplatform 1.8); no version of that API up to 1.8 has"
                        + " it")
                .hasMessageNotContaining("globalPlatformApi");
    }

    @Test
    void headlineNamesTheGlobalPlatformApiOnlyWhenAProblemIsAboutIt() {
        Reference ref = call("java/lang/Math", "max", "(II)I");
        List<ImportedPackage> imports = withGlobalPlatform(JavaCardVersion.V3_0_5, GlobalPlatformApi.export("1.0"));

        assertThatThrownBy(() -> LinkChecker.check(List.of(ref), imports, List.of(), JavaCardVersion.V3_0_5))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("Cannot link 1 reference(s) against the export files for Java Card 3.0.5:\n")
                .hasMessageNotContaining("GlobalPlatform");
    }

    @Test
    void suppliedExportFileOfGlobalPlatformIsDescribedAsAnExportFile_jcvm31_4_3_3() throws Exception {
        // the same content, but supplied by the user: the built-in data has no say about its versions
        ExportFile supplied = ExportFileReader.read(GlobalPlatformApi.exportFileBytes("1.0", JavaCardVersion.V3_0_5));
        Reference ref = call("org/globalplatform/GPSystem", "getCardInfo", "([BSS)S");
        List<ImportedPackage> imports = withGlobalPlatform(JavaCardVersion.V3_0_5, supplied);

        assertThatThrownBy(() -> LinkChecker.check(List.of(ref), imports, List.of(), JavaCardVersion.V3_0_5))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("Cannot link 1 reference(s) against the export files for Java Card 3.0.5:\n")
                .hasMessageContaining("org.globalplatform.GPSystem.getCardInfo([BSS)S is not available in the export"
                        + " file of org.globalplatform 1.0 (no such member in the export file)");
    }

    @Test
    void otherGlobalPlatformPackageStillNeedsItsExportFile_jcvm31_4_3_3() {
        // the built-in data covers org.globalplatform only, not org.globalplatform.upgrade and the like
        Reference ref = call("org/globalplatform/upgrade/UpgradeManager", "isUpgrading", "()Z");
        List<ImportedPackage> imports = withGlobalPlatform(JavaCardVersion.V3_0_5, GlobalPlatformApi.export("1.8"));

        assertThatThrownBy(() -> LinkChecker.check(List.of(ref), imports, List.of(), JavaCardVersion.V3_0_5))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining(WHERE + ": no export file was supplied for package org.globalplatform.upgrade"
                        + " (use importExportFile or exportPath)");
    }

    @Test
    void unlinkableReturnsTheReferencesThatCheckWouldReport() {
        Reference linkable = call("org/globalplatform/GPSystem", "getSecureChannel",
                "()Lorg/globalplatform/SecureChannel;");
        Reference later = call("org/globalplatform/GPSystem", "getCardInfo", "([BSS)S");
        Reference foreign = call("com/acme/lib/Lib", "run", "()V");
        List<Reference> refs = List.of(linkable, later, foreign);

        assertThat(LinkChecker.unlinkable(refs, withGlobalPlatform(JavaCardVersion.V3_0_5,
                GlobalPlatformApi.export("1.6")), List.of(), JavaCardVersion.V3_0_5))
                .containsExactly(later, foreign);
        assertThat(LinkChecker.unlinkable(refs, withGlobalPlatform(JavaCardVersion.V3_0_5,
                GlobalPlatformApi.export("1.7")), List.of(), JavaCardVersion.V3_0_5))
                .containsExactly(foreign);
    }

    /**
     * A method a class of the package inherits from a supertype in another package: without that package's export
     * file the method cannot be found there, and the problem is about that package. The conversion's message is the
     * one it always gave.
     */
    @Test
    void aMethodInheritedFromAPackageWithoutExportFileIsAProblemOfThatPackage_jcvm31_4_3_3(@TempDir Path classes)
            throws Exception {
        JavaSources.compile(classes, Map.of("lib.Wallet", """
                package lib;
                public interface Wallet extends javacard.framework.Shareable {
                    void debit(short amount);
                }
                """, "app.BonusWallet", """
                package app;
                public interface BonusWallet extends lib.Wallet {
                }
                """, "app.Client", """
                package app;
                public class Client {
                    void pay(BonusWallet w) {
                        w.debit((short) 5);
                    }
                }
                """));
        List<byte[]> files = List.of(Files.readAllBytes(classes.resolve("app/BonusWallet.class")),
                Files.readAllBytes(classes.resolve("app/Client.class")));
        List<ClassInfo> app = List.of(ClassFileReader.read(files.get(0)), ClassFileReader.read(files.get(1)));
        List<Reference> refs = ClassReferences.scan(files).stream()
                .filter(r -> "debit".equals(r.name())).toList();
        List<ImportedPackage> imports = BuiltinExports.allBuiltinImports(0, JavaCardVersion.V3_0_5);

        assertThat(LinkChecker.problems(refs, imports, app, JavaCardVersion.V3_0_5))
                .extracting(LinkChecker.Problem::packageName).containsExactly("lib");
        assertThatThrownBy(() -> LinkChecker.check(refs, imports, app, JavaCardVersion.V3_0_5))
                .hasMessageEndingWith(":\n  - app.Client.pay(Lapp/BonusWallet;)V (Client.java:4): java.lang.Object"
                        + ".debit(S)V is not available in the Java Card 3.0.5 API (java.lang 1.0) (no such member in"
                        + " the export file)");
    }
}
