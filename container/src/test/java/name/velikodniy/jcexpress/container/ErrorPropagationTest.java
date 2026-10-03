package name.velikodniy.jcexpress.container;

import javacard.framework.CardRuntimeException;
import javacard.framework.ISOException;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.HelloWorldApplet;
import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.container.applets.BaseApplet;
import name.velikodniy.jcexpress.container.applets.DerivedApplet;
import name.velikodniy.jcexpress.container.applets.Helper;
import name.velikodniy.jcexpress.container.applets.HelperApplet;
import name.velikodniy.jcexpress.container.applets.ThrowingInstallApplet;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Regression tests for the audit finding "unhelpful-error-propagation": the server used to send only
 * {@code getMessage()} (or the literal "Unknown error"), so an applet whose {@code install()} failed surfaced as
 * {@code IOException: Server error: Unknown error}, without exception type, reason code or cause.
 */
class ErrorPropagationTest {

    private static LocalSimulatorServer server;

    @BeforeAll
    static void startServer() {
        server = LocalSimulatorServer.start();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @Test
    void failingInstallRaisesTheSameInstallExceptionAsEmbeddedMode() throws IOException {
        Throwable embedded = catchThrowable(() -> {
            try (EmbeddedSession session = new EmbeddedSession(false)) {
                session.install(ThrowingInstallApplet.class);
            }
        });
        assertThat(embedded).isInstanceOf(InstallException.class)
                .hasMessageContaining("ISOException with reason 6A80");

        try (ContainerSession session = server.newSession()) {
            Throwable remote = catchThrowable(() -> session.install(ThrowingInstallApplet.class));

            assertThat(remote).isInstanceOf(InstallException.class);
            assertThat(((InstallException) remote).reason()).isEqualTo(((InstallException) embedded).reason());
            assertThat(remote.getCause()).isInstanceOf(ISOException.class);
            assertThat(((CardRuntimeException) remote.getCause()).getReason()).isEqualTo((short) 0x6A80);
            assertThat(remote.getCause().getCause())
                    .isInstanceOf(SimulatorException.class)
                    .hasMessageContaining("javacard.framework.ISOException")
                    .hasMessageContaining("0x6A80");
        }
    }

    @Test
    void sessionIsStillUsableAfterAFailedCommand() throws IOException {
        try (ContainerSession session = server.newSession()) {
            catchThrowable(() -> session.install(ThrowingInstallApplet.class));

            session.install(HelloWorldApplet.class);

            assertThat(session.send(0x80, 0x01).sw()).isEqualTo(0x9000);
        }
    }

    @Test
    void errorReportNamesTheExceptionTypeAndItsCauses() throws IOException {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(DerivedApplet.class.getName(), RawClient.classFile(DerivedApplet.class));

        try (RawClient client = new RawClient(server.port())) {
            RawClient.Reply reply = client.exchange(Protocol.CMD_INSTALL, RawClient.installPayload(
                    AID.of(0xF0, 0, 0, 0, 0x0E, 0x01).toBytes(), DerivedApplet.class.getName(), classes,
                    new byte[0]));

            assertThat(reply.status()).isEqualTo(1);
            assertThat(reply.text())
                    .contains("type: java.lang.NoClassDefFoundError")
                    .contains("message: " + BaseApplet.class.getName().replace('.', '/'))
                    .contains("cause: java.lang.ClassNotFoundException: " + BaseApplet.class.getName());
        }
    }

    @Test
    void classesTheAppletNeededButWereNotSentAreNamed() throws IOException {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(HelperApplet.class.getName(), RawClient.classFile(HelperApplet.class));

        try (RawClient client = new RawClient(server.port())) {
            RawClient.Reply reply = client.exchange(Protocol.CMD_INSTALL, RawClient.installPayload(
                    AID.of(0xF0, 0, 0, 0, 0x0E, 0x02).toBytes(), HelperApplet.class.getName(), classes,
                    new byte[0]));

            assertThat(reply.status()).isEqualTo(1);
            assertThat(reply.text()).contains("missing-class: " + Helper.class.getName());
            assertThat(RemoteError.parse(reply.payload()).describe())
                    .contains("classes that were not sent to the simulator: " + Helper.class.getName());
        }
    }

    @Test
    void malformedRawApduIsRejectedLikeEmbeddedMode() throws IOException {
        byte[] tooShort = {(byte) 0x80, 0x01};
        Throwable embedded = catchThrowable(() -> {
            try (EmbeddedSession session = new EmbeddedSession(false)) {
                session.transmit(tooShort);
            }
        });

        try (ContainerSession session = server.newSession()) {
            assertThatThrownBy(() -> session.transmit(tooShort)).isInstanceOf(embedded.getClass());
        }
    }
}
