package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.container.applets.DerivedApplet;
import name.velikodniy.jcexpress.container.applets.HelperApplet;
import name.velikodniy.jcexpress.container.applets.InterfaceApplet;
import name.velikodniy.jcexpress.container.applets.NestedApplet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HexFormat;
import java.util.Map;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for the audit finding "class-closure-not-shipped": {@link ContainerSession} used to send only
 * the applet class and its directly declared nested classes, so applets with a user superclass, a helper class, an
 * interface or a class nested two levels deep failed in container mode although they work in embedded mode.
 *
 * <p>The server runs in its own JVM whose class path holds only the server and jCardSim, so every user class must
 * arrive through the install request.</p>
 */
class ClassClosureTest {

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
    void connect() throws IOException {
        session = server.newSession();
    }

    @AfterEach
    void disconnect() {
        session.close();
    }

    @Test
    void appletWithUserDefinedSuperclassRuns() {
        session.install(DerivedApplet.class);

        assertResponse(session.send(0x80, 0x00), "1234");
    }

    @Test
    void appletWithTopLevelHelperClassRuns() {
        session.install(HelperApplet.class);

        assertResponse(session.send(0x80, 0x00), "112233");
    }

    @Test
    void appletUsingAnInterfaceAndItsImplementationRuns() {
        session.install(InterfaceApplet.class);

        assertResponse(session.send(0x80, 0x00), "0010");
        assertResponse(session.send(0x80, 0x00), "0020");
    }

    @Test
    void appletUsingAClassNestedTwoLevelsDeepRuns() {
        session.install(NestedApplet.class);

        assertResponse(session.send(0x80, 0x00), "0A0B");
    }

    /**
     * An applet declared as a static nested class of the test class is a common pattern. Its class file names the
     * test class only in the {@code InnerClasses}/{@code NestHost} attributes (JVMS 4.7.6, 4.7.28), which the JVM
     * does not resolve to load or run the applet; following them used to ship the whole test class path
     * (JUnit, Testcontainers, AssertJ: ~9000 classes, 34 MB, over the 16 MB request limit).
     */
    @Test
    void appletNestedInATestClassShipsOnlyItsOwnClosure() throws IOException {
        Map<String, byte[]> classes = AppletClasses.of(NestedInTestApplet.class);

        assertThat(classes.keySet()).containsExactly(NestedInTestApplet.class.getName());
    }

    @Test
    void appletNestedInATestClassRuns() {
        session.install(NestedInTestApplet.class);

        assertResponse(session.send(0x80, 0x00), "C0DE");
    }

    private static void assertResponse(APDUResponse response, String hexData) {
        assertThat(response.sw()).as("SW").isEqualTo(0x9000);
        assertThat(HexFormat.of().withUpperCase().formatHex(response.data())).isEqualTo(hexData);
    }

    /** Applet nested in the test class; INS 00 returns {@code C0DE}. */
    public static class NestedInTestApplet extends Applet {

        /**
         * Applet installation entry point.
         *
         * @param bArray  install parameters
         * @param bOffset offset
         * @param bLength length
         */
        public static void install(byte[] bArray, short bOffset, byte bLength) {
            new NestedInTestApplet().register();
        }

        @Override
        public void process(APDU apdu) {
            if (selectingApplet()) {
                return;
            }
            byte[] buffer = apdu.getBuffer();
            Util.setShort(buffer, (short) 0, (short) 0xC0DE);
            apdu.setOutgoingAndSend((short) 0, (short) 2);
        }
    }
}
