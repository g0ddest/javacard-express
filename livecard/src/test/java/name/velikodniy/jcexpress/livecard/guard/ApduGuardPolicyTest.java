package name.velikodniy.jcexpress.livecard.guard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static name.velikodniy.jcexpress.livecard.guard.GuardHarness.HEX;
import static name.velikodniy.jcexpress.livecard.guard.GuardHarness.TEST_KEY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard's allow-list outside the tests' own applets, the test-applet context, logical channels and the
 * fallbacks to the strict context.
 */
class ApduGuardPolicyTest {

    /** INSTALL [for load] of the test package F04A43580102 as the real card received it (C-MAC level). */
    private static final String INSTALL_FOR_LOAD = "84E602001306F04A43580102000000005234F195258312F900";
    /** INSTALL [for install and make selectable] with 'C9' parameters AABBCC, from the real run. */
    private static final String INSTALL_FOR_INSTALL = "84E60C002806F04A4358010207F04A435801020107F04A4358010201010005"
            + "C903AABBCC00092EA61B244F13A500";
    private static final String LOAD_LAST_BLOCK = "84E880022440011006B4B44404B43101200344100568006003B4300241066800A"
            + "1394DAE4AF855977300";
    private static final String DELETE_PACKAGE = "84E40080104F06F04A435801023EF220657F8175C000";

    private final GuardHarness harness = new GuardHarness();

    @Nested
    class StrictContext {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "SELECT ISD, 00A4040008A00000015100000000",
            "SELECT default application, 00A4040000",
            "GET DATA CPLC, 80CA9F7F00",
            "GET DATA under C-MAC, 84CA9F7F0847B4FDA6AC0618A700",
            "GET STATUS, 80F24002024F0000",
            "GET RESPONSE, 00C0000000",
            "MANAGE CHANNEL open, 0070000001",
            "INITIALIZE UPDATE, 80500000087FCE857F680B873000",
        })
        void readOnlyCommandsPass(String name, String command) {
            harness.selectIsd();

            assertThat(harness.allows(command)).as(name).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "PUT KEY, 80D8018110" + "00000000000000000000000000000000",
            "STORE DATA, 80E2800003010203",
            "SET STATUS card locked, 80F0807F",
            "SET STATUS card terminated, 80F080FF",
            "SET STATUS of a Security Domain, 80F0600F08A000000151000000",
            "READ BINARY, 00B0000000",
            "extended GET DATA, 80CA9F7F000000",
            "malformed Lc, 80F240020A4F00",
        })
        void otherCommandsAreBlockedEvenWithWriteAccess(String name, String command) {
            harness.selectIsd();
            harness.guard.writeAccess(true);

            assertThat(harness.allows(command)).as(name).isFalse();
            assertThat(harness.notes).anyMatch(note -> note.startsWith("GUARD BLOCKED (not sent)"));
        }

        /** In a proprietary class these instructions reach the ISD as unknown commands: blocked. */
        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "SELECT in a proprietary class, 80A4040008A00000015100000000",
            "MANAGE CHANNEL in a proprietary class, 8070000001",
            "GET RESPONSE in a proprietary class, 80C0000000",
        })
        void proprietaryClassSelectManageChannelAndGetResponseAreBlocked(String name, String command) {
            harness.selectIsd();
            harness.guard.writeAccess(true);

            assertThat(harness.allows(command)).as(name).isFalse();
            assertThat(harness.notes).anyMatch(note -> note.startsWith("GUARD BLOCKED (not sent)")
                    && note.contains("pass only in the plain inter-industry classes 00-03 and 40-4F"));
        }

        /** Inter-industry class bytes with logical channel bits (ISO/IEC 7816-4:2005 Tables 2 and 3) pass. */
        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "SELECT on channel 1, 01A4040008A00000015100000000",
            "GET RESPONSE on channel 1, 01C0000000",
            "MANAGE CHANNEL close of channel 1, 0170800000",
            "SELECT on channel 5, 41A4040008A00000015100000000",
            "GET RESPONSE on channel 5, 41C0000000",
            "MANAGE CHANNEL close of channel 5, 4170800000",
        })
        void interindustryClassesWithChannelBitsPass(String name, String command) {
            harness.selectIsd();

            assertThat(harness.allows(command)).as(name).isTrue();
        }

        @Test
        void contentManagementNeedsWriteAccess() {
            harness.selectIsd();

            assertThat(harness.allows(INSTALL_FOR_LOAD)).isFalse();
            assertThat(harness.allows(DELETE_PACKAGE)).isFalse();
            assertThat(harness.notes).anyMatch(note -> note.contains("outside a write-access scope"));
        }
    }

    /** Content management at the explicitly selected Issuer Security Domain, inside a write-access scope. */
    @Nested
    class WriteAccess {

        @BeforeEach
        void selectTheIssuerSecurityDomain() {
            harness.selectIsd();
        }

        @Test
        void loadAndInstallOfTheTestPackagePass() {
            harness.guard.writeAccess(true);
            harness.exchange(INSTALL_FOR_LOAD, "009000");
            harness.exchange(LOAD_LAST_BLOCK, "009000");
            harness.exchange(INSTALL_FOR_INSTALL, "009000");
            harness.exchange(DELETE_PACKAGE, "009000");

            assertThat(harness.changes).containsExactly(
                    new ContentChange.LoadFileCreated("F04A43580102"),
                    new ContentChange.InstanceCreated("F04A4358010201", "F04A43580102", "F04A4358010201"),
                    new ContentChange.Deleted("F04A43580102", true));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "INSTALL [for install and make selectable] without secure messaging, 80E60C001D06F04A4358010207F04A43580102"
                + "0107F04A4358010201010002C9000000",
            "INSTALL [for make selectable], 80E608000E000007F04A43580102010100000000",
            "DELETE of an application, 80E40000094F07F04A4358010201",
            "SET STATUS lock, 80F0408007F04A4358010201",
            "SET STATUS unlock, 80F0400007F04A4358010201",
        })
        void allowedChangesUnderThePrefix(String name, String command) {
            harness.guard.writeAccess(true);

            assertThat(harness.allows(command)).as(name).isTrue();
        }

        @Test
        void loadNeedsAnAcceptedInstallForLoad() {
            harness.guard.writeAccess(true);
            assertThat(harness.allows(LOAD_LAST_BLOCK)).isFalse();

            harness.exchange(INSTALL_FOR_LOAD, "6A80");
            assertThat(harness.allows(LOAD_LAST_BLOCK)).isFalse();

            harness.exchange(INSTALL_FOR_LOAD, "009000");
            harness.exchange(LOAD_LAST_BLOCK, "009000");
            assertThat(harness.allows(LOAD_LAST_BLOCK)).as("after the last block").isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "INSTALL [for load] of a Java Card API package, 80E602000C07A0000000620101000000000000",
            "INSTALL [for load] into another Security Domain, 80E602001306F04A4358010208A0000001510000AA00000000",
            "INSTALL [for load] with a load token, 80E602000C06F04A43580102000000010000",
            "INSTALL with privileges 04, 80E60C001D06F04A4358010207F04A435801020107F04A4358010201010402C9000000",
            "INSTALL with 3-byte privileges, 80E60C001F06F04A4358010207F04A435801020107F04A435801020103000080"
                + "02C9000000",
            "INSTALL with an install token, 80E60C001E06F04A4358010207F04A435801020107F04A4358010201010002C90001AA00",
            "INSTALL of an application outside the prefix, 80E60C001D06F04A4358010207F04A435801020107A00000000301"
                + "01010002C9000000",
            "INSTALL [for extradition], 80E610001508A0000001510000000007F04A435801020100000000",
            "INSTALL [for registry update], 80E640000E000007F04A43580102010104000000",
            "INSTALL [for personalization], 80E620000D000007F04A435801020100000000",
            "DELETE of the ISD, 80E400000A4F08A000000151000000",
            "DELETE of a Java Card API package, 80E40000094F07A0000000620101",
            "DELETE with a delete token, 80E400000E4F06F04A435801029E0400000000",
            "DELETE with P1 more commands, 80E48000084F06F04A43580102",
            "SET STATUS personalized, 80F0400F07F04A4358010101",
            "SET STATUS of another application, 80F0408007A0000000030000",
        })
        void forbiddenChangesAreBlocked(String name, String command) {
            harness.guard.writeAccess(true);

            assertThat(harness.allows(command)).as(name).isFalse();
        }

        @Test
        void writesUnderCencAreBlockedBecauseTheGuardCannotReadThem() {
            byte[] hostChallenge = HEX.parseHex("A1A2A3A4A5A6A7A8");
            byte[] response = harness.initializeUpdate("80", hostChallenge, TEST_KEY, 0x00);
            harness.exchange(GuardHarness.externalAuthenticateByGp(hostChallenge, response, 0x03), "9000");
            harness.guard.writeAccess(true);

            assertThat(harness.allows("84F24002124F00" + "00".repeat(16) + "00")).as("GET STATUS").isTrue();
            assertThat(harness.allows(DELETE_PACKAGE)).isFalse();
            assertThat(harness.notes).anyMatch(note -> note.contains("under C-ENC"));
        }
    }

    @Nested
    class TestAppletContext {

        @Test
        void commandsToASelectedTestAppletPass() {
            harness.exchange("00A4040007F04A435801010100", "9000");

            assertThat(harness.allows("80D8018110" + "00".repeat(16))).isTrue();
            assertThat(harness.allows("8001000000")).isTrue();
        }

        @Test
        void selectionWithMoreDataAvailableCounts() {
            harness.exchange("00A4040007F04A435801010100", "6110");

            assertThat(harness.allows("8001000000")).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "failed SELECT, 00A4040007F04A435801010100, 6A82",
            "partial AID shorter than the prefix, 00A4040002F04A00, 9000",
            "AID outside the prefix, 00A4040007A000000003101000, 9000",
            "ISD, 00A4040008A00000015100000000, 9000",
        })
        void otherSelectionsFallBackToTheStrictContext(String name, String select, String sw) {
            harness.exchange("00A4040007F04A435801010100", "9000");
            harness.exchange(select, sw);

            assertThat(harness.allows("8001000000")).as(name).isFalse();
        }

        /**
         * A proprietary-class SELECT goes to the applet; the applet rejecting it leaves the guard's picture as it
         * was (answered with success, the guard becomes unsure: ApduGuardContextTest).
         */
        @Test
        void proprietaryClassSelectRejectedByTheAppletKeepsTheContext() {
            harness.exchange("00A4040007F04A435801010100", "9000");
            harness.exchange("80A4040008A00000015100000000", "6D00");

            assertThat(harness.allows("80D8018110" + "00".repeat(16))).isTrue();
        }

        /** A proprietary-class MANAGE CHANNEL that the applet rejects neither closes nor opens a channel. */
        @Test
        void proprietaryClassManageChannelRejectedByTheAppletKeepsTheChannels() {
            harness.exchange("0070000001", "019000");
            harness.exchange("01A4040007F04A435801010100", "9000");
            harness.exchange("00A4040007F04A435801010100", "9000");
            harness.exchange("8070800100", "6D00");
            harness.exchange("8070000001", "6D00");

            assertThat(harness.allows("8101000000")).as("channel 1 still holds the test applet").isTrue();
        }

        @Test
        void cardResetAndTransportFailureEndTheTestAppletContext() {
            harness.exchange("00A4040007F04A435801010100", "9000");
            harness.guard.cardReset();
            assertThat(harness.allows("8001000000")).isFalse();

            harness.exchange("00A4040007F04A435801010100", "9000");
            harness.guard.transportFailed();
            assertThat(harness.allows("8001000000")).isFalse();
        }

        @Test
        void eachLogicalChannelHasItsOwnContext() {
            harness.exchange("0070000001", "019000");
            harness.exchange("01A4040007F04A435801010100", "9000");

            assertThat(harness.allows("81D8018110" + "00".repeat(16))).as("channel 1: test applet").isTrue();
            assertThat(harness.allows("80D8018110" + "00".repeat(16))).as("channel 0: ISD").isFalse();

            harness.exchange("0070800100", "9000");
            harness.exchange("0070000001", "019000");
            assertThat(harness.allows("8101000000")).as("reopened channel 1").isFalse();
        }

        @Test
        void furtherInterindustryChannelsAreTrackedToo() {
            harness.exchange("0070000000", "049000");
            harness.exchange("40A4040007F04A435801010100", "9000");

            assertThat(harness.allows("C001000000")).as("channel 4").isTrue();
            assertThat(harness.allows("8001000000")).as("channel 0").isFalse();
        }
    }
}
