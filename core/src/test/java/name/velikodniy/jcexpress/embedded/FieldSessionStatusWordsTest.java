package name.velikodniy.jcexpress.embedded;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.apdu.APDUSequence;
import name.velikodniy.jcexpress.model.ChunkedApplet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An {@link EmbeddedSession} used directly (a {@code @SmartCard} field, a session parameter outside
 * {@code @JavaCardTest} classes) returns '61XX' and '6CXX' as the applet answered them, so that tests of the
 * transmission itself see them; {@link APDUSequence} completes them (ISO/IEC 7816-4:2005 5.1.3).
 */
class FieldSessionStatusWordsTest {

    private static final AID AID_1 = AID.fromHex("F0000000010101");

    @Test
    void sendReturns61xxAnd6cxxAsTheAppletAnswered() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            card.install(ChunkedApplet.class, AID_1);

            assertThat(card.send(0x80, 0x40, 0x02, 0x58, null, 256).sw()).isEqualTo(0x6100);
            assertThat(card.send(0x80, 0x42, 0x00, 0x00, null, 256).sw()).isEqualTo(0x6C05);
        }
    }

    @Test
    void apduSequenceCompletesThem() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            card.install(ChunkedApplet.class, AID_1);

            APDUResponse all = APDUSequence.on(card).send(0x80, 0x40, 0x02, 0x58, null, 256);

            assertThat(all.sw()).isEqualTo(0x9000);
            assertThat(all.data()).hasSize(600);
        }
    }
}
