package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.HelloWorldApplet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Access token of the simulator server (audit finding "unauth-rce-bind-all-interfaces-root"): the server runs the
 * class files its clients send, so a server started with {@code JCX_TOKEN} serves only connections whose first
 * request is a HELLO with that token; anything else is answered with an error and disconnected.
 */
class AccessTokenTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    private static LocalSimulatorServer server;

    @BeforeAll
    static void startServer() {
        server = LocalSimulatorServer.start(Map.of("JCX_TOKEN", TOKEN));
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @Test
    void sessionPresentingTheTokenWorks() throws IOException {
        try (ContainerSession session = server.newSession()) {
            session.install(HelloWorldApplet.class);

            assertThat(session.send(0x80, 0x01)).isSuccess().dataAsString().isEqualTo("Hello");
        }
    }

    @Test
    void commandsWithoutTokenAreRefusedAndTheConnectionIsClosed() throws IOException {
        try (RawClient client = new RawClient(server.port())) {
            RawClient.Reply reply = client.exchange(Protocol.CMD_PING, new byte[0]);

            assertThat(reply.status()).isEqualTo(1);
            assertThat(reply.text()).contains("type: java.lang.SecurityException").contains("Access token required");
            assertThat(client.closedByServer()).isTrue();
        }
    }

    @Test
    void wrongTokenIsRefusedAndTheConnectionIsClosed() throws IOException {
        try (RawClient client = new RawClient(server.port())) {
            RawClient.Reply reply = client.exchange(Protocol.CMD_HELLO,
                    "0123456789abcdef0123456789abcdee".getBytes(StandardCharsets.UTF_8));

            assertThat(reply.status()).isEqualTo(1);
            assertThat(reply.text()).contains("Wrong access token");
            assertThat(client.closedByServer()).isTrue();
        }
    }

    @Test
    void sessionWithoutTheTokenFailsWithASecurityException() throws IOException {
        try (ContainerSession session = new ContainerSession("127.0.0.1", server.port(), null)) {
            assertThatThrownBy(() -> session.install(HelloWorldApplet.class))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Access token required");
        }
    }

    @Test
    void tokenIsNeverLogged() {
        assertThat(server.awaitLog("access token required")).contains("access token required").doesNotContain(TOKEN);
    }

    @Test
    void helloIsAcceptedByServersWithoutToken() throws IOException {
        try (LocalSimulatorServer open = LocalSimulatorServer.start();
             ContainerSession session = new ContainerSession("127.0.0.1", open.port(), null,
                     ContainerSession.DEFAULT_TIMEOUT, TOKEN)) {
            session.install(HelloWorldApplet.class);

            assertThat(session.send(0x80, 0x01)).isSuccess();
        }
    }
}
