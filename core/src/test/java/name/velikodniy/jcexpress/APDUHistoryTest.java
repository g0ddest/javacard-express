package name.velikodniy.jcexpress;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link APDUHistory}: a bounded record of exchanges and notes in the transcript format ({@code C:}, {@code R:},
 * {@code #}).
 */
class APDUHistoryTest {

    @Test
    void exchangesAndNotesAreKeptInOrderInTheTranscriptFormat() {
        APDUHistory history = new APDUHistory();

        history.note("install com.example.Applet as F000000001");
        history.record(Hex.decode("00A4040005F00000000100"), Hex.decode("9000"));
        history.record(Hex.decode("80010000"), Hex.decode("48656C6C6F9000"));

        assertThat(history.transcript()).isEqualTo("""
                # install com.example.Applet as F000000001
                C: 00A4040005F00000000100
                R: 9000
                C: 80010000
                R: 48656C6C6F9000
                """);
    }

    @Test
    void entriesAreTheExchanges() {
        APDUHistory history = new APDUHistory();
        history.note("card reset");
        history.record(Hex.decode("80010000"), Hex.decode("01029000"));

        List<APDULogEntry> entries = history.entries();

        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.commandHex()).isEqualTo("80 01 00 00");
            assertThat(entry.response()).isEqualTo(APDUResponse.fromHex("01029000"));
            assertThat(entry.response().inReplyTo(entry.command())).isEqualTo(entry.response());
        });
    }

    @Test
    void onlyTheMostRecentEntriesAreKeptAndTheTranscriptSaysSo() {
        APDUHistory history = new APDUHistory(2);

        history.record(Hex.decode("80010000"), Hex.decode("9000"));
        history.note("card reset");
        history.record(Hex.decode("80020000"), Hex.decode("6D00"));

        assertThat(history.transcript()).isEqualTo("""
                # 1 earlier entry not kept (the history keeps the last 2)
                # card reset
                C: 80020000
                R: 6D00
                """);
        assertThat(history.entries()).extracting(APDULogEntry::commandHex).containsExactly("80 02 00 00");
    }

    @Test
    void theBytesAreCopied() {
        APDUHistory history = new APDUHistory();
        byte[] command = Hex.decode("80010000");
        byte[] response = Hex.decode("9000");

        history.record(command, response);
        command[1] = 0x7F;
        response[0] = 0x6F;
        history.entries().getFirst().command()[2] = 0x55;

        assertThat(history.transcript()).isEqualTo("C: 80010000\nR: 9000\n");
    }

    @Test
    void aResponseShorterThanAStatusWordIsKeptAsSent() {
        APDUHistory history = new APDUHistory();

        history.record(Hex.decode("80010000"), new byte[]{0x6F});

        assertThat(history.transcript()).isEqualTo("C: 80010000\nR: 6F\n");
        assertThat(history.entries()).isEmpty();
    }

    @Test
    void noneRecordsNothing() {
        APDUHistory none = APDUHistory.none();

        none.record(Hex.decode("80010000"), Hex.decode("9000"));
        none.note("ignored");

        assertThat(none.transcript()).isEmpty();
        assertThat(none.entries()).isEmpty();
        assertThat(none.capacity()).isZero();
    }

    @Test
    void capacityMustNotBeNegative() {
        assertThatThrownBy(() -> new APDUHistory(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new APDUHistory().capacity()).isEqualTo(APDUHistory.DEFAULT_CAPACITY);
    }

    @Test
    void notesAreOneLine() {
        APDUHistory history = new APDUHistory();

        history.note("first\nsecond\r\nthird");

        assertThat(history.transcript()).isEqualTo("# first second third\n");
    }

    /** The extension takes the part of a session's history that belongs to one test. */
    @Test
    void transcriptSinceAPositionKeepsTheLastEntries() {
        APDUHistory history = new APDUHistory();
        history.record(Hex.decode("80010000"), Hex.decode("9000"));
        long start = history.position();
        history.note("card reset");
        history.record(Hex.decode("80020000"), Hex.decode("9000"));
        history.record(Hex.decode("80030000"), Hex.decode("6D00"));

        assertThat(history.transcriptSince(start, 10)).isEqualTo("""
                # card reset
                C: 80020000
                R: 9000
                C: 80030000
                R: 6D00
                """);
        assertThat(history.transcriptSince(start, 1)).isEqualTo("""
                # 2 earlier entries not shown
                C: 80030000
                R: 6D00
                """);
        assertThat(history.transcriptSince(history.position(), 10)).isEmpty();
    }

    @Test
    void transcriptSinceAPositionThatIsNoLongerKept() {
        APDUHistory history = new APDUHistory(1);
        long start = history.position();
        history.record(Hex.decode("80010000"), Hex.decode("9000"));
        history.record(Hex.decode("80020000"), Hex.decode("9000"));

        assertThat(history.transcriptSince(start, 10)).isEqualTo("""
                # 1 earlier entry not kept (the history keeps the last 1)
                C: 80020000
                R: 9000
                """);
    }

    @Test
    void concurrentRecordingKeepsEveryEntry() throws Exception {
        APDUHistory history = new APDUHistory(10_000);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int t = 0; t < 4; t++) {
                tasks.add(pool.submit(() -> {
                    for (int i = 0; i < 500; i++) {
                        history.record(Hex.decode("80010000"), Hex.decode("9000"));
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(history.entries()).hasSize(2000);
        assertThat(history.position()).isEqualTo(2000);
    }
}
