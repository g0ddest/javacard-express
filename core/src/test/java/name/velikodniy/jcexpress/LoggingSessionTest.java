package name.velikodniy.jcexpress;

import javacard.framework.Applet;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link LoggingSession}.
 */
class LoggingSessionTest {

    /**
     * Minimal stub that echoes back predictable responses.
     */
    static class StubSession implements SmartCardSession {
        private final List<int[]> sentCommands = new ArrayList<>();
        private boolean installed = false;
        private boolean selected = false;
        private boolean wasReset = false;

        List<int[]> sentCommands() { return sentCommands; }
        boolean wasInstalled() { return installed; }
        boolean wasSelected() { return selected; }
        boolean wasReset() { return wasReset; }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
            sentCommands.add(new int[]{cla, ins, p1, p2});
            // Return different data based on INS for filtering tests
            if (ins == 0xA4) {
                return new APDUResponse(Hex.decode("A000"), 0x9000);
            }
            return new APDUResponse(Hex.decode("0102"), 0x9000);
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
            return send(cla, ins, p1, p2, data, -1);
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2) {
            return send(cla, ins, p1, p2, null, -1);
        }

        @Override
        public APDUResponse send(int cla, int ins) {
            return send(cla, ins, 0, 0, null, -1);
        }

        @Override
        public byte[] transmit(byte[] rawApdu) {
            return new byte[]{0x01, 0x02, (byte) 0x90, 0x00};
        }

        @Override public void install(Class<? extends Applet> c) { installed = true; }
        @Override public void install(Class<? extends Applet> c, AID a) { installed = true; }
        @Override public void install(Class<? extends Applet> c, AID a, byte[] p) { installed = true; }
        @Override public void select(Class<? extends Applet> c) { selected = true; }
        @Override public void select(AID a) { selected = true; }
        @Override public void reset() { wasReset = true; }
        @Override public void close() {}
    }

    @Test
    void shouldDelegateSendToWrappedSession() {
        StubSession stub = new StubSession();
        LoggingSession logged = LoggingSession.wrap(stub);

        APDUResponse r = logged.send(0x80, 0x01, 0x00, 0x00);

        assertThat(r.isSuccess()).isTrue();
        assertThat(stub.sentCommands()).hasSize(1);
        assertThat(stub.sentCommands().get(0)[1]).isEqualTo(0x01); // INS
    }

    @Test
    void shouldRecordEntries() {
        StubSession stub = new StubSession();
        LoggingSession logged = LoggingSession.wrap(stub);

        logged.send(0x00, 0xA4, 0x04, 0x00);
        logged.send(0x80, 0x01);

        assertThat(logged.entries()).hasSize(2);
        assertThat(logged.entries().get(0).ins()).isEqualTo(0xA4);
        assertThat(logged.entries().get(1).ins()).isEqualTo(0x01);
    }

    @Test
    void shouldFilterByIns() {
        StubSession stub = new StubSession();
        LoggingSession logged = LoggingSession.wrap(stub);

        logged.send(0x00, 0xA4, 0x04, 0x00);
        logged.send(0x80, 0x01);
        logged.send(0x00, 0xA4, 0x04, 0x00);

        List<APDULogEntry> selects = logged.entries(0xA4);
        assertThat(selects).hasSize(2);

        List<APDULogEntry> customs = logged.entries(0x01);
        assertThat(customs).hasSize(1);
    }

    @Test
    void entryCountShouldTrack() {
        StubSession stub = new StubSession();
        LoggingSession logged = LoggingSession.wrap(stub);

        assertThat(logged.entryCount()).isZero();

        logged.send(0x80, 0x01);
        assertThat(logged.entryCount()).isEqualTo(1);

        logged.send(0x80, 0x02);
        assertThat(logged.entryCount()).isEqualTo(2);
    }

    @Test
    void lastEntryShouldReturnMostRecent() {
        StubSession stub = new StubSession();
        LoggingSession logged = LoggingSession.wrap(stub);

        logged.send(0x80, 0x01);
        logged.send(0x80, 0x02);

        assertThat(logged.lastEntry().ins()).isEqualTo(0x02);
    }

    @Test
    void lastEntryShouldThrowWhenEmpty() {
        StubSession stub = new StubSession();
        LoggingSession logged = LoggingSession.wrap(stub);

        assertThatThrownBy(logged::lastEntry)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void clearShouldRemoveEntries() {
        StubSession stub = new StubSession();
        LoggingSession logged = LoggingSession.wrap(stub);

        logged.send(0x80, 0x01);
        logged.send(0x80, 0x02);
        assertThat(logged.entryCount()).isEqualTo(2);

        logged.clear();
        assertThat(logged.entryCount()).isZero();
        assertThat(logged.entries()).isEmpty();
    }

    @Test
    void dumpShouldFormatEntries() {
        StubSession stub = new StubSession();
        LoggingSession logged = LoggingSession.wrap(stub);

        logged.send(0x80, 0x01, 0x00, 0x00);

        String dump = logged.dump();

        assertThat(dump).contains("[0]");
        assertThat(dump).contains(">>");
        assertThat(dump).contains("<<");
        assertThat(dump).contains("9000");
    }

    @Test
    void shouldDelegateLifecycleMethods() {
        StubSession stub = new StubSession();
        LoggingSession logged = LoggingSession.wrap(stub);

        logged.install(HelloWorldApplet.class);
        assertThat(stub.wasInstalled()).isTrue();

        logged.select(AID.fromHex("A000000003"));
        assertThat(stub.wasSelected()).isTrue();

        logged.reset();
        assertThat(stub.wasReset()).isTrue();
    }

    @Test
    void transmitShouldRecordEntry() {
        StubSession stub = new StubSession();
        LoggingSession logged = LoggingSession.wrap(stub);

        byte[] rawApdu = Hex.decode("00A40400");
        logged.transmit(rawApdu);

        assertThat(logged.entryCount()).isEqualTo(1);
        assertThat(logged.lastEntry().ins()).isEqualTo(0xA4);
    }

    @Test
    void delegateShouldReturnUnderlyingSession() {
        StubSession stub = new StubSession();
        LoggingSession logged = LoggingSession.wrap(stub);

        assertThat(logged.delegate()).isSameAs(stub);
    }
}
