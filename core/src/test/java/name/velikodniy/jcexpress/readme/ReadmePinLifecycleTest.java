package name.velikodniy.jcexpress.readme;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.PinApplet;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.pin.PinSession;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

/** The "Complete PIN Lifecycle" example of core/README.md, verbatim, in a {@code @JavaCardTest} class. */
@JavaCardTest
class ReadmePinLifecycleTest {

    @Test
    @InstallApplet(PinApplet.class)
    void shouldHandleFullPinLifecycle(SmartCardSession card) {
        PinSession pin = card.pin();

        pin.verify(1, "1234");

        APDUResponse wrong = pin.verify(1, "0000");
        assertThat(wrong).hasStatusWord(0x63C2);      // 2 retries left

        assertThat(pin.retries(1)).hasValue(2);

        pin.verify(1, "0000");
        pin.verify(1, "0000");
        assertThat(pin.isBlocked(1)).isTrue();

        pin.unblock(1, "12345678", "5678");
        assertThat(pin.verify(1, "5678")).isSuccess();
    }
}
