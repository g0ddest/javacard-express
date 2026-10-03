package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.apdu.APDUCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The C-APDU bytes {@link ContainerSession} puts on the wire follow the session-wide {@code le} contract
 * (ISO/IEC 7816-4:2005 5.1): {@code le = -1} means no Le field, {@code 1..256} is Ne in a short APDU
 * ({@code 256} is encoded as {@code '00'}), larger values use the extended form. The bytes are those of
 * {@link APDUCodec#encode}, the single encoder shared by all backends.
 */
class ApduEncodingTest {

    private static final byte[] SW_9000 = {(byte) 0x90, 0x00};

    private FakeSimulator fake;
    private ContainerSession session;

    @BeforeEach
    void connect() throws IOException {
        fake = new FakeSimulator(FakeSimulator.okWith(SW_9000));
        session = fake.newSession();
    }

    @AfterEach
    void disconnect() throws IOException {
        session.close();
        fake.close();
    }

    @Test
    void sendWithoutLeHasNoLeField() {
        APDUResponse response = session.send(0x00, 0xCA, 0x00, 0x66);

        assertThat(response.sw()).isEqualTo(0x9000);
        assertThat(lastApdu()).isEqualTo("00CA0066");
    }

    @Test
    void negativeLeMeansNoLeField() {
        session.send(0x00, 0xCA, 0x00, 0x66, null, -1);

        assertThat(lastApdu()).isEqualTo("00CA0066");
    }

    @Test
    void ne256IsTheShortLeZero() {
        session.send(0x00, 0xB0, 0x00, 0x00, null, 256);

        assertThat(lastApdu()).isEqualTo("00B0000000");
    }

    @Test
    void dataWithoutLeIsCase3() {
        session.send(0x80, 0x02, 0x00, 0x00, new byte[]{0x41, 0x42});

        assertThat(lastApdu()).isEqualTo("80020000024142");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 255, 256, 257, 65536})
    void bytesOnTheWireAreThoseOfTheSharedEncoder(int le) {
        byte[] data = {0x01, 0x02, 0x03};

        session.send(0x80, 0x10, 0x01, 0x02, data, le);

        assertThat(lastApdu()).isEqualTo(HexFormat.of().withUpperCase()
                .formatHex(APDUCodec.encode(0x80, 0x10, 0x01, 0x02, data, le)));
    }

    @Test
    void rawTransmitSendsTheBytesUnchanged() {
        byte[] response = session.transmit(new byte[]{(byte) 0x80, 0x01, 0x00, 0x00, 0x00});

        assertThat(response).containsExactly(0x90, 0x00);
        assertThat(lastApdu()).isEqualTo("8001000000");
    }

    private String lastApdu() {
        FakeSimulator.Request request = fake.requests().get(fake.requests().size() - 1);
        assertThat(request.command()).isEqualTo(Protocol.CMD_TRANSMIT);
        return HexFormat.of().withUpperCase().formatHex(request.payload());
    }
}
