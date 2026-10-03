package name.velikodniy.jcexpress;

/**
 * The one line format of APDU transcripts in JavaCard Express (the live-card transcripts use the same lines):
 * {@code C: <command as hex>}, {@code R: <response data and SW1 SW2 as hex>}, {@code # <note>} and
 * {@code ## <title>}. Hex is upper case without spaces.
 */
final class TranscriptFormat {

    private TranscriptFormat() {
    }

    /**
     * Formats a command line.
     *
     * @param command the command APDU
     * @return {@code "C: "} and the command as hex
     */
    static String command(byte[] command) {
        return "C: " + Hex.encode(command);
    }

    /**
     * Formats a response line.
     *
     * @param response the response APDU (data followed by SW1 SW2)
     * @return {@code "R: "} and the response as hex
     */
    static String response(byte[] response) {
        return "R: " + Hex.encode(response);
    }

    /**
     * Formats a note line.
     *
     * @param note the note
     * @return {@code "# "} and the note
     */
    static String note(String note) {
        return "# " + note;
    }

    /**
     * Formats a title line.
     *
     * @param title the title
     * @return {@code "## "} and the title
     */
    static String title(String title) {
        return "## " + title;
    }
}
