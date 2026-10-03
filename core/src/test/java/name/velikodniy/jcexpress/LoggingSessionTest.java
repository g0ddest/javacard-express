package name.velikodniy.jcexpress;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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

    /** The dump uses the transcript format of the whole toolkit: C: command, R: response. */
    @Test
    void dumpShouldFormatEntries() {
        StubSession stub = new StubSession();
        LoggingSession logged = LoggingSession.wrap(stub);

        logged.send(0x80, 0x01, 0x00, 0x00);
        logged.send(0x00, 0xA4, 0x04, 0x00);

        assertThat(logged.dump()).isEqualTo("""
                C: 80010000
                R: 01029000
                C: 00A40400
                R: A0009000
                """);
    }

    /** SELECTs and installs made through the logging session are logged like every other command. */
    @Test
    void selectsAndInstallsAreLogged() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            LoggingSession logged = LoggingSession.wrap(card);
            AID aid = AID.fromHex("F000000001");

            logged.install(HelloWorldApplet.class, aid);
            logged.send(0x80, 0x01);
            logged.select(aid);

            assertThat(logged.entries()).extracting(APDULogEntry::ins).containsExactly(0xA4, 0x01, 0xA4);
            assertThat(logged.dump()).isEqualTo("""
                    C: 00A4040005F00000000100
                    R: 9000
                    C: 80010000
                    R: 48656C6C6F9000
                    C: 00A4040005F00000000100
                    R: 9000
                    """);
        }
    }

    @Test
    void printedLinesUseTheTranscriptFormat() {
        Logger logger = Logger.getLogger("name.velikodniy.jcexpress");
        List<String> lines = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            LoggingSession.wrap(new StubSession(), true).send(0x80, 0x01);
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(lines).containsExactly("[JCX] C: 80010000", "[JCX] R: 01029000");
    }

    @Test
    void deleteIsForwardedToTheWrappedSession() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            LoggingSession logged = LoggingSession.wrap(card);
            AID aid = AID.fromHex("F000000001");
            logged.install(HelloWorldApplet.class, aid);

            logged.delete(aid);
            logged.install(HelloWorldApplet.class, aid);

            assertThat(card.history().transcript()).contains("# delete F000000001");
        }
        assertThatThrownBy(() -> LoggingSession.wrap(new StubSession()).delete(AID.fromHex("F000000001")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void historyIsTheHistoryOfTheWrappedSession() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            assertThat(LoggingSession.wrap(card).history()).isSameAs(card.history());
        }
        assertThat(LoggingSession.wrap(new StubSession()).history()).isSameAs(APDUHistory.none());
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
