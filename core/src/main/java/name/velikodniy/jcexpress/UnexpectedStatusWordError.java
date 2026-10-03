package name.velikodniy.jcexpress;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Thrown by {@link APDUResponse#requireSuccess()} when the card did not answer '9000', and by
 * {@link APDUResponse#requireSw(int...)} when it answered none of the expected status words.
 *
 * <p>An {@link AssertionError}: a test that stops here is reported as failed, like an assertion that does not hold,
 * not as an error. The message names the status words with their meaning ({@link SW#describe(int)}) and shows the
 * exchange in the transcript format used throughout JavaCard Express: {@code C:} the command (when the response
 * knows it, see {@link APDUResponse#inReplyTo(byte[])}) and {@code R:} the response, both as hex.</p>
 *
 * <pre>
 * Expected SW 9000 (success) but was 6A82 (file or application not found)
 * C: 00A4040005F000000001
 * R: 6A82
 * </pre>
 */
public final class UnexpectedStatusWordError extends AssertionError {

    private static final long serialVersionUID = 1L;

    private final transient APDUResponse response;

    UnexpectedStatusWordError(APDUResponse response) {
        this(response, new int[]{SW.NO_ERROR});
    }

    UnexpectedStatusWordError(APDUResponse response, int[] expected) {
        super(message(response, expected));
        this.response = response;
    }

    /**
     * Returns the response that failed the check.
     *
     * @return the response
     */
    public APDUResponse response() {
        return response;
    }

    private static String message(APDUResponse response, int[] expected) {
        String accepted = Arrays.stream(expected).mapToObj(SW::format).collect(Collectors.joining(" or "));
        StringBuilder text = new StringBuilder("Expected SW " + accepted + " but was " + SW.format(response.sw()));
        byte[] command = response.command();
        if (command != null) {
            text.append('\n').append(TranscriptFormat.command(command));
        }
        return text.append('\n').append(TranscriptFormat.response(response.toBytes())).toString();
    }
}
