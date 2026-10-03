package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.fakes.ContractCardTerminal;
import name.velikodniy.jcexpress.fakes.ProbeApplet;
import name.velikodniy.jcexpress.pcsc.PcscSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The {@code le} contract of {@link SmartCardSession#send(int, int, int, int, byte[], int)}
 * (ISO/IEC 7816-4:2005 5.1): {@link SmartCardSession#NO_LE} means "no Le field" (Ne = 0), 1..65536 is Ne
 * (256 is coded as the short Le '00'), 0 means "Le field set to '00' (maximum)".
 *
 * <p>Checked on every shipped backend and through {@link LoggingSession}, which must log exactly the
 * bytes the backend puts on the wire.</p>
 */
class LeContractTest {

    /** EmbeddedSession: what the applet sees ({@code APDU.setOutgoing()} returns Ne). */
    @Nested
    class Embedded {

        private EmbeddedSession card;

        @BeforeEach
        void setUp() {
            card = new EmbeddedSession();
            card.install(ProbeApplet.class);
        }

        @AfterEach
        void tearDown() {
            card.close();
        }

        /** INS 13 returns min(P1P2, Ne) pattern bytes, so the response length reveals Ne. */
        private int observedNe(SmartCardSession session, int le) {
            APDUResponse r = session.send(0x80, 0x13, 0x00, 0x20, null, le);
            assertThat(r.sw()).isEqualTo(0x9000);
            return r.data().length;
        }

        @Test
        void noLeMeansNeZero() {
            assertThat(observedNe(card, SmartCardSession.NO_LE)).isZero();
        }

        @Test
        void le256IsShortLe00() {
            APDUResponse r = card.send(0x80, 0x11, 0x00, 0x00, null, 256);
            // CLA INS P1 P2 P3 as seen by the applet, then Ne (2 bytes)
            assertThat(Hex.encode(r.data())).startsWith("8011000000" + "0100");
        }

        @Test
        void leZeroIsShortLe00ForCompatibility() {
            APDUResponse r = card.send(0x80, 0x11, 0x00, 0x00, null, 0);
            assertThat(Hex.encode(r.data())).startsWith("8011000000" + "0100");
        }

        @Test
        void explicitNe() {
            assertThat(observedNe(card, 5)).isEqualTo(5);
        }

        @Test
        void convenienceOverloadsSendNoLe() {
            assertThat(card.send(0x80, 0x11).data()).isEmpty();
            assertThat(card.send(0x80, 0x11, 0x00, 0x00).data()).isEmpty();
            assertThat(card.send(0x80, 0x11, 0x00, 0x00, new byte[]{0x55}).data()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {-2, -100, 65537})
        void invalidLeIsRejected(int le) {
            assertThatThrownBy(() -> card.send(0x80, 0x11, 0x00, 0x00, null, le))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("le");
        }
    }

    /** LoggingSession over the real embedded backend (the README "logged()" features). */
    @Nested
    class LoggingOverEmbedded {

        private EmbeddedSession card;

        @BeforeEach
        void setUp() {
            card = new EmbeddedSession();
            card.install(HelloWorldApplet.class);
        }

        @AfterEach
        void tearDown() {
            card.close();
        }

        @Test
        void everySendOverloadWorks() {
            LoggingSession logged = card.logged();
            assertThat(logged.send(0x80, 0x01).dataAsString()).isEqualTo("Hello");
            assertThat(logged.send(0x80, 0x01, 0x00, 0x00).dataAsString()).isEqualTo("Hello");
            assertThat(logged.send(0x80, 0x02, 0x00, 0x00, new byte[]{0x41}).data()).containsExactly(0x41);
            assertThat(logged.send(0x80, 0x01, 0x00, 0x00, null, 256).dataAsString()).isEqualTo("Hello");
            assertThat(logged.entries()).extracting(APDULogEntry::commandHex).containsExactly(
                    "80 01 00 00", "80 01 00 00", "80 02 00 00 01 41", "80 01 00 00 00");
        }

        /** core/README.md "Composing Decorators". */
        @Test
        void pinSessionOverLoggedSessionWorks() {
            EmbeddedSession pinCard = new EmbeddedSession();
            try {
                pinCard.install(PinApplet.class);
                LoggingSession logged = pinCard.logged(true);
                assertThat(logged.pin().verify(1, "1234").sw()).isEqualTo(0x9000);
                assertThat(logged.lastEntry().commandHex()).isEqualTo("00 20 00 01 04 31 32 33 34");
            } finally {
                pinCard.close();
            }
        }

        @Test
        void logShowsTheBytesTheAppletReceived() {
            card.install(ProbeApplet.class, AID.fromHex("F0000000AA"));
            LoggingSession logged = card.logged();
            APDUResponse r = logged.send(0x80, 0x11, 0x01, 0x02, null, 0);
            // the applet echoes CLA INS P1 P2 P3 exactly as received
            assertThat(Hex.encodeSpaced(java.util.Arrays.copyOf(r.data(), 5)))
                    .isEqualTo(logged.lastEntry().commandHex());
        }
    }

    /** PcscSession: the bytes on the wire (javax.smartcardio contract fake). */
    @Nested
    class Pcsc {

        private final ContractCardTerminal terminal = new ContractCardTerminal();

        private String wire(int index) {
            return Hex.encodeSpaced(terminal.wire.get(index));
        }

        @Test
        void encodesTheContract() {
            try (PcscSession card = PcscSession.open(terminal)) {
                card.send(0x00, 0xCA, 0x00, 0x66, null, SmartCardSession.NO_LE);
                card.send(0x00, 0xCA, 0x00, 0x66, null, 256);
                card.send(0x00, 0xCA, 0x00, 0x66, null, 0);
                card.send(0x00, 0xCA, 0x00, 0x66, null, 1);
                card.send(0x00, 0xCA, 0x00, 0x66, null, 65536);
                card.send(0x00, 0xCA, 0x00, 0x66);
            }
            assertThat(wire(0)).isEqualTo("00 CA 00 66");
            assertThat(wire(1)).isEqualTo("00 CA 00 66 00");
            assertThat(wire(2)).isEqualTo("00 CA 00 66 00");
            assertThat(wire(3)).isEqualTo("00 CA 00 66 01");
            assertThat(wire(4)).isEqualTo("00 CA 00 66 00 00 00");
            assertThat(wire(5)).isEqualTo("00 CA 00 66");
        }

        @Test
        void loggingSessionLogsTheWireBytes() {
            try (PcscSession card = PcscSession.open(terminal)) {
                LoggingSession logged = card.logged();
                logged.send(0x80, 0xCA, 0x00, 0x66, null, 256);
                logged.send(0x80, 0xCA, 0x00, 0x66);
                assertThat(logged.entries().get(0).commandHex()).isEqualTo(wire(0));
                assertThat(logged.entries().get(1).commandHex()).isEqualTo(wire(1));
            }
        }

        @Test
        void invalidLeIsRejected() {
            try (PcscSession card = PcscSession.open(terminal)) {
                assertThatThrownBy(() -> card.send(0x00, 0xB0, 0x00, 0x00, null, -5))
                        .isInstanceOf(IllegalArgumentException.class);
            }
            assertThat(terminal.wire).isEmpty();
        }
    }
}
