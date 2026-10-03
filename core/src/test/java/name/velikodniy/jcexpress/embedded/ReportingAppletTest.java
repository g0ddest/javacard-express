package name.velikodniy.jcexpress.embedded;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.fakes.ExtendedEchoApplet;
import name.velikodniy.jcexpress.fakes.ThrowingApplet;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

/**
 * jCardSim's runtime calls an applet through the wrapper that reports what it throws ({@link ReportingApplet}), so
 * the wrapper keeps what the runtime checks: an applet that implements {@code ExtendedLength} receives extended
 * length commands (ISO/IEC 7816-4:2005 5.1), any other applet is answered '6700' (wrong length), as before.
 */
class ReportingAppletTest {

    private static final AID AID_1 = AID.fromHex("F0000000010101");

    @Test
    void anExtendedLengthAppletReceivesAnExtendedCommand() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            card.install(ExtendedEchoApplet.class, AID_1);

            assertThat(card.send(0x80, 0x01, 0x00, 0x00, new byte[300], 2)).isSuccess().dataEquals(0x01, 0x2C);
        }
    }

    @Test
    void anotherAppletIsAnsweredWrongLength() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            card.install(ThrowingApplet.class, AID_1);

            assertThat(card.send(0x80, 0x05, 0x00, 0x00, new byte[300], 2)).statusWord(0x6700);
        }
    }
}
