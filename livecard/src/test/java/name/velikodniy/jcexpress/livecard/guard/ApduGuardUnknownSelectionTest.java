package name.velikodniy.jcexpress.livecard.guard;

import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.livecard.guard.GuardHarness.HEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * When the guard no longer knows what is selected, it keeps blocking as before (a SELECT or MANAGE CHANNEL in
 * another class answered with success may have changed the selection, ISO/IEC 7816-4:2005 7.1.1, 7.1.2), and every
 * block caused by that names the command or event after which the selection became unknown and what to do.
 */
class ApduGuardUnknownSelectionTest {

    private static final String SELECT_TEST_APPLET = "00A4040007F04A435801010100";

    private final GuardHarness harness = new GuardHarness();

    @Test
    void aBlockAfterAProprietaryManageChannelNamesThatCommand_iso7816_4_7_1_2() {
        harness.exchange(SELECT_TEST_APPLET, "9000");
        harness.exchange("8070040001", "709000");

        assertThatThrownBy(() -> harness.guard.check(HEX.parseHex("8010000001")))
                .isInstanceOf(GuardViolationException.class)
                .hasMessageContaining("INS 10 is not on the allow-list outside the tests' own applets")
                .hasMessageContaining("the channels became unknown after 80 70 04 00 (answered 9000), which in a"
                        + " proprietary class the guard has to treat as MANAGE CHANNEL")
                .hasMessageContaining("if it is the applet's own command, give it another INS")
                .hasMessageContaining("select the applet again");
    }

    @Test
    void aBlockAfterAProprietarySelectByNameNamesThatCommand_iso7816_4_7_1_1() {
        harness.exchange(SELECT_TEST_APPLET, "9000");
        harness.exchange("80A4040001", "A49000");

        assertThatThrownBy(() -> harness.guard.check(HEX.parseHex("8010000001")))
                .isInstanceOf(GuardViolationException.class)
                .hasMessageContaining("channel 0 became unknown after 80 A4 04 00 (answered 9000), which in a"
                        + " proprietary class the guard has to treat as a SELECT by DF name")
                .hasMessageContaining("give it another INS");
    }

    /** The rule stays: the next command is still blocked, and a new SELECT of the test applet ends the state. */
    @Test
    void theRuleIsUnchangedAndASelectOfTheAppletEndsIt() {
        harness.exchange(SELECT_TEST_APPLET, "9000");
        harness.exchange("8070040001", "709000");

        assertThat(harness.allows("8010000001")).isFalse();
        harness.exchange(SELECT_TEST_APPLET, "9000");
        assertThat(harness.allows("8010000001")).isTrue();
        harness.exchange("00A4040008A00000015100000000", "6A82");
        assertThatThrownBy(() -> harness.guard.check(HEX.parseHex("8010000001")))
                .hasMessageNotContaining("became unknown after 80 70");
    }

    @Test
    void aBlockAfterACardResetSaysSo_iso7816_4_5_1_1_2() {
        harness.exchange(SELECT_TEST_APPLET, "9000");
        harness.guard.cardReset();

        assertThatThrownBy(() -> harness.guard.check(HEX.parseHex("8010000001")))
                .hasMessageContaining("since the card reset")
                .hasMessageContaining("select the applet again");
    }

    /** A command the ISD context blocks anyway is not blamed on an unknown selection. */
    @Test
    void aBlockInTheIsdContextNamesNoUnknownSelection() {
        harness.exchange(SELECT_TEST_APPLET, "9000");
        harness.exchange("8070040001", "709000");
        harness.selectIsd();

        assertThatThrownBy(() -> harness.guard.check(HEX.parseHex("80D8018110" + "00".repeat(16))))
                .hasMessageNotContaining("became unknown");
    }
}
