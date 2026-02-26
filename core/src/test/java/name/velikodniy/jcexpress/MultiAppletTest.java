package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

/**
 * Demonstrates multi-applet scenarios: installing multiple applets
 * in the same session, switching between them, and verifying data isolation.
 */
class MultiAppletTest {

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
    void shouldInstallTwoAppletsWithDifferentAids() {
        AID helloAid = AID.of(0xF0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06);
        AID counterAid = AID.of(0xF0, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F);

        session.install(HelloWorldApplet.class, helloAid);
        session.install(CounterApplet.class, counterAid);

        // Last installed is selected — CounterApplet
        APDUResponse counter = session.send(0x80, 0x02);
        assertThat(counter).isSuccess();
        assertThat(counter).hasDataLength(4);
    }

    @Test
    void shouldSwitchBetweenApplets() {
        AID helloAid = AID.of(0xF0, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06);
        AID counterAid = AID.of(0xF0, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F);

        session.install(HelloWorldApplet.class, helloAid);
        session.install(CounterApplet.class, counterAid);

        // Select HelloWorldApplet
        session.select(helloAid);
        APDUResponse hello = session.send(0x80, 0x01);
        assertThat(hello).isSuccess();
        assertThat(hello).dataAsString().isEqualTo("Hello");

        // Switch back to CounterApplet
        session.select(counterAid);
        APDUResponse counter = session.send(0x80, 0x02);
        assertThat(counter).isSuccess();
        assertThat(counter).hasDataLength(4);
    }

    @Test
    void shouldMaintainDataIsolationBetweenApplets() {
        AID counter1Aid = AID.of(0xF0, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01);
        AID counter2Aid = AID.of(0xF0, 0x02, 0x02, 0x02, 0x02, 0x02, 0x02);

        session.install(CounterApplet.class, counter1Aid);

        // Increment counter1 three times
        session.send(0x80, 0x01);
        session.send(0x80, 0x01);
        APDUResponse c1 = session.send(0x80, 0x01);
        assertThat(c1).isSuccess();
        assertThat(c1).dataEquals(0x00, 0x00, 0x00, 0x03);

        // Install counter2 — fresh instance, counter = 0
        session.install(CounterApplet.class, counter2Aid);
        APDUResponse c2 = session.send(0x80, 0x02);
        assertThat(c2).isSuccess();
        assertThat(c2).dataEquals(0x00, 0x00, 0x00, 0x00);

        // Switch back to counter1 — should still be 3
        session.select(counter1Aid);
        APDUResponse c1Again = session.send(0x80, 0x02);
        assertThat(c1Again).isSuccess();
        assertThat(c1Again).dataEquals(0x00, 0x00, 0x00, 0x03);
    }

    @Test
    void shouldSelectByClassAfterAutoAid() {
        session.install(HelloWorldApplet.class);
        session.install(CounterApplet.class);

        // Select by class name
        session.select(HelloWorldApplet.class);
        APDUResponse hello = session.send(0x80, 0x01);
        assertThat(hello).isSuccess();
        assertThat(hello).dataAsString().isEqualTo("Hello");

        session.select(CounterApplet.class);
        APDUResponse counter = session.send(0x80, 0x02);
        assertThat(counter).isSuccess();
    }
}
