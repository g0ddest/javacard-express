package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.container.applets.StateApplet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ContainerSession#reset()} follows the {@code SmartCardSession.reset()} contract, "equivalent to removing
 * and reinserting the card": the server used to wipe the card (jCardSim {@code resetRuntime}). After a card reset the
 * Java Card runtime keeps installed applets and their persistent objects, clears transient arrays created with
 * {@code JCSystem.CLEAR_ON_RESET} and has no applet selected.
 */
class CardResetTest {

    private static final AID AID_STATE = AID.of(0xF0, 0x00, 0x00, 0x00, 0x52, 0x01);

    private static LocalSimulatorServer server;
    private ContainerSession session;

    @BeforeAll
    static void startServer() {
        server = LocalSimulatorServer.start();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void installApplet() throws IOException {
        session = server.newSession();
        session.install(StateApplet.class, AID_STATE);
        session.send(0x80, 0x01);
        session.send(0x80, 0x01);
    }

    @AfterEach
    void disconnect() {
        session.close();
    }

    @Test
    void appletAndPersistentStateSurviveAReset() {
        session.reset();
        session.select(AID_STATE);

        assertThat(session.send(0x80, 0x02)).isSuccess().dataEquals(0x00, 0x02, 0x00, 0x00);
    }

    @Test
    void resetClearsClearOnResetMemoryButNotPersistentMemory() {
        assertThat(session.send(0x80, 0x02)).dataEquals(0x00, 0x02, 0x00, 0x02);

        session.reset();
        session.select(StateApplet.class);

        assertThat(session.send(0x80, 0x02)).dataEquals(0x00, 0x02, 0x00, 0x00);
    }

    @Test
    void noAppletIsSelectedAfterAReset() {
        session.reset();

        assertThat(session.send(0x80, 0x02).sw()).isNotEqualTo(0x9000);
    }

    @Test
    void installingUnderAnAidThatIsInUseIsRejected() {
        session.reset();

        assertThatThrownBy(() -> session.install(StateApplet.class, AID_STATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already installed");
    }

    @Test
    void aNewSessionStartsWithABlankCard() throws IOException {
        try (ContainerSession other = server.newSession()) {
            other.install(StateApplet.class, AID_STATE);

            assertThat(other.send(0x80, 0x02)).dataEquals(0x00, 0x00, 0x00, 0x00);
        }
    }
}
