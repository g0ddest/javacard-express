package name.velikodniy.jcexpress;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The line format of the transcripts: a response line carries the time its exchange took, in milliseconds with one
 * decimal, when the session measured it.
 */
class TranscriptFormatTest {

    private static final byte[] OK = Hex.decode("00649000");

    @Test
    void aResponseLineShowsTheTimeOfTheExchange() {
        assertThat(TranscriptFormat.response(OK, Duration.ofMillis(12).plusNanos(345_678)))
                .isEqualTo("R: 00649000  (12.3 ms)");
        assertThat(TranscriptFormat.response(OK, Duration.ofNanos(420_000))).isEqualTo("R: 00649000  (0.4 ms)");
    }

    @Test
    void aResponseLineWithoutAMeasuredTimeIsTheResponseAlone() {
        assertThat(TranscriptFormat.response(OK, null)).isEqualTo("R: 00649000").isEqualTo(TranscriptFormat.response(OK));
    }

    @Test
    void theTimeUsesADotWhateverTheDefaultLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertThat(TranscriptFormat.milliseconds(Duration.ofMillis(1500))).isEqualTo("1500.0 ms");
            assertThat(TranscriptFormat.milliseconds(Duration.ofNanos(1_250_000))).isEqualTo("1.3 ms");
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void commandNoteAndTitleLines() {
        assertThat(TranscriptFormat.command(Hex.decode("8052000002"))).isEqualTo("C: 8052000002");
        assertThat(TranscriptFormat.note("card reset")).isEqualTo("# card reset");
        assertThat(TranscriptFormat.title("WalletTest")).isEqualTo("## WalletTest");
    }
}
