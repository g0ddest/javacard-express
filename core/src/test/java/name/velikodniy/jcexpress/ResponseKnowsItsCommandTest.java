package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.apdu.APDUBuilder;
import name.velikodniy.jcexpress.apdu.APDUSequence;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.fakes.ContractCardTerminal;
import name.velikodniy.jcexpress.fakes.RecordingSession;
import name.velikodniy.jcexpress.pcsc.PcscSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Responses returned by the sessions and helpers of the core module remember the command they answer, so
 * {@link APDUResponse#requireSuccess()} shows the whole exchange when it fails.
 */
class ResponseKnowsItsCommandTest {

    private static final String INS_NOT_SUPPORTED = "Expected SW 9000 (success) but was 6D00 (instruction code not"
            + " supported or invalid)\nC: 807E0000\nR: 6D00";

    @Test
    void embeddedSession() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            card.install(HelloWorldApplet.class);

            assertThatThrownBy(() -> card.send(0x80, 0x7E).requireSuccess()).hasMessage(INS_NOT_SUPPORTED);
        }
    }

    @Test
    void pcscSession() {
        ContractCardTerminal reader = new ContractCardTerminal("T=1", (channel, apdu) -> Hex.decode("6D00"));

        try (PcscSession card = PcscSession.open(reader)) {
            assertThatThrownBy(() -> card.send(0x80, 0x7E).requireSuccess()).hasMessage(INS_NOT_SUPPORTED);
        }
    }

    /** The logging decorator shows the command it logged, also when its delegate does not attach one. */
    @Test
    void loggingSession() {
        LoggingSession card = LoggingSession.wrap(new RecordingSession().reply("6D00"));

        assertThatThrownBy(() -> card.send(0x80, 0x7E).requireSuccess()).hasMessage(INS_NOT_SUPPORTED);
    }

    @Test
    void apduBuilder() {
        RecordingSession card = new RecordingSession().reply("6D00");

        assertThatThrownBy(() -> APDUBuilder.command().cla(0x80).ins(0x7E).sendTo(card).requireSuccess())
                .hasMessage(INS_NOT_SUPPORTED);
    }

    /** After GET RESPONSE chaining the caller's command is shown, with the assembled response. */
    @Test
    void apduSequence() {
        RecordingSession card = new RecordingSession().reply("6102").reply("01026A80");

        assertThatThrownBy(() -> APDUSequence.on(card).transmit(Hex.decode("80CA006600")).requireSuccess())
                .hasMessage("Expected SW 9000 (success) but was 6A80 (incorrect parameters in the command data field)"
                        + "\nC: 80CA006600\nR: 01026A80");
    }
}
