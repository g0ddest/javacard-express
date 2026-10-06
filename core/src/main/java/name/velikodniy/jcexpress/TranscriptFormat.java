package name.velikodniy.jcexpress;

import java.time.Duration;
import java.util.Locale;

/**
 * The line format of APDU transcripts in JavaCard Express: {@code C: <command as hex>},
 * {@code R: <response data and SW1 SW2 as hex>}, followed by the time the exchange took when the session measured
 * it ({@code R: 00649000  (12.3 ms)}), {@code # <note>} and {@code ## <title>}. Hex is upper case without spaces.
 *
 * <p>The histories of the sessions ({@link APDUHistory#transcript()}), the transcripts attached to failed tests and
 * published as {@code apdu-transcript.txt}, the lines printed with {@value JavaCardExtension#LOG_PARAMETER} or
 * {@link SmartCardSession#logged(boolean) logged(true)} and the transcripts of the live-card harness all use these
 * lines.</p>
 */
public final class TranscriptFormat {

    private TranscriptFormat() {
    }

    /**
     * Formats a command line.
     *
     * @param command the command APDU
     * @return {@code "C: "} and the command as hex
     */
    public static String command(byte[] command) {
        return "C: " + Hex.encode(command);
    }

    /**
     * Formats a response line.
     *
     * @param response the response APDU (data followed by SW1 SW2)
     * @return {@code "R: "} and the response as hex
     */
    public static String response(byte[] response) {
        return "R: " + Hex.encode(response);
    }

    /**
     * Formats a response line with the time its exchange took.
     *
     * @param response the response APDU (data followed by SW1 SW2)
     * @param duration how long the exchange took, or {@code null} if it was not measured
     * @return {@code "R: "} and the response as hex, then, if the time was measured, two spaces and the time in
     *         parentheses, e.g. {@code R: 00649000  (12.3 ms)}
     */
    public static String response(byte[] response, Duration duration) {
        return duration == null ? response(response) : response(response) + "  (" + milliseconds(duration) + ")";
    }

    /**
     * Formats a duration in milliseconds with one decimal, whatever the default locale.
     *
     * @param duration the duration
     * @return e.g. {@code "12.3 ms"} or {@code "0.4 ms"}
     */
    public static String milliseconds(Duration duration) {
        return String.format(Locale.ROOT, "%.1f ms", duration.toNanos() / 1_000_000.0);
    }

    /**
     * Formats a note line.
     *
     * @param note the note
     * @return {@code "# "} and the note
     */
    public static String note(String note) {
        return "# " + note;
    }

    /**
     * Formats a title line.
     *
     * @param title the title
     * @return {@code "## "} and the title
     */
    public static String title(String title) {
        return "## " + title;
    }
}
