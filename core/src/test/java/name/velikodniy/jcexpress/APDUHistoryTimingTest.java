package name.velikodniy.jcexpress;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The history records when a command was sent and how long its exchange took, keeps both through its entries and
 * views, shows the time in its transcript, and names its newest exchange.
 */
class APDUHistoryTimingTest {

    private static final byte[] COMMAND = Hex.decode("8052000002");
    private static final byte[] RESPONSE = Hex.decode("00649000");

    @Test
    void anExchangeIsRecordedWithItsSendTimeAndTheTimeOfTheTransmission() {
        APDUHistory history = new APDUHistory();
        AtomicLong transmissionStarted = new AtomicLong();

        byte[] response = history.exchange(COMMAND, () -> {
            transmissionStarted.set(System.currentTimeMillis());
            pause(25);
            return RESPONSE;
        });

        APDULogEntry entry = history.last();
        assertThat(response).isEqualTo(RESPONSE);
        assertThat(entry.timestampMs()).isLessThanOrEqualTo(transmissionStarted.get());
        assertThat(entry.duration()).isGreaterThanOrEqualTo(Duration.ofMillis(25));
    }

    @Test
    void aFailedTransmissionIsNotRecordedAndKeepsItsException() {
        APDUHistory history = new APDUHistory();

        assertThatThrownBy(() -> history.exchange(COMMAND, () -> {
            throw new IOException("reader gone");
        })).isInstanceOf(IOException.class).hasMessage("reader gone");
        assertThat(history.entries()).isEmpty();
    }

    @Test
    void entriesViewsAndTheTranscriptKeepTheDuration() {
        APDUHistory history = new APDUHistory();
        history.note("before the view");
        APDUHistory view = history.since(history.position());

        history.record(COMMAND, RESPONSE, 1_000L, Duration.ofMillis(12).plusNanos(300_000));

        assertThat(history.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.timestampMs()).isEqualTo(1_000L);
            assertThat(entry.duration()).isEqualTo(Duration.ofMillis(12).plusNanos(300_000));
        });
        assertThat(view.entries()).singleElement()
                .satisfies(entry -> assertThat(entry.duration()).isEqualTo(Duration.ofNanos(12_300_000)));
        assertThat(view.transcript()).isEqualTo("C: 8052000002\nR: 00649000  (12.3 ms)\n");
    }

    @Test
    void anExchangeRecordedWithoutATimeHasNoDurationAndNoTimeInTheTranscript() {
        APDUHistory history = new APDUHistory();

        history.record(COMMAND, RESPONSE);

        assertThat(history.last().duration()).isNull();
        assertThat(history.transcript()).isEqualTo("C: 8052000002\nR: 00649000\n");
    }

    @Test
    void lastIsTheNewestExchangeNotesLeftAside() {
        APDUHistory history = new APDUHistory();
        history.record(Hex.decode("8001000000"), Hex.decode("9000"), 1L, Duration.ofMillis(1));
        history.record(COMMAND, RESPONSE, 2L, Duration.ofMillis(2));
        history.note("card reset");

        assertThat(history.last().command()).isEqualTo(COMMAND);
        assertThat(history.last().response().sw()).isEqualTo(0x9000);
        assertThat(history.last().duration()).isEqualTo(Duration.ofMillis(2));
    }

    @Test
    void lastOfAViewIsItsNewestExchange() {
        APDUHistory history = new APDUHistory();
        history.record(COMMAND, RESPONSE, 1L, Duration.ofMillis(1));
        APDUHistory view = history.since(history.position());

        assertThatThrownBy(view::last).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no exchange");

        history.record(Hex.decode("8001000000"), Hex.decode("9000"), 2L, Duration.ofMillis(3));
        assertThat(view.last().duration()).isEqualTo(Duration.ofMillis(3));
    }

    @Test
    void lastOfAnEmptyHistoryExplainsItself() {
        APDUHistory history = new APDUHistory();
        history.note("install com.example.WalletApplet as F0");

        assertThatThrownBy(history::last).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no exchange");
        assertThatThrownBy(APDUHistory.none()::last).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keeps no history");
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
