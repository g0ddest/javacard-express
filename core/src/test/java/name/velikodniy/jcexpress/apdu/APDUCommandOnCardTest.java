package name.velikodniy.jcexpress.apdu;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.model.ModelApplet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

/**
 * Commands as values on the card of a {@link JavaCardTest} class (jCardSim): constants, hex as in a specification
 * or a trace, and data-driven tests whose columns JUnit converts to {@link APDUCommand} and {@link APDUResponse}.
 * {@code ModelApplet} answers INS {@code 10} with its incremented counter and INS {@code 11} with the counter.
 */
@JavaCardTest
@InstallApplet(ModelApplet.class)
class APDUCommandOnCardTest {

    private static final APDUCommand INCREMENT = APDUCommand.of(0x80, 0x10).le(2);

    @ParameterizedTest
    @CsvSource({"8011000002, 0000 9000", "8010000002, 0001 9000", "807F0000, 6D00"})
    void junitConvertsTheCommandAndTheExpectedResponse(APDUCommand command, APDUResponse expected,
                                                        SmartCardSession card) {
        assertThat(card.send(command)).isEqualTo(expected);
    }

    @Test
    void aCommandConstantAndHexTextGoThroughTheCardLikeAnyCommand(SmartCardSession card) {
        assertThat(card.send(INCREMENT)).isSuccess().u16(0).isEqualTo(1);
        assertThat(card.sendHex("80 10 00 00 02")).isSuccess().u16(0).isEqualTo(2);

        assertThat(card.history().transcript()).contains("C: 8010000002").contains("R: 00029000");
    }

    @Test
    void byteConstantsOfTheAppletAreHeaderBytes(SmartCardSession card) {
        byte cla = (byte) 0x80;
        byte ins = 0x11;

        assertThat(card.send(cla, ins, 0, 0, null, 2)).hasDataHex("0000").isSuccess();
    }
}
