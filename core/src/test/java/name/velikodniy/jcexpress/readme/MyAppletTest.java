package name.velikodniy.jcexpress.readme;

import name.velikodniy.jcexpress.JavaCardExtension;
import name.velikodniy.jcexpress.SmartCard;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

/**
 * The "Complete Test Lifecycle" example of core/README.md, verbatim (it must compile and pass as written).
 */
@ExtendWith(JavaCardExtension.class)
class MyAppletTest {

    @SmartCard
    SmartCardSession card;

    @Test
    void shouldProcessCommand() {
        card.install(MyApplet.class);

        // requireSuccess() throws if SW != 9000
        byte[] data = card.send(0x80, 0x01).requireSuccess().data();

        // Or assert fluently
        assertThat(card.send(0x80, 0x01))
            .isSuccess()
            .dataAsString().isEqualTo("Hello");
    }

    @Test
    void shouldSurviveReset() {
        card.install(MyApplet.class);
        card.send(0x80, 0x01).requireSuccess();

        card.reset();                  // applets stay installed, nothing is selected
        card.select(MyApplet.class);
        card.send(0x80, 0x01).requireSuccess();
    }
}
