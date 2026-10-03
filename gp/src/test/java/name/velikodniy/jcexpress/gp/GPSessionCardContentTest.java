package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Card content management commands of {@link GPSession}: DELETE (GPCS v2.3.1 11.2), INSTALL (11.5,
 * Tables 11-40 to 11-47) and LOAD (11.6), and the combined load-and-install flow with the Executable Module
 * AID taken from the CAP file's Applet component (11.5.2.3.2, JCVM 3.1 section 6.6).
 */
class GPSessionCardContentTest {

    private static final String PACKAGE = "A0000000620301";
    private static final String APPLET = "A000000062030101";
    private static final String SECOND_APPLET = "A000000062030102";

    @Nested
    class Delete {

        @Test
        void table_11_20_deleteSendsTheTaggedAidWithLe00() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000");
            card.open().deleteAid(APPLET);

            assertThat(card.plainCommands()).containsExactly("80E400000A4F08" + APPLET + "00");
        }

        @Test
        void table_11_22_deleteRelatedObjectsUsesP2_80() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000");
            card.open().deleteAid(Hex.decode(PACKAGE), true);

            assertThat(card.plainCommands()).containsExactly("80E40080094F07" + PACKAGE + "00");
        }

        @Test
        void failedDeleteReportsTheStatusWord() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("6A88");
            GPSession gp = card.open();

            assertThatThrownBy(() -> gp.deleteAid(APPLET))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining(APPLET)
                    .satisfies(e -> assertThat(((GPException) e).statusWord()).isEqualTo(0x6A88));
        }
    }

    @Nested
    class Install {

        /**
         * Same data field as the INSTALL [for load] GlobalPlatformPro sent to the public real SCP02 card
         * (transcript SCP02_real_card_i15_session1), plus the Le '00' of Table 11-40.
         */
        @Test
        void table_11_42_installForLoadMatchesTheCommandAcceptedByTheRealCard() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000");
            card.open().installForLoad("D27600012401", "A000000003000000");

            assertThat(card.plainCommands()).containsExactly("80E602001306D2760001240108A000000003000000000000" + "00");
        }

        @Test
        void table_11_42_installForLoadWithHashAndLoadParameters() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000");
            byte[] hash = new byte[32];
            Arrays.fill(hash, (byte) 0xAB);
            byte[] params = Hex.decode("EF04C6020400");

            card.open().installForLoad(Hex.decode(PACKAGE), new byte[0], hash, params);

            assertThat(card.plainCommands()).containsExactly("80E602003207" + PACKAGE + "00" + "20" + Hex.encode(hash)
                    + "06EF04C6020400" + "00" + "00");
        }

        /**
         * Same data field as the INSTALL [for install and make selectable] GlobalPlatformPro sent to the public
         * real SCP02 card (pastebin ZQSDaJFm), plus the Le '00' of Table 11-40.
         */
        @Test
        void table_11_43_installForInstallMatchesTheCommandAcceptedByTheRealCard() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000");
            String module = "D2760001240102000000000000010000";

            card.open().installForInstall("D27600012401", module, module, 0x00, null);

            assertThat(card.plainCommands()).containsExactly("80E60C002F06D27600012401" + "10" + module + "10" + module
                    + "0100" + "02C900" + "00" + "00");
        }

        @Test
        void table_11_43_installParametersLongerThan127BytesUseBerLengths() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000");
            byte[] params = new byte[130];

            card.open().installForInstall(PACKAGE, APPLET, APPLET, 0x00, params);

            String data = "07" + PACKAGE + "08" + APPLET + "08" + APPLET + "0100" + "8185" + "C98182"
                    + Hex.encode(params) + "00";
            assertThat(card.plainCommands()).containsExactly("80E60C00" + String.format("%02X", data.length() / 2)
                    + data + "00");
        }

        @Test
        void gpcs_11_1_2_threeBytePrivileges() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000");

            card.open().installForInstall(PACKAGE, APPLET, APPLET, 0x80C000, null);

            assertThat(card.plainCommands().getFirst()).contains("0380C000" + "02C900");
        }

        @Test
        void table_11_45_extradition() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000");
            card.open().extradite(APPLET, "A000000151000000");

            assertThat(card.plainCommands()).containsExactly(
                    "80E6100016" + "08A000000151000000" + "00" + "08" + APPLET + "000000" + "00");
        }

        @Test
        void table_11_46_registryUpdate() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000");
            card.open().registryUpdate(APPLET, 0x04);

            assertThat(card.plainCommands()).containsExactly("80E640000F0000" + "08" + APPLET + "0104" + "0000" + "00");
        }

        @Test
        void table_11_47_personalization() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000");
            card.open().personalize(APPLET);

            assertThat(card.plainCommands()).containsExactly("80E620000E0000" + "08" + APPLET + "000000" + "00");
        }

        @Test
        void invalidAidLengthIsRejectedBeforeSending() {
            ScriptedCard card = ScriptedCard.scp02();
            GPSession gp = card.open();

            assertThatThrownBy(() -> gp.installForInstall(PACKAGE, "A000", APPLET, 0, null))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("5-16 bytes");
            assertThat(card.commandCount()).isZero();
        }
    }

    @Nested
    class Load {

        @Test
        void table_11_56_loadSendsNumberedBlocksAndFlagsTheLastOne() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000").thenAnswer("009000").thenAnswer("009000");
            byte[] loadFile = new byte[600];
            for (int i = 0; i < loadFile.length; i++) {
                loadFile[i] = (byte) (i * 7);
            }

            card.open().load(loadFile);

            assertThat(card.plainCommands()).containsExactly(
                    "80E80000F7" + Hex.encode(Arrays.copyOfRange(loadFile, 0, 247)) + "00",
                    "80E80001F7" + Hex.encode(Arrays.copyOfRange(loadFile, 247, 494)) + "00",
                    "80E880026A" + Hex.encode(Arrays.copyOfRange(loadFile, 494, 600)) + "00");
        }

        @Test
        void e_4_6_withCommandEncryptionBlocksLeaveRoomForThePadding() {
            ScriptedCard card = ScriptedCard.of("SCP02_i15_level03_CENC");

            card.open().load(new byte[600]);

            // 239 + 239 + 122 clear bytes: padded to 240, 240 and 128 bytes, plus the 8-byte C-MAC
            assertThat(card.wrappedCommands()).extracting(c -> c.substring(0, 10))
                    .containsExactly("84E80000F8", "84E80001F8", "84E8800288");
        }

        @Test
        void gpcs_11_6_2_2_loadFilesNeedingMoreThan256BlocksAreRejectedBeforeSending() {
            ScriptedCard card = ScriptedCard.scp02();
            GPSession gp = card.open();

            assertThatThrownBy(() -> gp.load(new byte[256 * 247 + 1]))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("257 blocks");
            assertThat(card.commandCount()).isZero();
        }

        @Test
        void failedLoadBlockStopsTheLoad() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000").thenAnswer("6A84");
            GPSession gp = card.open();

            assertThatThrownBy(() -> gp.load(new byte[600]))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("block 1");
            assertThat(card.commandCount()).isEqualTo(2);
        }
    }

    @Nested
    class LoadAndInstall {

        @Test
        void gpcs_11_5_2_3_2_executableModuleIsTheAppletAidOfTheCapFile() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000").thenAnswer("009000").thenAnswer("009000");
            CAPFile cap = CAPFile.from(CapFixture.of(PACKAGE).applets(APPLET).build());

            card.open().loadAndInstall(cap, null, 0x00, null);

            List<String> commands = card.plainCommands();
            assertThat(commands).hasSize(3);
            assertThat(commands.get(0)).isEqualTo("80E602000C07" + PACKAGE + "00000000" + "00");
            assertThat(commands.get(1)).isEqualTo("80E88000" + String.format("%02X", cap.loadFileData().length)
                    + Hex.encode(cap.loadFileData()) + "00");
            assertThat(commands.get(2)).isEqualTo("80E60C0020" + "07" + PACKAGE + "08" + APPLET + "08" + APPLET
                    + "0100" + "02C900" + "00" + "00");
        }

        @Test
        void explicitInstanceAidIsUsedAsApplicationAid() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000").thenAnswer("009000").thenAnswer("009000");
            CAPFile cap = CAPFile.from(CapFixture.of(PACKAGE).applets(APPLET).build());

            card.open().loadAndInstall(cap, "A00000006203010199", 0x00, null);

            assertThat(card.plainCommands().getLast())
                    .contains("08" + APPLET + "09" + "A00000006203010199" + "0100");
        }

        @Test
        void capWithSeveralAppletsNeedsAnExplicitModuleAndNothingIsSentOtherwise() {
            ScriptedCard card = ScriptedCard.scp02().thenAnswer("009000").thenAnswer("009000").thenAnswer("009000");
            CAPFile cap = CAPFile.from(CapFixture.of(PACKAGE).applets(APPLET, SECOND_APPLET).build());
            GPSession gp = card.open();

            assertThatThrownBy(() -> gp.loadAndInstall(cap, null, 0x00, null))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("2 applets");
            assertThat(card.commandCount()).isZero();

            gp.loadAndInstall(cap, SECOND_APPLET, null, 0x00, null);
            assertThat(card.plainCommands().getLast()).contains("08" + SECOND_APPLET + "08" + SECOND_APPLET);
        }

        @Test
        void moduleThatIsNotAnAppletOfTheCapFileIsRejectedBeforeSending() {
            ScriptedCard card = ScriptedCard.scp02();
            CAPFile cap = CAPFile.from(CapFixture.of(PACKAGE).applets(APPLET).build());
            GPSession gp = card.open();

            assertThatThrownBy(() -> gp.loadAndInstall(cap, PACKAGE, null, 0x00, null))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("defines no applet " + PACKAGE);
            assertThat(card.commandCount()).isZero();
        }

        @Test
        void tooLargeCapFileIsRejectedBeforeInstallForLoad() {
            ScriptedCard card = ScriptedCard.scp02();
            CAPFile cap = CAPFile.from(CapFixture.of(PACKAGE).applets(APPLET).fillerSize("Method", 64_000).build());
            GPSession gp = card.open();

            assertThatThrownBy(() -> gp.loadAndInstall(cap, null, 0x00, null))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("blocks");
            assertThat(card.commandCount()).isZero();
        }
    }
}
