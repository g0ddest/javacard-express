package name.velikodniy.jcexpress.livecard.sim;

import name.velikodniy.jcexpress.scp.SCP03;
import name.velikodniy.jcexpress.scp.SCPKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The simulated ISD checks SCP03 secure messaging like a card (GlobalPlatform Amendment D v1.1.2): the C-MAC of
 * every command in the session with the MAC chaining value (6.2.3, 6.2.4), C-DECRYPTION with the counter-based ICV
 * (6.2.6), and a failure ends the session. So the offline run of the live suite catches a regression in the
 * {@code gp} module's wrapping, which a real card would reject with '6982'. The commands under test are wrapped
 * by the {@code gp} module; the simulated ISD verifies them with its own code.
 */
class SimulatedIsdTest {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final byte[] TEST_KEY = HEX.parseHex("404142434445464748494A4B4C4D4E4F");
    private static final byte[] HOST_CHALLENGE = HEX.parseHex("A1A2A3A4A5A6A7A8");
    private static final String GET_STATUS_ISD = "80F28002024F0000";

    private final SimulatedCard card = new SimulatedCard(TEST_KEY, SimulatedApplets.of(module -> Optional.empty(), java.util.List.of()));

    /** SELECT ISD, INITIALIZE UPDATE and EXTERNAL AUTHENTICATE at a level; returns the host's channel. */
    private SCP03 authenticate(int level) {
        card.transmit(HEX.parseHex("00A4040008A00000015100000000"));
        byte[] response = card.transmit(HEX.parseHex("8050000008" + HEX.formatHex(HOST_CHALLENGE) + "00"));
        byte[] data = Arrays.copyOf(response, response.length - 2);
        SCP03 scp = SCP03.from(SCPKeys.defaultKeys(), HOST_CHALLENGE, data, level);
        assertThat(sw(card.transmit(scp.externalAuthenticate()))).isEqualTo(0x9000);
        return scp;
    }

    @ParameterizedTest(name = "security level {0}")
    @ValueSource(ints = {0x01, 0x03})
    void commandsWrappedByTheHostPassInSequence(int level) {
        SCP03 scp = authenticate(level);

        byte[] first = card.transmit(scp.wrap(HEX.parseHex(GET_STATUS_ISD)));
        byte[] second = card.transmit(scp.wrap(HEX.parseHex(GET_STATUS_ISD)));

        assertThat(sw(first)).isEqualTo(0x9000);
        assertThat(HEX.formatHex(first)).startsWith("E3");
        assertThat(sw(second)).as("MAC chaining value carried on").isEqualTo(0x9000);
    }

    @Test
    void wrongCmacIsRejectedAndEndsTheSession() {
        SCP03 scp = authenticate(0x01);

        assertThat(sw(card.transmit(HEX.parseHex("84F280020A4F00" + "00".repeat(8) + "00")))).isEqualTo(0x6982);
        assertThat(sw(card.transmit(scp.wrap(HEX.parseHex(GET_STATUS_ISD))))).as("session ended").isEqualTo(0x6982);
    }

    @Test
    void replayedCommandIsRejected() {
        SCP03 scp = authenticate(0x01);
        byte[] command = scp.wrap(HEX.parseHex(GET_STATUS_ISD));

        assertThat(sw(card.transmit(command))).isEqualTo(0x9000);
        assertThat(sw(card.transmit(command))).isEqualTo(0x6982);
    }

    @Test
    void plainDataAtLevel03IsRejected() {
        authenticate(0x03);

        assertThat(sw(card.transmit(HEX.parseHex("84F280020A4F00" + "00".repeat(8) + "00")))).isEqualTo(0x6982);
    }

    @Test
    void commandWithoutSecureMessagingInACmacSessionIsRejected() {
        authenticate(0x01);

        assertThat(sw(card.transmit(HEX.parseHex(GET_STATUS_ISD)))).isEqualTo(0x6982);
    }

    @Test
    void level00SessionTakesPlainCommands() {
        authenticate(0x00);

        assertThat(sw(card.transmit(HEX.parseHex(GET_STATUS_ISD)))).isEqualTo(0x9000);
    }

    /** The encrypted DELETE data must be decrypted to find the AID (and the counter must match the host's). */
    @Test
    void encryptedCommandDataIsDecrypted() {
        card.addForeignApplication("F04A4358010101", "A0000001515350", 0x00, 0x07);
        SCP03 scp = authenticate(0x03);
        card.transmit(scp.wrap(HEX.parseHex(GET_STATUS_ISD)));

        byte[] response = card.transmit(scp.wrap(HEX.parseHex("80E40000094F07F04A435801010100")));

        assertThat(sw(response)).isEqualTo(0x9000);
        assertThat(card.applications()).doesNotContain("F04A4358010101");
    }

    private static int sw(byte[] response) {
        return ((response[response.length - 2] & 0xFF) << 8) | (response[response.length - 1] & 0xFF);
    }
}
