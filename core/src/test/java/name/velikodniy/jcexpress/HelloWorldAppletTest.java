package name.velikodniy.jcexpress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

@ExtendWith(JavaCardExtension.class)
class HelloWorldAppletTest {

    @SmartCard
    SmartCardSession card;

    @Test
    void shouldReturnHello() {
        card.install(HelloWorldApplet.class);
        APDUResponse response = card.send(0x80, 0x01);
        assertThat(response).isSuccess();
        assertThat(response).dataAsString().isEqualTo("Hello");
    }

    @Test
    void shouldEchoData() {
        card.install(HelloWorldApplet.class);
        byte[] data = {0x01, 0x02, 0x03};
        APDUResponse response = card.send(0x80, 0x02, 0x00, 0x00, data);
        assertThat(response).isSuccess();
        assertThat(response).dataEquals(0x01, 0x02, 0x03);
    }

    @Test
    void shouldRejectUnsupportedIns() {
        card.install(HelloWorldApplet.class);
        APDUResponse response = card.send(0x80, 0xFF);
        assertThat(response).statusWord(0x6D00);
    }

    @Test
    void shouldWorkAfterResetAndReinstall() {
        card.install(HelloWorldApplet.class);
        APDUResponse first = card.send(0x80, 0x01);
        assertThat(first).isSuccess();

        card.reset();
        card.install(HelloWorldApplet.class);

        APDUResponse second = card.send(0x80, 0x01);
        assertThat(second).isSuccess();
        assertThat(second).dataAsString().isEqualTo("Hello");
    }
}
