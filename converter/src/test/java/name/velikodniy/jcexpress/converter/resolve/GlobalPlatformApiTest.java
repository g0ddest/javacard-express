package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The built-in linking data of {@code org.globalplatform} ({@link GlobalPlatformApi}): one table that yields the
 * export file of every version of the package (JCVM 3.1 chapter 5), because later versions only add classes and
 * methods (§4.4).
 */
class GlobalPlatformApiTest {

    private static final String GP_SYSTEM = "org/globalplatform/GPSystem";

    @Test
    void packageAidIsTheOneOfTheCardSpecification_gpcs231_a1() {
        // GlobalPlatform Card Specification v2.3.1, Annex A.1: the AID of the export file is 'A00000015100'
        assertThat(HexFormat.of().withUpperCase().formatHex(GlobalPlatformApi.aid())).isEqualTo("A00000015100");
    }

    @Test
    void versionsAreAscendingAndTheLastIsTheLatest() {
        assertThat(GlobalPlatformApi.versions())
                .containsExactly("1.0", "1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8");
        assertThat(GlobalPlatformApi.latest()).isEqualTo("1.8");
    }

    @ParameterizedTest
    @CsvSource({"1.0, 0, 4", "1.1, 1, 7", "1.2, 2, 9", "1.3, 3, 11", "1.4, 4, 12", "1.5, 5, 12", "1.6, 6, 12",
            "1.7, 7, 13", "1.8, 8, 15"})
    void exportOfAVersionHasItsPackageVersionAndItsClasses_jcvm31_5_6_1(String version, int minor, int classes) {
        ExportFile export = GlobalPlatformApi.export(version);

        assertThat(export.packageName()).isEqualTo("org/globalplatform");
        assertThat(export.majorVersion()).isEqualTo(1);
        assertThat(export.minorVersion()).isEqualTo(minor);
        assertThat(export.isLibrary()).isTrue();
        assertThat(export.classes()).hasSize(classes);
        assertThat(export.classes()).extracting(ExportFile.ClassExport::token)
                .as("class tokens of a version are 0..n-1 (JCVM 3.1 §4.3.7.2)")
                .containsExactlyInAnyOrderElementsOf(java.util.stream.IntStream.range(0, classes).boxed().toList());
    }

    @Test
    void tokensAreTheSameInEveryVersionThatHasTheElement_jcvm31_4_4() {
        for (String version : GlobalPlatformApi.versions()) {
            ExportFile.ClassExport system = GlobalPlatformApi.export(version).findClass("GPSystem");
            assertThat(system.token()).as("GPSystem in %s", version).isEqualTo(3);
            assertThat(method(system, "getSecureChannel").token()).as("getSecureChannel in %s", version).isEqualTo(4);
            ExportFile.ClassExport channel = GlobalPlatformApi.export(version).findClass("SecureChannel");
            assertThat(channel.token()).isEqualTo(2);
            assertThat(method(channel, "processSecurity").token()).isZero();
            assertThat(method(channel, "unwrap").token()).isEqualTo(2);
        }
    }

    @Test
    void laterMethodsOfAClassAreAbsentFromEarlierVersions() {
        assertThat(methodNames("1.0", "GPSystem")).doesNotContain("getRegistryEntry", "getService", "getCardInfo");
        assertThat(methodNames("1.1", "GPSystem")).contains("getRegistryEntry", "getService")
                .doesNotContain("getCardInfo", "getCardInfoLength");
        assertThat(methodNames("1.7", "GPSystem")).contains("getCardInfo", "getCardInfoLength");
    }

    @Test
    void interfacesAreShareableAndListTheirSuperinterfaces_jcvm31_5_7() {
        ExportFile.ClassExport channelx = GlobalPlatformApi.export("1.8").findClass("SecureChannelx");

        assertThat(channelx.isInterface()).isTrue();
        assertThat(channelx.isShareable()).isTrue();
        assertThat(channelx.interfaces()).contains("org/globalplatform/SecureChannel", "javacard/framework/Shareable");
        // §5.9: an interface lists the methods of its superinterfaces too, with their tokens
        assertThat(method(channelx, "processSecurity").token()).isZero();
        assertThat(method(channelx, "setSecurityLevel").token()).isEqualTo(7);
    }

    @Test
    void introducedInNamesTheFirstVersionOfAClassOrMethod() {
        assertThat(GlobalPlatformApi.introducedIn(GP_SYSTEM, null, null)).contains("1.0");
        assertThat(GlobalPlatformApi.introducedIn(GP_SYSTEM, "getRegistryEntry",
                "(Ljavacard/framework/AID;)Lorg/globalplatform/GPRegistryEntry;")).contains("1.1");
        assertThat(GlobalPlatformApi.introducedIn(GP_SYSTEM, "getCardInfo", "([BSS)S")).contains("1.7");
        assertThat(GlobalPlatformApi.introducedIn("org/globalplatform/Personalization", null, null)).contains("1.2");
        assertThat(GlobalPlatformApi.introducedIn("org/globalplatform/AdministrationSession", null, null))
                .contains("1.8");
        assertThat(GlobalPlatformApi.introducedIn(GP_SYSTEM, "unofficial", "()V")).isEmpty();
        assertThat(GlobalPlatformApi.introducedIn("org/globalplatform/Unofficial", null, null)).isEmpty();
    }

    @Test
    void unknownVersionIsRejectedWithTheKnownVersions() {
        assertThatThrownBy(() -> GlobalPlatformApi.export("1.9"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'1.9' is not a version of the GlobalPlatform Card API package"
                        + " org.globalplatform")
                .hasMessageContaining("1.0, 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8");
        assertThatThrownBy(() -> GlobalPlatformApi.requireVersion("2.3.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'2.3.1' is not a version");
        assertThatThrownBy(() -> GlobalPlatformApi.requireVersion(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(GlobalPlatformApi.requireVersion("1.5")).isEqualTo("1.5");
    }

    @Test
    void onlyTheConvertersOwnDataIsBuiltIn_jcvm31_4_5_2() throws IOException {
        ExportFile view = GlobalPlatformApi.export("1.2");
        // an export file a user supplies is not built-in even with the same content: its version is recorded
        ExportFile supplied = ExportFileReader.read(GlobalPlatformApi.exportFileBytes("1.2", JavaCardVersion.V3_0_5));
        ExportFile other = BuiltinExports.getExport("javacard/framework", JavaCardVersion.V3_0_5);

        assertThat(supplied).isEqualTo(view);
        assertThat(GlobalPlatformApi.isBuiltin(imported(view))).isTrue();
        assertThat(GlobalPlatformApi.isBuiltin(imported(supplied))).isFalse();
        assertThat(GlobalPlatformApi.isBuiltin(imported(other))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(JavaCardVersion.class)
    void exportFileBytesAreReadBackWithTheSameContent_jcvm31_5_5(JavaCardVersion target) throws IOException {
        for (String version : GlobalPlatformApi.versions()) {
            ExportFile expected = GlobalPlatformApi.export(version);

            ExportFile read = ExportFileReader.read(GlobalPlatformApi.exportFileBytes(version, target));

            assertThat(read.formatMajor() + "." + read.formatMinor()).as("export file format for %s", target)
                    .isEqualTo(target.exportFormatMajor() + "." + target.exportFormatMinor());
            assertThat(read.packageName()).isEqualTo(expected.packageName());
            assertThat(read.aid()).isEqualTo(expected.aid());
            assertThat(read.majorVersion() + "." + read.minorVersion()).isEqualTo(version);
            assertThat(read.packageFlags()).isEqualTo(ExportFile.ACC_LIBRARY);
            assertThat(read.classes()).as("classes of %s for %s", version, target).isEqualTo(expected.classes());
        }
    }

    @Test
    void format23ExportFileReferencesThePackagesOfItsTypes_jcvm31_5_5() throws IOException {
        ExportFile read = ExportFileReader.read(GlobalPlatformApi.exportFileBytes("1.8", JavaCardVersion.V3_1_0));

        // java.lang.Object is a superclass, Shareable an interface, APDU and AID descriptor types
        assertThat(read.referencedPackages()).extracting(ExportFile.PackageReference::name)
                .containsExactlyInAnyOrder("java/lang", "javacard/framework");
        assertThat(read.referencedPackages()).extracting(ref -> ref.majorVersion() + "." + ref.minorVersion())
                .containsExactlyInAnyOrder("1.0", "1.8");
        assertThat(ExportFileReader.read(GlobalPlatformApi.exportFileBytes("1.8", JavaCardVersion.V3_0_5))
                .referencedPackages()).isEmpty();
    }

    private static ImportedPackage imported(ExportFile export) {
        return new ImportedPackage(0, export.aid(), export.majorVersion(), export.minorVersion(), export);
    }

    private static ExportFile.MethodExport method(ExportFile.ClassExport cls, String name) {
        return cls.methods().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow();
    }

    private static List<String> methodNames(String version, String simpleName) {
        return GlobalPlatformApi.export(version).findClass(simpleName).methods().stream()
                .map(ExportFile.MethodExport::name).toList();
    }
}
