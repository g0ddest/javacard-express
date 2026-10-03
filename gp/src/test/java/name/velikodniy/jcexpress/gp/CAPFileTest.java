package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CAPFile}: CAP file structure per JCVM 3.1 chapter 6 and the Load File of
 * GPCS v2.3.1 Table 11-58.
 */
class CAPFileTest {

    private static final String PACKAGE_AID = "A0000000620301";
    private static final String APPLET_AID = "A000000062030101";

    @Nested
    class Header {

        @Test
        void jcvm_6_4_packageAidAndVersionsComeFromTheHeaderComponent() {
            CAPFile cap = CAPFile.from(CapFixture.of(PACKAGE_AID).applets(APPLET_AID).build());

            assertThat(cap.packageAidHex()).isEqualTo(PACKAGE_AID);
            assertThat(cap.packageAid()).isEqualTo(Hex.decode(PACKAGE_AID));
            assertThat(cap.majorVersion()).isEqualTo(1);
            assertThat(cap.minorVersion()).isZero();
            assertThat(cap.isExtended()).isFalse();
            assertThat(cap).hasToString("CAPFile[aid=" + PACKAGE_AID + ", version=1.0, components=11]");
        }

        @Test
        void jcvm_6_4_extendedFormatIsFlaggedAndUsesTheCapAid() {
            CAPFile cap = CAPFile.from(CapFixture.of(PACKAGE_AID).applets(APPLET_AID).extended().build());

            assertThat(cap.isExtended()).isTrue();
            assertThat(cap.packageAidHex()).isEqualTo(PACKAGE_AID);
        }

        @Test
        void jcvm_6_2_1_componentFileNamesAreNotCaseSensitive() {
            byte[] zip = CapFixture.of(PACKAGE_AID).applets(APPLET_AID).inDirectory("com/example/JAVACARD/")
                    .lowerCaseFileNames().build();

            CAPFile cap = CAPFile.from(zip);

            assertThat(cap.appletAids()).extracting(Hex::encode).containsExactly(APPLET_AID);
            assertThat(cap.componentNames()).hasSize(11);
        }
    }

    @Nested
    class Components {

        @Test
        void jcvm_6_3_loadFileDataBlockFollowsTheReferenceComponentInstallOrder() {
            CapFixture fixture = CapFixture.of(PACKAGE_AID).applets(APPLET_AID).withDebug();
            CAPFile cap = CAPFile.from(fixture.build());

            assertThat(cap.componentNames()).containsExactly("Header", "Directory", "Import", "Applet", "Class",
                    "Method", "StaticField", "Export", "ConstantPool", "RefLocation", "Descriptor");
            assertThat(Hex.encode(cap.code())).isEqualTo(concat(fixture.components(), cap.componentNames()));
        }

        @Test
        void jcvm_6_3_debugComponentIsNeverLoaded() {
            CapFixture fixture = CapFixture.of(PACKAGE_AID).withDebug();
            CAPFile cap = CAPFile.from(fixture.build());

            assertThat(cap.componentNames()).doesNotContain("Debug");
            assertThat(Hex.encode(cap.code())).doesNotContain(Hex.encode(fixture.components().get("Debug")));
        }

        @Test
        void jcvm_6_2_1_extendedFormatComponentsAreReadFromCapxFiles() {
            CapFixture fixture = CapFixture.of(PACKAGE_AID).applets(APPLET_AID).extended();
            CAPFile cap = CAPFile.from(fixture.build());

            assertThat(cap.componentNames()).contains("Method", "RefLocation", "Descriptor");
            assertThat(Hex.encode(cap.code())).isEqualTo(concat(fixture.components(), cap.componentNames()));
        }

        @Test
        void jcvm_6_3_staticResourcesAreLoadedAfterRefLocationAndBeforeDescriptor() {
            CapFixture fixture = CapFixture.of(PACKAGE_AID).applets(APPLET_AID).withStaticResources();
            CAPFile cap = CAPFile.from(fixture.build());

            assertThat(cap.componentNames()).endsWith("RefLocation", "StaticResources", "Descriptor");
            assertThat(Hex.encode(cap.code())).isEqualTo(concat(fixture.components(), cap.componentNames()));
        }

        @Test
        void jcvm_6_3_descriptorIsOptionalForLoading() {
            CapFixture fixture = CapFixture.of(PACKAGE_AID).applets(APPLET_AID);
            CAPFile cap = CAPFile.from(fixture.build());

            List<String> withoutDescriptor = cap.componentNames().stream()
                    .filter(name -> !name.equals("Descriptor")).toList();
            assertThat(Hex.encode(cap.code(false))).isEqualTo(concat(fixture.components(), withoutDescriptor));
            assertThat(cap.code()).isEqualTo(cap.code(true));
            assertThat(cap.loadFileData(false)).hasSizeLessThan(cap.loadFileData().length);
        }

        @Test
        void jcvm_6_2_missingRequiredComponentIsRejected() {
            byte[] zip = CapFixture.of(PACKAGE_AID).without("Method").build();

            assertThatThrownBy(() -> CAPFile.from(zip))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("Method");
        }

        @Test
        void jcvm_6_2_optionalComponentsMayBeAbsent() {
            CAPFile cap = CAPFile.from(CapFixture.of(PACKAGE_AID).without("Export").build());

            assertThat(cap.componentNames()).containsExactly("Header", "Directory", "Import", "Class", "Method",
                    "StaticField", "ConstantPool", "RefLocation", "Descriptor");
        }

        @Test
        void componentPresentAsBothCapAndCapxIsRejected() {
            byte[] zip = CapFixture.of(PACKAGE_AID).alsoAsCapx("Method").build();

            assertThatThrownBy(() -> CAPFile.from(zip))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("Method.cap and Method.capx");
        }
    }

    @Nested
    class AppletComponent {

        @Test
        void jcvm_6_6_appletAidsAreTheExecutableModuleAids() {
            CAPFile cap = CAPFile.from(CapFixture.of(PACKAGE_AID)
                    .applets(APPLET_AID, "A00000006203010203").build());

            assertThat(cap.appletAids()).extracting(Hex::encode)
                    .containsExactly(APPLET_AID, "A00000006203010203");
        }

        @Test
        void jcvm_6_6_extendedAppletEntriesCarryAMethodBlockIndex() {
            CAPFile cap = CAPFile.from(CapFixture.of(PACKAGE_AID)
                    .applets(APPLET_AID, "A00000006203010203").extended().build());

            assertThat(cap.appletAids()).extracting(Hex::encode)
                    .containsExactly(APPLET_AID, "A00000006203010203");
        }

        @Test
        void libraryWithoutAppletComponentHasNoApplets() {
            CAPFile cap = CAPFile.from(CapFixture.of(PACKAGE_AID).build());

            assertThat(cap.appletAids()).isEmpty();
            assertThatThrownBy(cap::singleAppletAid)
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("0 applets");
        }

        @Test
        void singleAppletAidRequiresExactlyOneApplet() {
            CAPFile one = CAPFile.from(CapFixture.of(PACKAGE_AID).applets(APPLET_AID).build());
            CAPFile two = CAPFile.from(CapFixture.of(PACKAGE_AID).applets(APPLET_AID, "A00000006203010203").build());

            assertThat(Hex.encode(one.singleAppletAid())).isEqualTo(APPLET_AID);
            assertThatThrownBy(two::singleAppletAid)
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("2 applets");
        }

        @Test
        void truncatedAppletComponentIsRejected() {
            byte[] truncated = Hex.decode("03000502" + "08A000000062030101");  // count 2, only part of one entry
            byte[] zip = CapFixture.of(PACKAGE_AID).applets(APPLET_AID).replace("Applet", truncated).build();

            CAPFile cap = CAPFile.from(zip);
            assertThatThrownBy(cap::appletAids).isInstanceOf(GPException.class);
        }
    }

    @Nested
    class LoadFile {

        @ParameterizedTest(name = "code of {0} bytes -> length field {1}")
        @CsvSource({"100, 64", "200, 81C8", "1000, 8203E8"})
        void gpcs_table_11_58_loadFileDataBlockIsC4WithBerLength(int codeLength, String lengthField) {
            CAPFile cap = CAPFile.from(fixtureWithCodeLength(codeLength).build());

            assertThat(cap.code()).hasSize(codeLength);
            assertThat(Hex.encode(cap.loadFileData())).isEqualTo("C4" + lengthField + Hex.encode(cap.code()));
        }

        @Test
        void gpcs_c_2_loadFileDataBlockHashCoversTheComponentsWithoutTheC4Header() throws Exception {
            CAPFile cap = CAPFile.from(CapFixture.of(PACKAGE_AID).applets(APPLET_AID).build());

            assertThat(cap.loadFileDataBlockHash("SHA-256", true))
                    .isEqualTo(MessageDigest.getInstance("SHA-256").digest(cap.code(true)));
            assertThat(cap.loadFileDataBlockHash("sha-1", false))
                    .isEqualTo(MessageDigest.getInstance("SHA-1").digest(cap.code(false)));
            assertThatThrownBy(() -> cap.loadFileDataBlockHash("MD5x", true)).isInstanceOf(GPException.class);
        }

        @Test
        void loadBlocksSplitTheLoadFile() {
            CAPFile cap = CAPFile.from(fixtureWithCodeLength(600).build());

            List<byte[]> blocks = cap.loadBlocks(247);

            assertThat(blocks).hasSize(3);
            assertThat(blocks.get(0)).hasSize(247);
            ByteArrayOutputStream joined = new ByteArrayOutputStream();
            blocks.forEach(joined::writeBytes);
            assertThat(joined.toByteArray()).isEqualTo(cap.loadFileData());
        }
    }

    @Nested
    class Rejection {

        @Test
        void rejectsNullOrEmptyData() {
            assertThatThrownBy(() -> CAPFile.from(null)).isInstanceOf(GPException.class);
            assertThatThrownBy(() -> CAPFile.from(new byte[0])).isInstanceOf(GPException.class);
        }

        @Test
        void rejectsDataThatIsNotAZip() {
            assertThatThrownBy(() -> CAPFile.from(Hex.decode("0102030405")))
                    .isInstanceOf(GPException.class);
        }

        @Test
        void rejectsCapWithoutHeaderComponent() {
            assertThatThrownBy(() -> CAPFile.from(CapFixture.of(PACKAGE_AID).without("Header").build()))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("Header");
        }

        @Test
        void rejectsJarWithMoreThanOneCapFile() throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (String dir : List.of("a/javacard/", "b/javacard/")) {
                    zip.putNextEntry(new ZipEntry(dir + "Header.cap"));
                    zip.write(CapFixture.of(PACKAGE_AID).components().get("Header"));
                    zip.closeEntry();
                }
            }

            assertThatThrownBy(() -> CAPFile.from(bytes.toByteArray()))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("more than one CAP file");
        }

        @Test
        void rejectsHeaderTooShortForItsAid() {
            byte[] zip = CapFixture.of(PACKAGE_AID).replace("Header", Hex.decode("01000ADECAFFED010200000110A0")).build();

            assertThatThrownBy(() -> CAPFile.from(zip))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("Header");
        }
    }

    /** A fixture whose concatenated components are exactly {@code codeLength} bytes long. */
    private static CapFixture fixtureWithCodeLength(int codeLength) {
        CapFixture fixture = CapFixture.of(PACKAGE_AID).without("Export");
        int current = fixture.components().values().stream().mapToInt(c -> c.length).sum();
        int classInfo = fixture.components().get("Class").length - 3;
        return fixture.fillerSize("Class", classInfo + codeLength - current);
    }

    private static String concat(Map<String, byte[]> components, List<String> names) {
        StringBuilder hex = new StringBuilder();
        names.forEach(name -> hex.append(Hex.encode(components.get(name))));
        return hex.toString();
    }
}
