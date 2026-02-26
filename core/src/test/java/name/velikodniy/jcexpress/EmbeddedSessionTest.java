package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

class EmbeddedSessionTest {

    private EmbeddedSession session;

    @BeforeEach
    void setUp() {
        session = new EmbeddedSession(false);
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void shouldInstallAndSendCommand() {
        session.install(HelloWorldApplet.class);
        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response).isSuccess();
        assertThat(response).dataAsString().isEqualTo("Hello");
    }

    @Test
    void shouldInstallWithExplicitAid() {
        AID aid = AID.of(0xF0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06);
        session.install(HelloWorldApplet.class, aid);
        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response).isSuccess();
    }

    @Test
    void shouldSelectByClass() {
        session.install(HelloWorldApplet.class);
        session.select(HelloWorldApplet.class);
        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response).isSuccess();
    }

    @Test
    void shouldSelectByAid() {
        AID aid = AID.of(0xF0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06);
        session.install(HelloWorldApplet.class, aid);
        session.select(aid);
        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response).isSuccess();
    }

    @Test
    void shouldTransmitRawApdu() {
        session.install(HelloWorldApplet.class);
        // CLA=80, INS=01, P1=00, P2=00
        byte[] raw = {(byte) 0x80, 0x01, 0x00, 0x00};
        byte[] responseBytes = session.transmit(raw);
        // Response should end with 9000
        int len = responseBytes.length;
        int sw = ((responseBytes[len - 2] & 0xFF) << 8) | (responseBytes[len - 1] & 0xFF);
        org.assertj.core.api.Assertions.assertThat(sw).isEqualTo(0x9000);
    }

    @Test
    void shouldResetCard() {
        session.install(HelloWorldApplet.class);
        session.reset();
        // After reset, re-install should work
        session.install(HelloWorldApplet.class);
        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response).isSuccess();
    }

    @Test
    void shouldSendWithAllParameters() {
        session.install(HelloWorldApplet.class);
        byte[] data = {0x41, 0x42, 0x43};
        APDUResponse response = session.send(0x80, 0x02, 0x00, 0x00, data);
        assertThat(response).isSuccess();
        assertThat(response).dataEquals(0x41, 0x42, 0x43);
    }

    @Test
    void shouldSendWithLe() {
        session.install(HelloWorldApplet.class);
        APDUResponse response = session.send(0x80, 0x01, 0x00, 0x00, null, 5);
        assertThat(response).isSuccess();
        assertThat(response).hasDataLength(5);
        assertThat(response).dataAsString().isEqualTo("Hello");
    }

    @Test
    void shouldInstallWithParams() {
        AID aid = AID.of(0xF0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06);
        byte[] params = {0x01, 0x02, 0x03};
        session.install(HelloWorldApplet.class, aid, params);
        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response).isSuccess();
    }
}
