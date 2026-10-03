package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.HelloWorldApplet;
import name.velikodniy.jcexpress.container.applets.BaseApplet;
import name.velikodniy.jcexpress.container.applets.DerivedApplet;
import name.velikodniy.jcexpress.container.applets.Helper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the audit finding "server-crashes-on-ordinary-input": a single malformed frame or an
 * ordinary class loading error used to terminate the whole simulator (single thread, no guard around the
 * client handler, {@code catch (Exception)} that misses {@link LinkageError}, unchecked payload length).
 *
 * <p>Every test runs a fresh server process and finally checks that the process is still alive and answers a new
 * client.</p>
 */
class ServerRobustnessTest {

    private static final byte[] AID = {(byte) 0xF0, 0x00, 0x00, 0x00, 0x01, 0x01};

    private LocalSimulatorServer server;

    @BeforeEach
    void startServer() {
        server = LocalSimulatorServer.start();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void negativePayloadLengthIsRejectedAndOnlyThatConnectionIsClosed() throws IOException {
        try (RawClient client = new RawClient(server.port())) {
            client.send(Protocol.CMD_TRANSMIT, -1, new byte[0]);

            RawClient.Reply reply = client.read();

            assertThat(reply.status()).isEqualTo(1);
            assertThat(reply.text()).contains("Invalid payload length -1");
            assertThat(client.closedByServer()).isTrue();
        }
        assertServerAlive();
    }

    @Test
    void hugePayloadLengthIsRejectedWithoutAllocatingIt() throws IOException {
        try (RawClient client = new RawClient(server.port())) {
            client.send(Protocol.CMD_TRANSMIT, 0x7FFF_FFF0, new byte[0]);

            RawClient.Reply reply = client.read();

            assertThat(reply.status()).isEqualTo(1);
            assertThat(reply.text()).contains("Invalid payload length " + 0x7FFF_FFF0);
        }
        assertServerAlive();
    }

    @Test
    void unknownCommandIsAnsweredAndTheConnectionStaysUsable() throws IOException {
        try (RawClient client = new RawClient(server.port())) {
            RawClient.Reply reply = client.exchange((byte) 0x7F, new byte[0]);

            assertThat(reply.status()).isEqualTo(1);
            assertThat(reply.text()).contains("Unknown command 0x7F");
            assertThat(client.exchange(Protocol.CMD_PING, new byte[0]).status()).isZero();
        }
        assertServerAlive();
    }

    @Test
    void truncatedInstallRequestIsReportedAsMalformed() throws IOException {
        try (RawClient client = new RawClient(server.port())) {
            RawClient.Reply reply = client.exchange(Protocol.CMD_INSTALL, new byte[]{5, 1, 2});

            assertThat(reply.status()).isEqualTo(1);
            assertThat(reply.text()).contains("Malformed INSTALL request");
            assertThat(client.exchange(Protocol.CMD_PING, new byte[0]).status()).isZero();
        }
        assertServerAlive();
    }

    @Test
    void corruptClassFileIsReportedNotFatal() throws IOException {
        byte[] garbage = {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE, 0, 1, 2};

        RawClient.Reply reply = install("audit.Corrupt", Map.of("audit.Corrupt", garbage));

        assertThat(reply.status()).isEqualTo(1);
        assertThat(reply.text()).contains("java.lang.ClassFormatError");
        assertServerAlive();
    }

    @Test
    void classFileOfNewerJavaVersionIsReportedNotFatal() throws IOException {
        byte[] hello = RawClient.classFile(HelloWorldApplet.class);
        hello[6] = 0;
        hello[7] = 0x7F; // class file major version 127: newer than any JVM

        RawClient.Reply reply = install(HelloWorldApplet.class.getName(), Map.of(HelloWorldApplet.class.getName(), hello));

        assertThat(reply.status()).isEqualTo(1);
        assertThat(reply.text()).contains("java.lang.UnsupportedClassVersionError");
        assertServerAlive();
    }

    @Test
    void missingSuperclassIsReportedNotFatal() throws IOException {
        String derived = DerivedApplet.class.getName();

        RawClient.Reply reply = install(derived, Map.of(derived, RawClient.classFile(DerivedApplet.class)));

        assertThat(reply.status()).isEqualTo(1);
        assertThat(reply.text())
                .contains("java.lang.NoClassDefFoundError")
                .contains(BaseApplet.class.getName().replace('.', '/'));
        assertServerAlive();
    }

    @Test
    void classThatIsNotAnAppletIsReported() throws IOException {
        String helper = Helper.class.getName();

        RawClient.Reply reply = install(helper, Map.of(helper, RawClient.classFile(Helper.class)));

        assertThat(reply.status()).isEqualTo(1);
        assertThat(reply.text()).contains("does not extend javacard.framework.Applet");
        assertServerAlive();
    }

    private RawClient.Reply install(String appletClass, Map<String, byte[]> classes) throws IOException {
        try (RawClient client = new RawClient(server.port())) {
            Map<String, byte[]> ordered = new LinkedHashMap<>(classes);
            return client.exchange(Protocol.CMD_INSTALL,
                    RawClient.installPayload(AID, appletClass, ordered, new byte[0]));
        }
    }

    private void assertServerAlive() {
        assertThat(server.isAlive()).as("server process alive; log:%n%s", server.log()).isTrue();
        assertThat(server.answersPing()).as("server answers a new client").isTrue();
    }
}
