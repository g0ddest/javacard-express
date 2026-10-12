package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi;
import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.ExternalRef;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.PackageRef;
import name.velikodniy.jcexpress.converter.testutil.ExpFixture;
import name.velikodniy.jcexpress.converter.testutil.GlobalPlatformStandIns;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import name.velikodniy.jcexpress.converter.translate.CapView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Linking against the GlobalPlatform Card API package {@code org.globalplatform} with the converter's built-in
 * data ({@link GlobalPlatformApi}): no export file is needed (JCVM 3.1 §4.3.3), the tokens are those of the
 * package (§4.3.7), and the Import component records the lowest version of the package that has everything the
 * CAP file references, or the version fixed with {@code globalPlatformApi} (§4.5.2, §6.7). The probes compile
 * against stand-in sources of the API, declared by the test.
 */
class GlobalPlatformApiLinkingTest {

    private static final String GP_AID = "A00000015100";
    private static final int CLASS_REF = 1;
    private static final int STATIC_METHOD_REF = 6;

    @TempDir
    Path classes;

    @TempDir
    Path files;

    private static final Pattern TYPE_NAME = Pattern.compile("public (?:class|interface) (\\w+)");

    /** Compiles the sources (each declares one public type of package com.acme.gp) with the stand-in API. */
    private Converter.Builder probe(String... sources) {
        return library(sources).applet("com.acme.gp.Probe", "A000000FFE5001");
    }

    /** Like {@link #probe}, for a package without applets. */
    private Converter.Builder library(String... sources) {
        Map<String, String> units = new LinkedHashMap<>(GlobalPlatformStandIns.sources());
        for (String source : sources) {
            Matcher type = TYPE_NAME.matcher(source);
            assertThat(type.find()).as("a public type in the source").isTrue();
            units.put("com.acme.gp." + type.group(1), source);
        }
        JavaSources.compile(classes, units);
        return Converter.builder()
                .classesDirectory(classes)
                .packageName("com.acme.gp")
                .packageAid("A000000FFE50");
    }

    /** An applet whose process method has the given body; {@code members} are further class members. */
    private static String applet(String members, String body) {
        return """
                package com.acme.gp;
                import javacard.framework.*;
                import org.globalplatform.*;
                public class Probe extends Applet {
                %s
                    public static void install(byte[] b, short o, byte l) { new Probe().register(); }
                    public void process(APDU apdu) {
                        byte[] buf = apdu.getBuffer();
                %s
                    }
                }
                """.formatted(members, body);
    }

    private static final String SECURE_CHANNEL_USE = """
                    SecureChannel channel = GPSystem.getSecureChannel();
                    if (buf[ISO7816.OFFSET_INS] == (byte) 0x50) {
                        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, channel.processSecurity(apdu));
                        return;
                    }
                    short length = channel.unwrap(buf, (short) 0, (short) (apdu.setIncomingAndReceive() + 5));
                    if ((channel.getSecurityLevel() & SecureChannel.C_MAC) == 0) ISOException.throwIt((short) 0x6982);
                    if (GPSystem.getCardContentState() != GPSystem.APPLICATION_SELECTABLE) channel.resetSecurity();
            """;

    private static String importedVersion(ConverterResult result) {
        return CapInspector.imports(result.capFile()).stream().filter(p -> p.aidHex().equals(GP_AID))
                .map(PackageRef::version).findFirst().orElse("not imported");
    }

    // ── the built-in data replaces the export file (§4.3.3) and gives the package's tokens (§4.3.7) ──

    @Test
    void secureChannelAppletLinksWithoutAnExportFileAndImportsVersion1_0_jcvm31_4_5_2() throws Exception {
        ConverterResult result = probe(applet("", SECURE_CHANNEL_USE)).build().convert();

        assertThat(importedVersion(result)).isEqualTo("1.0");
        assertThat(result.imports()).filteredOn(i -> i.packageName().equals("org.globalplatform"))
                .containsExactly(new ConverterResult.Import("org.globalplatform", GP_AID, 1, 0, true));
    }

    @Test
    void constantPoolCarriesTheTokensOfThePackage_jcvm31_4_3_7() throws Exception {
        ConverterResult result = probe(applet("", SECURE_CHANNEL_USE)).build().convert();

        // GPSystem is class token 3: getSecureChannel static method token 4, getCardContentState 1 (§6.8.6)
        assertThat(CapInspector.externalRefs(result.capFile())).contains(
                new ExternalRef(STATIC_METHOD_REF, GP_AID, 3, 4),
                new ExternalRef(STATIC_METHOD_REF, GP_AID, 3, 1),
                // SecureChannel is class token 2; an interface is referenced by a class reference (§6.8.1) ...
                new ExternalRef(CLASS_REF, GP_AID, 2, -1));
        // ... and its methods by their interface method tokens in the instruction (§7.5.56):
        // processSecurity 0, unwrap 2, getSecurityLevel 6, resetSecurity 5
        List<String> invocations = CapView.parse(result.capFile()).disassembledMethods().stream()
                .flatMap(List::stream).filter(line -> line.startsWith("invokeinterface")).toList();
        assertThat(invocations).extracting(line -> line.substring(line.lastIndexOf(' ') + 1))
                .containsExactlyInAnyOrder("0", "2", "6", "5");
    }

    @ParameterizedTest
    @EnumSource(JavaCardVersion.class)
    void importedVersionDoesNotDependOnTheJavaCardVersion_jcvm31_4_5_2(JavaCardVersion target) throws Exception {
        ConverterResult result = probe(applet("", SECURE_CHANNEL_USE)).javaCardVersion(target).build().convert();

        assertThat(importedVersion(result)).isEqualTo("1.0");
    }

    // ── the lowest version that has everything the package references (§4.5.2) ──

    @Test
    void implementingPersonalizationNeedsVersion1_2_jcvm31_4_5_2() throws Exception {
        String source = """
                package com.acme.gp;
                import javacard.framework.*;
                import org.globalplatform.Personalization;
                public class Probe extends Applet implements Personalization {
                    public static void install(byte[] b, short o, byte l) { new Probe().register(); }
                    public void process(APDU apdu) { }
                    public short processData(byte[] in, short inOffset, short inLength, byte[] out, short outOffset) {
                        return 0;
                    }
                }
                """;

        assertThat(importedVersion(probe(source).build().convert())).isEqualTo("1.2");
    }

    @Test
    void methodAddedToAClassLaterRaisesTheVersion_jcvm31_4_5_2() throws Exception {
        String body = "        GPSystem.getCardInfo(buf, (short) 0, (short) 0x80);\n";

        assertThat(importedVersion(probe(applet("", body)).build().convert())).isEqualTo("1.7");
    }

    @Test
    void newestInterfaceNeedsTheNewestVersion_jcvm31_4_5_2() throws Exception {
        String body = """
                    GlobalService service = GPSystem.getService(null, (short) 0x8400);
                    ((AdministrationSession) service).requestAdministrationSession(buf, (short) 5, (short) 0);
            """;

        assertThat(importedVersion(probe(applet("", body)).build().convert())).isEqualTo("1.8");
    }

    @Test
    void typeOfAFieldCounts_jcvm31_6_14() throws Exception {
        // never used by code: the Descriptor component still names the class (§6.14)
        String members = "    private GPRegistryEntry entry;\n";

        assertThat(importedVersion(probe(applet(members, "")).build().convert())).isEqualTo("1.1");
    }

    @Test
    void typeInAMethodDescriptorCounts_jcvm31_6_14() throws Exception {
        String members = "    private short length(AuthoritySignature signature) { return 0; }\n";

        assertThat(importedVersion(probe(applet(members, "")).build().convert())).isEqualTo("1.7");
    }

    @Test
    void methodInheritedByALaterInterfaceCountsAsThatInterface_jcvm31_4_5_2() throws Exception {
        // processSecurity is SecureChannel's since 1.0, but it is referenced as SecureChannelx's (1.1)
        String body = """
                    SecureChannelx channel = (SecureChannelx) GPSystem.getSecureChannel();
                    apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, channel.processSecurity(apdu));
            """;

        assertThat(importedVersion(probe(applet("", body)).build().convert())).isEqualTo("1.1");
    }

    @Test
    void methodInheritedThroughAnInterfaceOfThePackageBeingConvertedCounts_jcvm31_4_5_2() throws Exception {
        String own = """
                package com.acme.gp;
                public interface OwnChannel extends org.globalplatform.SecureChannelx2 { }
                """;
        String body = """
                    OwnChannel channel = (OwnChannel) GPSystem.getSecureChannel();
                    channel.unwrap(buf, (short) 0, (short) 5);
            """;

        assertThat(importedVersion(probe(applet("", body), own).build().convert())).isEqualTo("1.4");
    }

    @Test
    void packageThatDoesNotUseTheApiDoesNotImportIt_jcvm31_6_7() throws Exception {
        ConverterResult result = probe(applet("", "")).build().convert();

        assertThat(importedVersion(result)).isEqualTo("not imported");
        assertThat(result.imports()).extracting(ConverterResult.Import::packageName)
                .containsExactly("javacard.framework", "java.lang");
        assertThat(result.imports()).allMatch(ConverterResult.Import::builtIn);
    }

    @Test
    void fixedVersionChangesNothingForAPackageThatDoesNotUseTheApi() throws Exception {
        byte[] automatic = probe(applet("", "")).build().convert().capFile();

        byte[] fixed = probe(applet("", "")).globalPlatformApi("1.8").build().convert().capFile();

        assertThat(fixed).isEqualTo(automatic);
    }

    // ── a fixed version (§4.5.2) ──

    @Test
    void fixedLaterVersionIsRecordedAndChangesNothingElse_jcvm31_4_4() throws Exception {
        ConverterResult automatic = probe(applet("", SECURE_CHANNEL_USE)).build().convert();

        ConverterResult fixed = probe(applet("", SECURE_CHANNEL_USE)).globalPlatformApi("1.5").build().convert();

        assertThat(importedVersion(fixed)).isEqualTo("1.5");
        // binary compatibility: only the version in the Import component differs
        Map<String, byte[]> a = CapInspector.components(automatic.capFile());
        Map<String, byte[]> f = CapInspector.components(fixed.capFile());
        assertThat(f.keySet()).isEqualTo(a.keySet());
        f.forEach((name, bytes) -> {
            if (!name.equals("Import.cap")) assertThat(bytes).as(name).isEqualTo(a.get(name));
        });
    }

    @Test
    void fixedVersionThatLacksAUsedTypeIsRejectedWithPlaceAndIntroducingVersion_jcvm31_4_3_5() {
        String source = """
                package com.acme.gp;
                import javacard.framework.*;
                import org.globalplatform.Personalization;
                public class Probe extends Applet implements Personalization {
                    public static void install(byte[] b, short o, byte l) { new Probe().register(); }
                    public void process(APDU apdu) { }
                    public short processData(byte[] in, short inOffset, short inLength, byte[] out, short outOffset) {
                        return 0;
                    }
                }
                """;

        assertThatThrownBy(() -> probe(source).globalPlatformApi("1.0").javaCardVersion(JavaCardVersion.V3_0_4)
                .build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("against the export files for Java Card 3.0.4 and version 1.0 of the"
                        + " GlobalPlatform Card API")
                .hasMessageContaining("com.acme.gp.Probe: org.globalplatform.Personalization is not available in"
                        + " version 1.0 of the GlobalPlatform Card API (org.globalplatform 1.0); it was introduced in"
                        + " version 1.2")
                .hasMessageContaining("globalPlatformApi: set it to 1.2 or later, or leave it unset");
    }

    @Test
    void fixedVersionThatLacksAUsedMethodNamesMethodAndSourceLine_jcvm31_4_3_5() {
        String body = "        GPSystem.getCardInfo(buf, (short) 0, (short) 0x80);\n";

        assertThatThrownBy(() -> probe(applet("", body)).globalPlatformApi("1.6").build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("com.acme.gp.Probe.process(Ljavacard/framework/APDU;)V (Probe.java:9)")
                .hasMessageContaining("org.globalplatform.GPSystem.getCardInfo([BSS)S is not available in version 1.6"
                        + " of the GlobalPlatform Card API (org.globalplatform 1.6); it was introduced in version 1.7");
    }

    @Test
    void memberThatNoVersionHasIsReportedAsSuch_jcvm31_4_3_5() {
        String body = "        GPSystem.unofficial();\n        Unofficial.touch();\n";

        assertThatThrownBy(() -> probe(applet("", body)).build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("Cannot link 2 reference(s)")
                .hasMessageContaining("org.globalplatform.GPSystem.unofficial()V is not available in version 1.0 of the"
                        + " GlobalPlatform Card API (org.globalplatform 1.0); no version of that API up to 1.8 has it")
                .hasMessageContaining("org.globalplatform.Unofficial is not available in version 1.0 of the"
                        + " GlobalPlatform Card API (org.globalplatform 1.0); no version of that API up to 1.8 has it");
    }

    @Test
    void nullMeansTheLowestSufficientVersionAgain() throws Exception {
        ConverterResult result = probe(applet("", SECURE_CHANNEL_USE)).globalPlatformApi("1.5")
                .globalPlatformApi(null).build().convert();

        assertThat(importedVersion(result)).isEqualTo("1.0");
    }

    @Test
    void exportFileOfALibraryReferencesThePackageInTheImportedVersion_jcvm31_5_5() throws Exception {
        // export file format 2.3 lists the packages of the types in exported signatures, with their versions
        String helper = """
                package com.acme.gp;
                import org.globalplatform.GPRegistryEntry;
                public class Helper {
                    public static boolean selectable(GPRegistryEntry entry) { return entry.getState() == 7; }
                }
                """;

        ConverterResult result = library(helper).javaCardVersion(JavaCardVersion.V3_1_0).build().convert();

        assertThat(importedVersion(result)).isEqualTo("1.1");
        assertThat(ExportFileReader.read(result.exportFile()).referencedPackages())
                .filteredOn(referenced -> referenced.name().equals("org/globalplatform"))
                .extracting(referenced -> referenced.majorVersion() + "." + referenced.minorVersion())
                .containsExactly("1.1");
    }

    @Test
    void unknownVersionIsRejectedByTheBuilder() {
        assertThatThrownBy(() -> Converter.builder().globalPlatformApi("2.3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'2.3' is not a version of the GlobalPlatform Card API package org.globalplatform")
                .hasMessageContaining("1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8");
    }

    // ── a supplied export file takes precedence (§4.3.3) ──

    @Test
    void suppliedExportFileWinsAndItsVersionIsRecorded_jcvm31_4_5_2() throws Exception {
        Path supplied = exportFile("1.7");

        ConverterResult result = probe(applet("", SECURE_CHANNEL_USE)).importExportFile(supplied).build().convert();

        assertThat(importedVersion(result)).isEqualTo("1.7");
        assertThat(result.imports()).filteredOn(i -> i.packageName().equals("org.globalplatform"))
                .containsExactly(new ConverterResult.Import("org.globalplatform", GP_AID, 1, 7, false));
    }

    @Test
    void exportFileOnTheExportPathWinsToo_jcvm31_5_2() throws Exception {
        Path entry = files.resolve("exports/org/globalplatform/javacard/globalplatform.exp");
        Files.createDirectories(entry.getParent());
        Files.copy(exportFile("1.3"), entry);

        ConverterResult result = probe(applet("", SECURE_CHANNEL_USE)).exportPath(files.resolve("exports"))
                .build().convert();

        assertThat(importedVersion(result)).isEqualTo("1.3");
    }

    @Test
    void suppliedExportFileOfAnotherVersionThanTheFixedOneIsRejected_jcvm31_4_5_2() throws Exception {
        Path supplied = exportFile("1.7");

        assertThatThrownBy(() -> probe(applet("", SECURE_CHANNEL_USE)).importExportFile(supplied)
                .globalPlatformApi("1.2").build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("globalPlatformApi fixes version 1.2 of org.globalplatform")
                .hasMessageContaining(supplied.toString())
                .hasMessageContaining("describes version 1.7");
    }

    @Test
    void suppliedExportFileOfTheFixedVersionIsAccepted() throws Exception {
        Path supplied = exportFile("1.2");

        ConverterResult result = probe(applet("", SECURE_CHANNEL_USE)).importExportFile(supplied)
                .globalPlatformApi("1.2").build().convert();

        assertThat(importedVersion(result)).isEqualTo("1.2");
    }

    /** An export file of the package as a user would supply it (here written from the same data). */
    private Path exportFile(String version) throws IOException {
        return Files.write(files.resolve("globalplatform-" + version + ".exp"),
                ExpFixture.write(GlobalPlatformApi.export(version)));
    }
}
