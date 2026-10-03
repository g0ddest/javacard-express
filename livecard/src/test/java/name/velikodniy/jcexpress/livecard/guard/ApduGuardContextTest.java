package name.velikodniy.jcexpress.livecard.guard;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static name.velikodniy.jcexpress.livecard.guard.GuardHarness.HEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the guard believes is selected must never be looser than what the card did. Only the exact SELECT form
 * the harness sends enters a context; the Issuer Security Domain context needs an explicit SELECT of the ISD;
 * authentication commands are checked even inside a test applet; class 'FF' never reaches the reader; a verified
 * handshake ends at the next unrelated command.
 */
class ApduGuardContextTest {

    private static final String PUT_KEY = "80D8018110" + "00".repeat(16);
    private static final String SELECT_TEST_APPLET = "00A4040007F04A435801010100";
    private static final GuardSelfCheck.Handshake RIGHT_KEYS = GuardSelfCheck.REFERENCE_HANDSHAKES.get(0);
    private static final GuardSelfCheck.Handshake OTHER_KEYS = GuardSelfCheck.REFERENCE_HANDSHAKES.get(1);

    private final GuardHarness harness = new GuardHarness();

    @Nested
    class Selection {

        /**
         * A SELECT that is not the harness's form (CLA 00-03 or 40-4F without secure messaging or chaining, P1 04,
         * P2 00, a 5-16 byte AID) may not have been an applet selection on the card (JCRE: other forms reach the
         * selected applet, here the ISD): whatever it answers, the guard falls back to the strict rules.
         */
        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "command chaining bit (CLA 10), 10A4040007F04A435801010100",
            "RFU class 001x xxxx (CLA 20), 20A4040007F04A435801010100",
            "further class with chaining bit (CLA 50) on channel 4, 50A4040007F04A435801010100",
            "secure messaging indication (CLA 04), 04A4040007F04A435801010100",
            "P2 0C (no response data), 00A4040C07F04A4358010101",
            "P2 02 (next occurrence), 00A4040207F04A435801010100",
            "extended length, 00A40400000007F04A4358010101",
        })
        void onlyTheStandardSelectByNameEntersTheTestAppletContext(String name, String select) {
            harness.exchange("0070000000", "049000");
            harness.selectIsd();
            harness.guard.observe(HEX.parseHex(select), HEX.parseHex("9000"));

            String putKey = select.startsWith("50") ? "C0" + PUT_KEY.substring(2) : PUT_KEY;
            assertThat(harness.allows(putKey)).as(name + ": PUT KEY afterwards").isFalse();
            assertThat(harness.allows(select.startsWith("50") ? "C001000000" : "8001000000")).as(name).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "command chaining bit (CLA 10), 10A4040007F04A435801010100",
            "RFU class (CLA 20), 20A4040007F04A435801010100",
            "secure messaging indication (CLA 0C), 0CA4040007F04A435801010100",
            "extended length, 00A40400000007F04A4358010101",
            "extended MANAGE CHANNEL, 00700000000001",
        })
        void nonStandardSelectionCommandsAreNotSentOutsideTestApplets(String name, String command) {
            harness.selectIsd();

            assertThat(harness.allows(command)).as(name).isFalse();
        }

        /** A proprietary-class SELECT answered as a success: the guard cannot tell what the card selected. */
        @Test
        void proprietaryClassSelectAnsweredWithSuccessEndsTheTestAppletContext() {
            harness.exchange(SELECT_TEST_APPLET, "9000");
            harness.exchange("80A4040008A00000015100000000", "9000");

            assertThat(harness.allows(PUT_KEY)).isFalse();
        }

        @Test
        void proprietaryClassManageChannelAnsweredWithSuccessMakesEveryChannelUnknown() {
            harness.exchange("0070000001", "019000");
            harness.exchange("01A4040007F04A435801010100", "9000");
            harness.exchange(SELECT_TEST_APPLET, "9000");
            harness.exchange("8070800100", "9000");

            assertThat(harness.allows("8101000000")).as("channel 1").isFalse();
            assertThat(harness.allows("8001000000")).as("channel 0").isFalse();
        }

        @Test
        void standardSelectionOnLogicalChannelsStillCounts() {
            harness.exchange("0070000001", "019000");
            harness.exchange("01A4040007F04A435801010100", "6110");

            assertThat(harness.allows("81D8018110" + "00".repeat(16))).isTrue();
        }
    }

    @Nested
    class IssuerSecurityDomain {

        /**
         * With a foreign application selected (here the PIV AID), content management and authentication never
         * reach it: an ISO DELETE FILE ('00 E4 00 00': the current file, ISO/IEC 7816-9) or INITIALIZE UPDATE
         * means something else there.
         */
        @Test
        void contentManagementAndAuthenticationGoOnlyToAnExplicitlySelectedIsd() {
            harness.exchange("00A404000BA00000030800001000010000", "9000");
            harness.guard.writeAccess(true);

            assertThat(harness.allows("00E40000084F06F04A43580102")).as("DELETE").isFalse();
            assertThat(harness.allows("80E40000084F06F04A43580102")).as("GP DELETE").isFalse();
            assertThat(harness.allows("8050000008A1A2A3A4A5A6A7A800")).as("INITIALIZE UPDATE").isFalse();
            assertThat(harness.allows("80F24002024F0000")).as("GET STATUS").isFalse();
            assertThat(harness.allows("80CA9F7F00")).as("GET DATA stays readable").isTrue();
            assertThat(harness.budget.failures()).as("nothing was sent, nothing counts").isZero();
        }

        /** After a card reset the default application is selected: GET DATA only, until SELECT of the ISD. */
        @Test
        void afterAResetOnlyGetDataPassesUntilTheIsdIsSelected() {
            harness.guard.cardReset();

            assertThat(harness.allows("80CA9F7F00")).isTrue();
            assertThat(harness.allows(RIGHT_KEYS.initializeUpdate())).isFalse();
            harness.selectIsd();
            assertThat(harness.allows(RIGHT_KEYS.initializeUpdate())).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource({
            "partial ISD AID, 00A4040005A00000015100",
            "ISD AID with a longer suffix, 00A4040009A000000151000000AA00",
            "default application (no AID), 00A4040000",
        })
        void onlyTheExactIsdAidSelectsTheIsdContext(String name, String select) {
            harness.exchange(select, "9000");

            assertThat(harness.allows(RIGHT_KEYS.initializeUpdate())).as(name).isFalse();
        }

        @Test
        void externalAuthenticateOutsideTheIsdIsBlockedAndCounted() {
            harness.exchange("00A404000BA00000030800001000010000", "9000");

            assertThat(harness.allows(RIGHT_KEYS.externalAuthenticate())).isFalse();
            assertThat(harness.budget.failures()).isEqualTo(1);
        }
    }

    @Nested
    class ClassFf {

        /**
         * Class 'FF' is invalid (ISO/IEC 7816-4:2005 5.1.1), and PC/SC reader drivers take it as a command for
         * the reader itself (PC/SC part 3 pseudo-APDUs): never sent, not even inside a test applet.
         */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"FFCA000000", "FFF2000000", "FF50000008A1A2A3A4A5A6A7A800", "FFA4040007F04A4358010101"})
        void classFfIsNeverSent(String command) {
            harness.selectIsd();
            assertThat(harness.allows(command)).as("ISD").isFalse();

            harness.exchange(SELECT_TEST_APPLET, "9000");
            assertThat(harness.allows(command)).as("test applet").isFalse();
        }
    }

    /**
     * An applet that uses GlobalPlatform's SecureChannel API forwards INITIALIZE UPDATE and EXTERNAL AUTHENTICATE
     * to its Security Domain (the ISD here), whose key set counts failed authentications: in a test applet the
     * proprietary-class INS '50' and '82' follow the same rules as at the ISD.
     */
    @Nested
    class AuthenticationInATestApplet {

        @Test
        void unverifiedExternalAuthenticateIsBlockedAndAbortsTheRun() {
            harness.exchange(SELECT_TEST_APPLET, "9000");
            harness.exchange(OTHER_KEYS.initializeUpdate(), OTHER_KEYS.response());

            assertThat(harness.allows(OTHER_KEYS.externalAuthenticate())).isFalse();
            assertThat(harness.budget.exhausted()).isTrue();
            assertThat(harness.notes).anyMatch(note -> note.contains("card cryptogram NOT verified"));
        }

        @Test
        void externalAuthenticateWithoutInitializeUpdateIsBlockedAndCounted() {
            harness.exchange(SELECT_TEST_APPLET, "9000");

            assertThat(harness.allows(RIGHT_KEYS.externalAuthenticate())).isFalse();
            assertThat(harness.budget.failures()).isEqualTo(1);
        }

        @Test
        void verifiedHandshakeThroughTheAppletPassesOnce() {
            GuardHarness lenient = new GuardHarness(3);
            lenient.exchange(SELECT_TEST_APPLET, "9000");
            lenient.exchange(RIGHT_KEYS.initializeUpdate(), RIGHT_KEYS.response());

            assertThat(lenient.guard.verifiedHandshakes()).isEqualTo(1);
            assertThat(lenient.allows(RIGHT_KEYS.externalAuthenticate())).isTrue();
            assertThat(lenient.allows(RIGHT_KEYS.externalAuthenticate())).as("second time").isFalse();
        }

        @Test
        void initializeUpdateTheGuardCannotVerifyIsNotSent() {
            harness.exchange(SELECT_TEST_APPLET, "9000");

            assertThat(harness.allows("8050000010" + "00".repeat(16) + "00")).as("S16 challenge").isFalse();
            assertThat(harness.allows("8450000010A1A2A3A4A5A6A7A80102030405060708")).as("with secure messaging")
                    .isFalse();
        }

        /**
         * An applet's own command that uses INS '50' or '82' in a proprietary class cannot be told apart from an
         * authentication it forwards (Amendment D 7.1.1, 7.1.2 code INITIALIZE UPDATE and EXTERNAL AUTHENTICATE so):
         * the reason says so and what to change.
         */
        @Test
        void aBlockedCommandOfATestAppletExplainsTheGlobalPlatformCoding_amendmentD_7_1() {
            harness.exchange(SELECT_TEST_APPLET, "9000");

            assertThatThrownBy(() -> harness.guard.check(HEX.parseHex("8050000002")))
                    .isInstanceOf(GuardViolationException.class)
                    .hasMessageContaining("INS 50 in a proprietary class is GlobalPlatform's INITIALIZE UPDATE")
                    .hasMessageContaining("forwards it to its Security Domain")
                    .hasMessageContaining("If this is the applet's own command, give it another INS");
            assertThatThrownBy(() -> harness.guard.check(HEX.parseHex("8082000000")))
                    .hasMessageContaining("INS 82 in a proprietary class is GlobalPlatform's EXTERNAL AUTHENTICATE")
                    .hasMessageContaining("counts as a failed authentication of the run");
        }

        /** ISO/IEC 7816-4 EXTERNAL / MUTUAL AUTHENTICATE in an inter-industry class is the applet's own command. */
        @Test
        void interindustryAuthenticationIsTheAppletsOwnCommand() {
            harness.exchange(SELECT_TEST_APPLET, "9000");

            assertThat(harness.allows("0082000028" + "11".repeat(40) + "28")).as("BAC MUTUAL AUTHENTICATE").isTrue();
            assertThat(harness.allows("8001000000")).isTrue();
            assertThat(harness.budget.failures()).isZero();
        }
    }

    /** INITIALIZE UPDATE and its EXTERNAL AUTHENTICATE belong together (Amendment D 7.1.1, 7.1.2). */
    @Nested
    class HandshakeSequence {

        @Test
        void anotherCommandBetweenInitializeUpdateAndExternalAuthenticateEndsTheHandshake() {
            harness.selectIsd();
            harness.exchange(RIGHT_KEYS.initializeUpdate(), RIGHT_KEYS.response());
            harness.exchange("80CA9F7F00", "9F7F2A" + "00".repeat(42) + "9000");

            assertThat(harness.allows(RIGHT_KEYS.externalAuthenticate())).isFalse();
        }

        @Test
        void getResponseInBetweenKeepsTheHandshake() {
            harness.selectIsd();
            harness.exchange(RIGHT_KEYS.initializeUpdate(), RIGHT_KEYS.response());
            harness.exchange("00C0000000", "6A82");

            assertThat(harness.allows(RIGHT_KEYS.externalAuthenticate())).isTrue();
        }

        @Test
        void aBlockedCommandInBetweenWasNotSentAndKeepsTheHandshake() {
            harness.selectIsd();
            harness.exchange(RIGHT_KEYS.initializeUpdate(), RIGHT_KEYS.response());

            assertThat(harness.allows(PUT_KEY)).isFalse();
            assertThat(harness.allows(RIGHT_KEYS.externalAuthenticate())).isTrue();
        }
    }

    @Test
    void extendedGetResponsePassesOnlyInATestApplet() {
        harness.selectIsd();
        assertThat(harness.allows("00C00000000100")).isFalse();

        harness.exchange(SELECT_TEST_APPLET, "9000");
        assertThat(harness.allows("00C00000000100")).isTrue();
    }
}
