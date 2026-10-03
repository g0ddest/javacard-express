package name.velikodniy.jcexpress.pin;

import name.velikodniy.jcexpress.APDULogEntry;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The options of {@link PinSession} for applets whose PIN commands differ from the interindustry defaults: the
 * class byte ({@link PinSession#cla(int)}), PIN padding ({@link PinSession#padTo(int, int)}, the PIV Card
 * Application pads its PIN with {@code 'FF'} to 8 bytes, NIST SP 800-73-4 Part 2), and the retry counter and
 * verification state read with a VERIFY without data (ISO/IEC 7816-4:2005 7.5.6).
 */
class PinSessionOptionsTest {

    private static final int PIN_REF = 0x80;

    private EmbeddedSession session;
    private PinSession pin;

    @BeforeEach
    void setUp() {
        session = new EmbeddedSession();
        session.install(ProprietaryPinApplet.class);
        pin = PinSession.on(session).cla(0x80).padTo(8, 0xFF);
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    private String lastCommand() {
        List<APDULogEntry> entries = session.history().entries();
        return Hex.encode(entries.getLast().command());
    }

    @Test
    void theClassByteIsInterindustryByDefaultAndConfigurable_iso7816_4_5_1_1() {
        assertThat(PinSession.on(session).padTo(8, 0xFF).verify(PIN_REF, "123456").sw()).isEqualTo(0x6E00);
        assertThat(lastCommand()).startsWith("0020");

        assertThat(pin.verify(PIN_REF, "123456").sw()).isEqualTo(0x9000);
        assertThat(lastCommand()).startsWith("8020");
    }

    @Test
    void everyPinCommandUsesTheClassByteAndThePadding() {
        pin.change(PIN_REF, "123456", "654321");
        assertThat(lastCommand()).isEqualTo("8024008010" + "313233343536FFFF" + "363534333231FFFF");
        pin.changeWithoutOldPin(PIN_REF, "654321");
        assertThat(lastCommand()).isEqualTo("8024018008" + "363534333231FFFF");
        pin.unblock(PIN_REF, "12345678", "654321");
        assertThat(lastCommand()).isEqualTo("802C008010" + "3132333435363738" + "363534333231FFFF");
        pin.isBlocked(PIN_REF);
        assertThat(lastCommand()).isEqualTo("80200080");
    }

    @Test
    void aByteConstantOfTheAppletIsAcceptedAsTheClassByte() {
        byte claProprietary = (byte) 0x80;

        assertThat(PinSession.on(session).cla(claProprietary).padTo(8, 0xFF).verify(PIN_REF, "123456").sw())
                .isEqualTo(0x9000);
        assertThat(lastCommand()).startsWith("8020");
    }

    @Test
    void aClassByteOutsideAByteIsRejected() {
        assertThatThrownBy(() -> pin.cla(0x100)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLA");
        assertThatThrownBy(() -> pin.cla(-129)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void padToFillsThePinUpWithThePadByte_nistSp800_73_4() {
        assertThat(pin.verify(PIN_REF, "123456").sw()).isEqualTo(0x9000);
        assertThat(lastCommand()).isEqualTo("8020008008313233343536FFFF");
    }

    @Test
    void withoutPaddingThePinHasItsEncodedLength() {
        assertThat(PinSession.on(session).cla(0x80).verify(PIN_REF, "123456").sw()).isEqualTo(0x6A80);
        assertThat(lastCommand()).isEqualTo("8020008006313233343536");
    }

    @Test
    void aPinLongerThanThePaddedLengthIsRejectedBeforeAnythingIsSent() {
        int exchanges = session.history().entries().size();

        assertThatThrownBy(() -> pin.padTo(4, 0xFF).verify(PIN_REF, "123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("6 bytes").hasMessageContaining("padTo(4");
        assertThat(session.history().entries()).hasSize(exchanges);
    }

    @Test
    void padToRejectsALengthOrPadByteOutOfRange() {
        assertThatThrownBy(() -> pin.padTo(0, 0xFF)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pin.padTo(256, 0xFF)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pin.padTo(8, 0x100)).isInstanceOf(IllegalArgumentException.class);
        assertThat(pin.padTo(8, (byte) 0xFF)).isSameAs(pin);
    }

    @Test
    void retriesReportsTheCounterOfTheCard_iso7816_4_7_5_6() {
        assertThat(pin.retries(PIN_REF)).isEqualTo(OptionalInt.of(3));

        pin.verify(PIN_REF, "000000");

        assertThat(pin.retries(PIN_REF)).isEqualTo(OptionalInt.of(2));
        assertThat(pin.isVerified(PIN_REF)).isFalse();
    }

    @Test
    void aVerifiedPinHasNoCounterToReport_iso7816_4_7_5_6() {
        pin.verify(PIN_REF, "123456");

        assertThat(pin.isVerified(PIN_REF)).isTrue();
        assertThat(pin.retries(PIN_REF)).isEmpty();
        assertThat(pin.retriesRemaining(PIN_REF)).isEqualTo(-1);
    }

    @Test
    void aBlockedPinHasNoRetriesLeft_iso7816_4_7_5_6() {
        for (int i = 0; i < 3; i++) {
            pin.verify(PIN_REF, "000000");
        }

        assertThat(pin.retries(PIN_REF)).isEqualTo(OptionalInt.of(0));
        assertThat(pin.isBlocked(PIN_REF)).isTrue();
        assertThat(pin.isVerified(PIN_REF)).isFalse();
    }

    @Test
    void anAnswerWithoutACounterGivesNoRetries() {
        PinSession wrongClass = PinSession.on(session);

        assertThat(wrongClass.retries(PIN_REF)).isEmpty();
        assertThat(wrongClass.isVerified(PIN_REF)).isFalse();
        assertThat(wrongClass.retriesRemaining(PIN_REF)).isEqualTo(-1);
    }
}
