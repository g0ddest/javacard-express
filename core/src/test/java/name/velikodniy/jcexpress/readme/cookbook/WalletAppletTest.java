package name.velikodniy.jcexpress.readme.cookbook;

import javacard.framework.ISO7816;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDULogEntry;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.EnabledOnBackend;
import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.Mode;
import name.velikodniy.jcexpress.SW;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUCommand;
import name.velikodniy.jcexpress.pin.PinSession;
import name.velikodniy.jcexpress.tlv.TLV;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;   // also AssertJ's assertThat

/**
 * The recipes of TESTING.md on the {@link WalletApplet}, with the cookbook's names and lines ("The first test" is
 * {@code first.WalletAppletTest}). {@code TestingCookbookTest} checks that every Java block of TESTING.md is a
 * contiguous run of one source file of this package, and Surefire runs these classes, so a recipe that stops
 * compiling or passing fails the build.
 */
@JavaCardTest
@InstallApplet(WalletApplet.class)              // a fresh instance before every test, deleted after it
class WalletAppletTest {

    static final APDUCommand GET_BALANCE = APDUCommand.of(0x80, 0x52).le(2);
    static final APDUCommand CREDIT = APDUCommand.of(0x80, 0x30);       // the amount is the command data
    static final APDUCommand DEBIT = APDUCommand.of(0x80, 0x40);
    static final APDUCommand GET_INFO = APDUCommand.of(0x80, 0x54).le(256);

    /** Recipe "Commands as constants". */
    @Test
    void credits(SmartCardSession card) {
        assertThat(card.send(CREDIT.data(0x00, 0x64))).isSuccess();   // a new command; CREDIT stays as it is
        assertThat(card.send(GET_BALANCE)).isSuccess().u16(0).isEqualTo(100);
    }

    /** Recipe "Commands as constants": a command copied from a trace, the applet's own byte constants. */
    @Test
    void commandsAsWrittenElsewhere(SmartCardSession card) {
        assertThat(card.sendHex("80 30 00 00 02 0064")).isSuccess();   // CREDIT 100, as written in a trace
        assertThat(card.send(WalletApplet.CLA_WALLET, WalletApplet.INS_GET_BALANCE, 0, 0, null, 2))
                .hasDataHex("0064");
    }

    /** Recipe "Status words". */
    @Test
    void statusWords(SmartCardSession card) {
        assertThat(card.send(GET_BALANCE)).isSuccess();                                   // 9000
        assertThat(card.send(DEBIT.data(0x00, 0x0A))).hasStatusWord(SW.SECURITY_STATUS_NOT_SATISFIED);
        assertThat(card.send(0x80, 0x7F)).hasStatusWord(ISO7816.SW_INS_NOT_SUPPORTED);   // the applet's constants
        assertThat(card.send(0x80, 0x7F)).isNotSuccess().hasSw1(0x6D);
        card.send(CREDIT.data(0x00, 0x64)).requireSuccess();         // an AssertionError unless the SW is 9000
        card.send(DEBIT.data(0x00, 0x0A)).requireSw(SW.NO_ERROR, SW.SECURITY_STATUS_NOT_SATISFIED);
    }

    /** Recipe "Response data". */
    @Test
    void responseData(SmartCardSession card) {
        card.send(CREDIT.data(0x00, 0x64)).requireSuccess();
        APDUResponse info = card.send(GET_INFO);                   // E1 07 81 02 <balance> 82 01 <tries>
        int balance = info.u16(4);                                 // two bytes, big-endian, unsigned: 100
        int tries = info.u8(8);                                    // one byte, unsigned: 3
        byte[] balanceTlv = info.data(2, 6);                       // bytes 2 to 5: 81 02 00 64
        assertThat(card.send(GET_BALANCE)).isSuccess().u16(0).isEqualTo(100);
        assertThat(card.send(GET_BALANCE)).hasDataHex("0064");
        assertThat(card.send(GET_BALANCE)).data().hasSize(2);

        assertThat(balance).isEqualTo(100);
        assertThat(tries).isEqualTo(3);
        assertThat(balanceTlv).containsExactly(0x81, 0x02, 0x00, 0x64);
    }

    /** Recipe "The SELECT response". */
    @Test
    void answersTheSelectWithItsAid(SmartCardSession card) {
        AID wallet = card.aid(WalletApplet.class);
        APDUResponse fci = card.send(APDUCommand.select(wallet));        // 00 A4 04 00 Lc AID 00
        assertThat(fci).isSuccess().tlv().tag(0x6F).tag(0x84).hasValue(wallet.toHex());
    }

    /** Recipe "Data-driven tests". */
    @ParameterizedTest
    @CsvSource({
            "8052000002,     0000 9000",        // GET BALANCE of a new wallet
            "8040000002000A, 6982",             // DEBIT before the PIN is verified
            "807F0000,       6D00"})            // an instruction the applet does not know
    void answers(APDUCommand command, APDUResponse expected, SmartCardSession card) {
        assertThat(card.send(command)).isEqualTo(expected);
    }

    /** Recipe "State across reset and deselect": a reset. */
    @Test
    void theBalanceSurvivesAResetThePinValidationDoesNot(SmartCardSession card) {
        PinSession pin = card.pin().cla(0x80).padTo(8, 0xFF);
        card.send(CREDIT.data(0x00, 0x64)).requireSuccess();
        pin.verify(1, "1234").requireSuccess();

        card.reset();                                  // like pulling the card out: fields stay, nothing is selected
        card.select(WalletApplet.class);
        assertThat(card.send(GET_BALANCE)).u16(0).isEqualTo(100);
        assertThat(pin.isVerified(1)).isFalse();       // the PIN's validation flag is cleared by a reset
    }

    /** Recipe "State across reset and deselect": a deselect. */
    @Test
    void deselectingEndsThePinValidation(SmartCardSession card) {
        PinSession pin = card.pin().cla(0x80).padTo(8, 0xFF);
        pin.verify(1, "1234").requireSuccess();

        card.deselect();                               // the applet's deselect() runs, CLEAR_ON_DESELECT memory is cleared
        card.select(WalletApplet.class);
        assertThat(pin.isVerified(1)).isFalse();       // WalletApplet.deselect() resets the PIN
    }

    /** Recipe "PIN". */
    @Test
    void pin(SmartCardSession card) {
        PinSession pin = card.pin().cla(0x80).padTo(8, 0xFF);    // the applet's class, PIN padded with FF to 8 bytes
        assertThat(pin.verify(1, "0000")).hasStatusWord(0x63C2);  // wrong PIN: 2 tries left
        assertThat(pin.retries(1)).hasValue(2);
        assertThat(pin.verify(1, "1234")).isSuccess();
        assertThat(pin.isVerified(1)).isTrue();
        assertThat(card.send(DEBIT.data(0x00, 0x00))).isSuccess();
    }

    /** Recipe "TLV". */
    @Test
    void tlv(SmartCardSession card) {
        card.send(CREDIT.data(0x00, 0x64)).requireSuccess();
        APDUResponse info = card.send(GET_INFO);
        assertThat(info).tlv().tag(0xE1).isConstructed().tag(0x81).hasValue("0064");
        TLV tries = info.tlv().at(0xE1, 0x82).orElseThrow();      // E1 > 82, or Optional.empty()
        assertThat(tries.value()).containsExactly(3);
    }

    /** Recipe "Watching the exchanges": the history of the current test. */
    @Test
    void historyHoldsTheExchangesOfThisTest(SmartCardSession card) {
        card.send(CREDIT.data(0x00, 0x64)).requireSuccess();
        assertThat(card.history().entries())                       // this test's exchanges: its SELECT, CREDIT
                .extracting(APDULogEntry::ins).containsExactly(0xA4, 0x30);
    }

    /** Recipe "One test class, several backends". */
    @Test
    @EnabledOnBackend({Mode.SIMULATED_GP, Mode.LIVECARD})          // only where the package is converted and loaded
    void runsWhereThePackageIsConvertedAndLoaded(SmartCardSession card) {
        assertThat(card.send(GET_BALANCE)).isSuccess();
    }
}
