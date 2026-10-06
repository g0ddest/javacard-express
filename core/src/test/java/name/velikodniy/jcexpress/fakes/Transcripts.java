package name.velikodniy.jcexpress.fakes;

import java.util.List;

/**
 * The time a session measured varies from run to run; tests that compare transcripts or printed lines exactly
 * remove it first ({@link #withoutTimes(String)}) and check its format separately ({@link #TIMED_RESPONSE}).
 */
public final class Transcripts {

    /** The time a response line ends with: two spaces and the milliseconds with one decimal in parentheses. */
    public static final String TIME = " {2}\\(\\d+\\.\\d ms\\)";

    /** A whole response line with its time, e.g. {@code R: 00649000  (12.3 ms)}. */
    public static final String TIMED_RESPONSE = "R: (?:[0-9A-F]{2})+" + TIME;

    private static final String TIME_OF_A_RESPONSE = "(?m)(R: (?:[0-9A-F]{2})*)" + TIME + "$";

    private Transcripts() {
    }

    /**
     * Removes the times from the response lines of a transcript.
     *
     * @param transcript transcript text
     * @return the same text without the times
     */
    public static String withoutTimes(String transcript) {
        return transcript.replaceAll(TIME_OF_A_RESPONSE, "$1");
    }

    /**
     * Removes the times from printed lines.
     *
     * @param lines lines of a transcript, possibly with a prefix such as {@code [JCX] }
     * @return the same lines without the times
     */
    public static List<String> withoutTimes(List<String> lines) {
        return lines.stream().map(Transcripts::withoutTimes).toList();
    }
}
