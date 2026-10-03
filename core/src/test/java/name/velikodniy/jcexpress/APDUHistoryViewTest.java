package name.velikodniy.jcexpress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A view of a history from a position on ({@code since}): what the card of a {@code @JavaCardTest} class returns as
 * the history of the current test or class. It shows the entries recorded from that position on, also those
 * recorded later, and records into the history it views.
 */
class APDUHistoryViewTest {

    private static final byte[] SELECT = Hex.decode("00A4040008F04A43580102030400");
    private static final byte[] COMMAND = Hex.decode("80530000");
    private static final byte[] OK = Hex.decode("9000");

    @Test
    void aViewShowsTheEntriesFromItsPositionOn() {
        APDUHistory history = new APDUHistory();
        history.record(SELECT, OK);
        APDUHistory view = history.since(history.position());
        history.note("install");
        history.record(COMMAND, OK);

        assertThat(view.entries()).extracting(entry -> Hex.encode(entry.command())).containsExactly("80530000");
        assertThat(view.transcript()).isEqualTo("# install\nC: 80530000\nR: 9000\n");
        assertThat(history.entries()).hasSize(2);
    }

    @Test
    void aViewRecordsIntoTheHistoryItViews() {
        APDUHistory history = new APDUHistory();
        APDUHistory view = history.since(0);

        view.note("build of an applet");
        view.record(COMMAND, OK);

        assertThat(history.transcript()).isEqualTo("# build of an applet\nC: 80530000\nR: 9000\n");
        assertThat(view.position()).isEqualTo(history.position()).isEqualTo(2);
    }

    @Test
    void aViewSaysHowManyOfItsEntriesWereDropped() {
        APDUHistory history = new APDUHistory(2);
        history.record(SELECT, OK);
        APDUHistory view = history.since(history.position());
        history.record(COMMAND, OK);
        history.record(COMMAND, OK);
        history.record(COMMAND, OK);

        assertThat(view.transcript()).startsWith("# 1 earlier entry not kept (the history keeps the last 2)\n");
        assertThat(view.capacity()).isEqualTo(2);
    }
}
