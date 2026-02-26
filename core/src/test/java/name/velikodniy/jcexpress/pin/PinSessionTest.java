package name.velikodniy.jcexpress.pin;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.PinApplet;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PinSessionTest {

    private EmbeddedSession session;
    private PinSession pin;

    @BeforeEach
    void setUp() {
        session = new EmbeddedSession(false);
        session.install(PinApplet.class);
        pin = PinSession.on(session);
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void verifyShouldSucceedWithCorrectPin() {
        APDUResponse response = pin.verify(1, "1234");
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void verifyShouldFailWithWrongPin() {
        APDUResponse response = pin.verify(1, "0000");
        assertThat(response.isSuccess()).isFalse();
        // SW should be 63C2 (2 retries remaining)
        assertThat(response.sw() & 0xFFF0).isEqualTo(0x63C0);
    }

    @Test
    void retriesRemainingShouldReturn3Initially() {
        int retries = pin.retriesRemaining(1);
        assertThat(retries).isEqualTo(3);
    }

    @Test
    void retriesRemainingShouldDecreaseAfterFailedVerify() {
        pin.verify(1, "0000"); // wrong
        int retries = pin.retriesRemaining(1);
        assertThat(retries).isEqualTo(2);
    }

    @Test
    void isBlockedShouldReturnFalseInitially() {
        assertThat(pin.isBlocked(1)).isFalse();
    }

    @Test
    void isBlockedShouldReturnTrueAfterMaxAttempts() {
        pin.verify(1, "0000");
        pin.verify(1, "0000");
        pin.verify(1, "0000");
        assertThat(pin.isBlocked(1)).isTrue();
    }

    @Test
    void changeShouldUpdatePin() {
        // Change from "1234" to "5678"
        APDUResponse response = pin.change(1, "1234", "5678");
        assertThat(response.isSuccess()).isTrue();

        // Old PIN should fail
        APDUResponse oldResult = pin.verify(1, "1234");
        assertThat(oldResult.isSuccess()).isFalse();

        // New PIN should work
        APDUResponse newResult = pin.verify(1, "5678");
        assertThat(newResult.isSuccess()).isTrue();
    }

    @Test
    void unblockShouldResetBlockedPin() {
        // Block the PIN
        pin.verify(1, "0000");
        pin.verify(1, "0000");
        pin.verify(1, "0000");
        assertThat(pin.isBlocked(1)).isTrue();

        // Unblock with PUK and set new PIN "9999"
        APDUResponse response = pin.unblock(1, "12345678", "9999");
        assertThat(response.isSuccess()).isTrue();

        // New PIN should work
        APDUResponse verifyResult = pin.verify(1, "9999");
        assertThat(verifyResult.isSuccess()).isTrue();
    }

    @Test
    void getDataShouldFailWithoutVerification() {
        // INS=0x01 requires PIN verification
        APDUResponse response = session.send(0x00, 0x01);
        assertThat(response.sw()).isEqualTo(0x6982);
    }

    @Test
    void getDataShouldSucceedAfterVerification() {
        pin.verify(1, "1234");
        APDUResponse response = session.send(0x00, 0x01);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.dataAsString()).isEqualTo("OK");
    }
}
