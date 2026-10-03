package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.HelloWorldApplet;
import name.velikodniy.jcexpress.container.applets.LoopingApplet;
import name.velikodniy.jcexpress.container.applets.StateApplet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Map;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Regression tests for the audit finding "no-socket-timeouts-and-blocking-loop": neither side had read timeouts and
 * the server served one client at a time, so an applet in an endless loop, an idle connection or a client that
 * stalled mid-frame blocked the simulator (and the test JVM) forever.
 */
class TimeoutAndConcurrencyTest {

    private static final Duration GUARD = Duration.ofSeconds(30);
    private static final AID AID_STATE = AID.of(0xF0, 0x00, 0x00, 0x00, 0x52, 0x02);

    private LocalSimulatorServer server;
    private String previousTimeout;

    @BeforeEach
    void startServer() {
        server = LocalSimulatorServer.start();
        previousTimeout = System.getProperty(ContainerSession.TIMEOUT_PROPERTY);
    }

    @AfterEach
    void stopServer() {
        if (previousTimeout == null) {
            System.clearProperty(ContainerSession.TIMEOUT_PROPERTY);
        } else {
            System.setProperty(ContainerSession.TIMEOUT_PROPERTY, previousTimeout);
        }
        server.close();
    }

    @Test
    void appletInEndlessLoopFailsTheCallWhenTheTimeoutExpires() throws IOException {
        System.setProperty(ContainerSession.TIMEOUT_PROPERTY, "2");
        try (ContainerSession session = server.newSession()) {
            session.install(LoopingApplet.class);

            assertTimeoutPreemptively(GUARD, () -> assertThatThrownBy(() -> session.send(0x80, 0x10))
                    .isInstanceOf(UncheckedIOException.class)
                    .hasMessageContaining("2 s")
                    .hasRootCauseInstanceOf(SocketTimeoutException.class));
            assertThatThrownBy(() -> session.send(0x80, 0x00))
                    .as("a timed-out session is closed, not silently out of sync")
                    .isInstanceOf(UncheckedIOException.class)
                    .hasMessageContaining("closed");
        }
    }

    @Test
    void otherClientsAreServedWhileAnAppletIsStuck() throws IOException {
        System.setProperty(ContainerSession.TIMEOUT_PROPERTY, "1");
        try (ContainerSession stuck = server.newSession()) {
            stuck.install(LoopingApplet.class);
            assertTimeoutPreemptively(GUARD, () -> assertThatThrownBy(() -> stuck.send(0x80, 0x10))
                    .isInstanceOf(UncheckedIOException.class));
        }
        System.clearProperty(ContainerSession.TIMEOUT_PROPERTY);

        assertTimeoutPreemptively(GUARD, () -> assertHelloWorks(server));
    }

    @Test
    void idleOrStalledConnectionsDoNotBlockOtherClients() throws IOException {
        try (RawClient idle = new RawClient(server.port());
             RawClient stalled = new RawClient(server.port())) {
            stalled.send(Protocol.CMD_TRANSMIT, 100, new byte[]{0x00, (byte) 0xA4});

            assertTimeoutPreemptively(GUARD, () -> assertHelloWorks(server));
            assertThat(idle.exchange(Protocol.CMD_PING, new byte[0]).status()).isZero();
        }
    }

    @Test
    void concurrentSessionsHaveIndependentCards() throws IOException {
        assertTimeoutPreemptively(GUARD, () -> {
            try (ContainerSession first = server.newSession(); ContainerSession second = server.newSession()) {
                first.install(StateApplet.class, AID_STATE);
                second.install(StateApplet.class, AID_STATE);
                first.send(0x80, 0x01);
                first.send(0x80, 0x01);
                second.send(0x80, 0x01);

                assertThat(first.send(0x80, 0x02)).dataEquals(0x00, 0x02, 0x00, 0x02);
                assertThat(second.send(0x80, 0x02)).dataEquals(0x00, 0x01, 0x00, 0x01);
            }
        });
    }

    @Test
    void sessionsBeyondTheLimitAreRejectedWithAClearError() throws IOException {
        server.close();
        server = LocalSimulatorServer.start(Map.of("JCX_MAX_SESSIONS", "1"));

        try (ContainerSession first = server.newSession()) {
            first.install(HelloWorldApplet.class);
            assertTimeoutPreemptively(GUARD, () -> {
                try (ContainerSession second = server.newSession()) {
                    assertThatThrownBy(() -> second.install(HelloWorldApplet.class))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("Simulator busy");
                }
            });
        }
    }

    private static void assertHelloWorks(LocalSimulatorServer server) throws IOException {
        try (ContainerSession session = server.newSession()) {
            session.install(HelloWorldApplet.class);
            assertThat(session.send(0x80, 0x01)).isSuccess().dataAsString().isEqualTo("Hello");
        }
    }
}
