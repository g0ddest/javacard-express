package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.fakes.ThrowingApplet;
import name.velikodniy.jcexpress.fakes.Transcripts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static name.velikodniy.jcexpress.fakes.Transcripts.withoutTimes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code logged(true)} and {@code -Djcx.log=true} print one line per transcript line to standard output, without the
 * header line java.util.logging's default format puts before every record, as long as the logger
 * {@code name.velikodniy.jcexpress} has no handler of its own; a handler the user gives it receives the lines
 * instead, and the records of other loggers keep their way.
 */
class LoggingConsoleTest {

    private static final Logger LOGGER = Logger.getLogger("name.velikodniy.jcexpress");

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private Handler[] originalHandlers;
    private boolean originalUseParentHandlers;

    @BeforeEach
    void unconfiguredLogger() {
        originalOut = System.out;
        originalHandlers = LOGGER.getHandlers();
        originalUseParentHandlers = LOGGER.getUseParentHandlers();
        for (Handler handler : originalHandlers) {
            LOGGER.removeHandler(handler);
        }
        LOGGER.setUseParentHandlers(true);
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restore() {
        System.setOut(originalOut);
        for (Handler handler : LOGGER.getHandlers()) {
            LOGGER.removeHandler(handler);
        }
        for (Handler handler : originalHandlers) {
            LOGGER.addHandler(handler);
        }
        LOGGER.setUseParentHandlers(originalUseParentHandlers);
    }

    private List<String> printed() {
        return out.toString(StandardCharsets.UTF_8).lines().toList();
    }

    @Test
    void everyTranscriptLineIsOneLineOnStandardOutput() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            card.logged(true).send(0x80, 0x01, 0x00, 0x00);
        }

        assertThat(printed()).satisfiesExactly(
                line -> assertThat(line).isEqualTo("[JCX] C: 80010000"),
                line -> assertThat(line).matches("\\[JCX] R: 6986" + Transcripts.TIME));
        assertThat(LOGGER.getUseParentHandlers()).isFalse();
    }

    @Test
    void aHandlerTheUserGaveTheLoggerReceivesTheLinesInstead() {
        List<String> received = new CopyOnWriteArrayList<>();
        LOGGER.addHandler(collecting(received));

        try (EmbeddedSession card = new EmbeddedSession()) {
            card.logged(true).send(0x80, 0x01, 0x00, 0x00);
        }

        assertThat(withoutTimes(received)).containsExactly("[JCX] C: 80010000", "[JCX] R: 6986");
        assertThat(printed()).isEmpty();
        assertThat(LOGGER.getHandlers()).hasSize(1);
        assertThat(LOGGER.getUseParentHandlers()).isTrue();
    }

    @Test
    void recordsOfOtherLoggersBelowItKeepTheirWay() {
        Logger root = Logger.getLogger("");
        List<String> received = new CopyOnWriteArrayList<>();
        Handler rootHandler = collecting(received);
        root.addHandler(rootHandler);
        try {
            try (EmbeddedSession card = new EmbeddedSession()) {
                card.logged(true).send(0x80, 0x01, 0x00, 0x00);
            }
            Logger.getLogger("name.velikodniy.jcexpress.livecard.LiveCard").warning("unverified CAP files");
        } finally {
            root.removeHandler(rootHandler);
        }

        assertThat(received).containsExactly("unverified CAP files");
        assertThat(withoutTimes(printed())).containsExactly("[JCX] C: 80010000", "[JCX] R: 6986");
    }

    @Test
    void aNoteTheSessionRecordsDuringAnExchangeIsPrintedAfterIt() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            card.install(ThrowingApplet.class, AID.fromHex("F0000000010101"));
            card.logged(true).send(0x80, 0x01, 0x00, 0x00);
        }

        List<String> lines = withoutTimes(printed());
        assertThat(lines).contains("[JCX] C: 80010000", "[JCX] R: 6F00")
                .anySatisfy(line -> assertThat(line).startsWith("[JCX] # applet threw"
                        + " java.lang.ArrayIndexOutOfBoundsException"));
        assertThat(lines.indexOf("[JCX] R: 6F00") + 1).isEqualTo(indexOfNote(lines));
    }

    private static int indexOfNote(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("[JCX] # applet threw")) {
                return i;
            }
        }
        return -1;
    }

    private static Handler collecting(List<String> messages) {
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                messages.add(logRecord.getMessage());
            }

            @Override
            public void flush() {
                // nothing buffered
            }

            @Override
            public void close() {
                // nothing to release
            }
        };
        handler.setLevel(Level.ALL);
        return handler;
    }
}
