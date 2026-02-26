package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.container.ContainerSession;
import name.velikodniy.jcexpress.container.SmartCardContainer;
import org.junit.jupiter.api.*;
import org.testcontainers.DockerClientFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ContainerSessionTest {

    private static SmartCardContainer container;
    private ContainerSession session;

    @BeforeAll
    static void startContainer() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, skipping container tests");
        Path dockerDir = Paths.get(System.getProperty("user.dir")).resolve("../docker").normalize();
        container = new SmartCardContainer(dockerDir);
        container.start();
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        // Each test gets a fresh session; reset state from previous test
        session = new ContainerSession(
                container.getHost(),
                container.getPort(),
                null, // container lifecycle managed by @BeforeAll/@AfterAll
                false
        );
        session.reset();
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
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
        byte[] raw = {(byte) 0x80, 0x01, 0x00, 0x00};
        byte[] responseBytes = session.transmit(raw);
        int len = responseBytes.length;
        int sw = ((responseBytes[len - 2] & 0xFF) << 8) | (responseBytes[len - 1] & 0xFF);
        org.assertj.core.api.Assertions.assertThat(sw).isEqualTo(0x9000);
    }

    @Test
    void shouldResetCard() {
        session.install(HelloWorldApplet.class);
        session.reset();
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
}
