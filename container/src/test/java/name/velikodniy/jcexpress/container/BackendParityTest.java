package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SelectException;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.container.applets.RefusingSelectApplet;
import name.velikodniy.jcexpress.container.applets.StateApplet;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.fakes.AcceptAnySelectApplet;
import name.velikodniy.jcexpress.fakes.ProbeApplet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two jCardSim backends, {@link EmbeddedSession} (core) and {@link ContainerSession} (this module), honour the
 * same {@link SmartCardSession} contract. Written at integration of the core and container fix branches: each
 * branch fixed its own backend, and this test runs every scenario on both.
 *
 * <ul>
 *   <li>{@code le}: ISO/IEC 7816-4:2005 5.1, Ne = 256 is the short Le {@code '00'}, {@code NO_LE} sends no Le.</li>
 *   <li>SELECT: ISO/IEC 7816-4:2005 7.1.1, a failed SELECT leaves the previous selection in place, so the session
 *       reports it ({@link SelectException}) instead of letting later commands reach another applet.</li>
 *   <li>Logical channels: ISO/IEC 7816-4:2005 5.1.1.2, every channel has its own selection; jCardSim implements
 *       only the basic channel, so commands for other channels are refused instead of being delivered to the
 *       applet selected on the basic channel.</li>
 *   <li>Reset: "equivalent to removing and reinserting the card", applets are kept.</li>
 * </ul>
 */
class BackendParityTest {

    private static final AID AID_PROBE = AID.of(0xF0, 0x00, 0x00, 0x00, 0x54, 0x01);
    private static final AID AID_REFUSING = AID.of(0xF0, 0x00, 0x00, 0x00, 0x54, 0x02);
    private static final AID AID_ANY = AID.of(0xF0, 0x00, 0x00, 0x00, 0x54, 0x03);
    private static final AID AID_STATE = AID.of(0xF0, 0x00, 0x00, 0x00, 0x54, 0x04);
    private static final AID AID_UNKNOWN = AID.of(0xF0, 0x00, 0x00, 0x00, 0x54, 0x7F);

    private static LocalSimulatorServer server;
    private SmartCardSession session;

    @BeforeAll
    static void startServer() {
        server = LocalSimulatorServer.start();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    static Stream<Named<Supplier<SmartCardSession>>> backends() {
        return Stream.of(
                Named.of("embedded", EmbeddedSession::new),
                Named.of("container", BackendParityTest::containerSession));
    }

    private static SmartCardSession containerSession() {
        try {
            return server.newSession();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private SmartCardSession open(Supplier<SmartCardSession> backend) {
        session = backend.get();
        return session;
    }

    @ParameterizedTest
    @MethodSource("backends")
    void ne256IsTheShortLe00AndNoLeSendsNoLeField(Supplier<SmartCardSession> backend) {
        SmartCardSession card = open(backend);
        card.install(ProbeApplet.class, AID_PROBE);

        // ProbeApplet INS 11: CLA INS P1 P2 P3 as the applet sees them, then Ne from setOutgoing()
        assertThat(Hex.encode(card.send(0x80, 0x11, 0x00, 0x00, null, 256).data())).startsWith("8011000000" + "0100");
        // INS 13: min(P1P2, Ne) bytes, so no Le field (Ne = 0) gives no data
        assertThat(card.send(0x80, 0x13, 0x00, 0x20, null, SmartCardSession.NO_LE).data()).isEmpty();
        assertThat(card.send(0x80, 0x13, 0x00, 0x20, null, 5).data()).hasSize(5);
    }

    @ParameterizedTest
    @MethodSource("backends")
    void installOfAnAppletThatDeclinesSelectionThrowsSelectException(Supplier<SmartCardSession> backend) {
        SmartCardSession card = open(backend);

        assertThatThrownBy(() -> card.install(RefusingSelectApplet.class, AID_REFUSING))
                .isInstanceOf(SelectException.class)
                .satisfies(e -> assertThat(((SelectException) e).sw()).isEqualTo(0x6999));
    }

    @ParameterizedTest
    @MethodSource("backends")
    void selectOfAnAppletThatDeclinesSelectionThrowsSelectException(Supplier<SmartCardSession> backend) {
        SmartCardSession card = open(backend);
        card.install(StateApplet.class, AID_STATE);
        try {
            card.install(RefusingSelectApplet.class, AID_REFUSING);
        } catch (SelectException expected) {
            // covered above; the applet is installed nevertheless
        }

        assertThatThrownBy(() -> card.select(AID_REFUSING))
                .isInstanceOf(SelectException.class)
                .satisfies(e -> assertThat(((SelectException) e).sw()).isEqualTo(0x6999));
    }

    @ParameterizedTest
    @MethodSource("backends")
    void selectOfAnAidThatIsNotInstalledThrowsEvenWhenTheCurrentAppletAnswers9000(
            Supplier<SmartCardSession> backend) {
        SmartCardSession card = open(backend);
        card.install(AcceptAnySelectApplet.class, AID_ANY);

        assertThatThrownBy(() -> card.select(AID_UNKNOWN)).isInstanceOf(SelectException.class);
    }

    @ParameterizedTest
    @MethodSource("backends")
    void selectOfAnInstalledAppletSucceeds(Supplier<SmartCardSession> backend) {
        SmartCardSession card = open(backend);
        card.install(AcceptAnySelectApplet.class, AID_ANY);
        card.install(StateApplet.class, AID_STATE);

        card.select(AID_ANY);
        card.select(StateApplet.class);

        assertThat(card.send(0x80, 0x02).sw()).isEqualTo(0x9000);
    }

    @ParameterizedTest
    @MethodSource("backends")
    void commandsForAnotherLogicalChannelAreRefused(Supplier<SmartCardSession> backend) {
        SmartCardSession card = open(backend);
        card.install(ProbeApplet.class, AID_PROBE);

        assertThatThrownBy(() -> card.send(0x81, 0x11, 0x00, 0x00, null, 256))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> card.transmit(Hex.decode("4111000000")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("backends")
    void manageChannelIsRefused(Supplier<SmartCardSession> backend) {
        SmartCardSession card = open(backend);
        card.install(ProbeApplet.class, AID_PROBE);

        assertThatThrownBy(() -> card.transmit(Hex.decode("0070000001")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** CLA '0C' (secure messaging, ISO/IEC 7816-4:2005 Table 2) and '80' still address the basic channel. */
    @ParameterizedTest
    @ValueSource(ints = {0x00, 0x0C, 0x80, 0x84})
    void basicChannelClassBytesAreDelivered(int cla) {
        for (Named<Supplier<SmartCardSession>> backend : backends().toList()) {
            try (SmartCardSession card = backend.getPayload().get()) {
                card.install(ProbeApplet.class, AID_PROBE);

                APDUResponse response = card.send(cla, 0x11, 0x00, 0x00, null, 256);

                assertThat(response.sw()).as(backend.getName()).isEqualTo(0x9000);
                assertThat(response.data()[0] & 0xFF).as(backend.getName()).isEqualTo(cla);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("backends")
    void resetKeepsAppletsAndDeselects(Supplier<SmartCardSession> backend) {
        SmartCardSession card = open(backend);
        card.install(StateApplet.class, AID_STATE);
        card.send(0x80, 0x01);

        card.reset();

        assertThat(card.send(0x80, 0x02).sw()).isNotEqualTo(0x9000);
        card.select(AID_STATE);
        assertThat(card.send(0x80, 0x02).data()).containsExactly(0x00, 0x01, 0x00, 0x00);
    }

    @ParameterizedTest
    @MethodSource("backends")
    void installingTheSameAidTwiceIsRejected(Supplier<SmartCardSession> backend) {
        SmartCardSession card = open(backend);
        card.install(StateApplet.class, AID_STATE);

        assertThatThrownBy(() -> card.install(StateApplet.class, AID_STATE))
                .isInstanceOf(IllegalStateException.class);
    }
}
