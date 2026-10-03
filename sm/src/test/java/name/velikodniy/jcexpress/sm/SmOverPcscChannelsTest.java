package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.LogicalChannel;
import name.velikodniy.jcexpress.fakes.ContractCardTerminal;
import name.velikodniy.jcexpress.pcsc.PcscSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Secure messaging over the PC/SC backend's logical channels (integration of the sm and core fix branches).
 *
 * <p>{@link SMCodec} marks protected commands in the class byte (ISO/IEC 7816-4:2005 5.4.1: first interindustry
 * class b4-b3 = 11, further interindustry class b6 = 1) and keeps the channel bits; {@link PcscSession} routes every
 * command to the {@code javax.smartcardio} channel its class byte codes (5.1.1, Tables 2 and 3). The fake terminal
 * adjusts CLA like the SunPCSC provider and records the bytes that reach the card. No real reader is used.</p>
 */
class SmOverPcscChannelsTest {

    private static final byte[] KEY = Hex.decode("0123456789ABCDEFFEDCBA9876543210");
    private static final byte[] SW_FILE_NOT_FOUND = {0x6A, (byte) 0x82};

    private ContractCardTerminal reader;
    private PcscSession card;

    @BeforeEach
    void connect() {
        // channels are assigned by the card; every other command is answered with an unprotected 6A82
        reader = new ContractCardTerminal("T=1", ContractCardTerminal.cardAssignsChannels(
                (channel, apdu) -> SW_FILE_NOT_FOUND.clone()));
        card = PcscSession.open(reader);
    }

    @AfterEach
    void disconnect() {
        card.close();
    }

    private static SMSession protect(PcscSession session) {
        return SMSession.wrap(session, new SMContext(SMAlgorithm.DES3, new SMKeys(KEY, KEY), new byte[8]));
    }

    private byte[] lastOnTheWire() {
        return reader.wire.get(reader.wire.size() - 1);
    }

    @Test
    void protectedCommandOnTheBasicChannelUsesCla0C() {
        APDUResponse response = protect(card).send(0x00, 0xB0, 0x00, 0x00, null, 4);

        assertThat(lastOnTheWire()[0]).isEqualTo((byte) 0x0C);
        assertThat(response.sw()).isEqualTo(0x6A82);
    }

    /**
     * Core's {@code le} contract (Ne = 256) carried through SM (ICAO 9303-11 9.8.4, ISO/IEC 7816-4:2005 5.1): DO'97'
     * holds Le '00' and the protected command, which always expects response data objects, ends with Le' '00'.
     */
    @Test
    void ne256BecomesDo97Le00AndProtectedLe00OnTheWire() {
        protect(card).send(0x00, 0xB0, 0x00, 0x00, null, 256);

        byte[] wire = lastOnTheWire();
        // 0C B0 00 00 | Lc '0D' | 97 01 00 | 8E 08 <MAC> | Le '00'
        assertThat(wire).hasSize(5 + 0x0D + 1);
        assertThat(wire[4]).isEqualTo((byte) 0x0D);
        assertThat(Hex.encode(Arrays.copyOfRange(wire, 5, 10))).isEqualTo("970100" + "8E08");
        assertThat(wire[wire.length - 1]).isZero();
    }

    @Test
    void protectedCommandForChannelOneReachesChannelOne() {
        LogicalChannel channel = LogicalChannel.open(card);
        assertThat(channel.channelNumber()).isEqualTo(1);

        protect(card).send(0x01, 0xB0, 0x00, 0x00, null, 4);

        assertThat(lastOnTheWire()[0]).as("CLA on the wire").isEqualTo((byte) 0x0D);
    }

    @Test
    void protectedCommandForChannelFiveUsesTheFurtherInterindustryClass() {
        for (int i = 0; i < 5; i++) {
            LogicalChannel.open(card);
        }

        protect(card).send(0x41, 0xB0, 0x00, 0x00, null, 4);

        // Table 3: 01xx xxxx, b6 = 1 (SM), b4-b1 = channel - 4
        assertThat(lastOnTheWire()[0]).as("CLA on the wire").isEqualTo((byte) 0x61);
    }

    @Test
    void protectedCommandForAChannelThatIsNotOpenIsRefusedBeforeReachingTheCard() {
        int before = reader.wire.size();

        assertThatThrownBy(() -> protect(card).send(0x02, 0xB0, 0x00, 0x00, null, 4))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("channel 2");
        assertThat(reader.wire).hasSize(before);
    }

    /**
     * javax.smartcardio cannot transmit MANAGE CHANNEL; the PC/SC backend maps the plain command to
     * {@code Card.openLogicalChannel()}, which sends an unprotected {@code 00 70 00 00 01}. A protected MANAGE
     * CHANNEL must not be replaced by that plain command (the card would end secure messaging, ICAO 9303-11 9.8.3),
     * so it is refused before anything reaches the card.
     */
    @Test
    void protectedManageChannelIsRefusedInsteadOfBeingSentInPlain() {
        int before = reader.wire.size();
        SMSession sm = protect(card);

        assertThatThrownBy(() -> sm.send(0x00, 0x70, 0x00, 0x00, null, 1))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("secure messaging");
        assertThat(reader.wire).hasSize(before);
    }
}
