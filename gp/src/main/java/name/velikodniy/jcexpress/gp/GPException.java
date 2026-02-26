package name.velikodniy.jcexpress.gp;

/**
 * Unchecked exception for GlobalPlatform operation errors.
 *
 * <p>Thrown when GP commands fail: authentication errors, unexpected status words,
 * malformed responses, etc. Includes the status word from the card when applicable.</p>
 *
 * <h3>Typical scenarios:</h3>
 * <ul>
 *   <li>INITIALIZE UPDATE returns non-9000 status</li>
 *   <li>EXTERNAL AUTHENTICATE fails (wrong keys, wrong SCP version)</li>
 *   <li>Card cryptogram verification fails</li>
 *   <li>GP command returns error SW (e.g., 6A88 = referenced data not found)</li>
 * </ul>
 */
public class GPException extends RuntimeException {

    private final int statusWord;

    /**
     * Creates a GP exception with a message and no status word.
     *
     * @param message the detail message
     */
    public GPException(String message) {
        super(message);
        this.statusWord = -1;
    }

    /**
     * Creates a GP exception with a message and the card's status word.
     *
     * @param message the detail message
     * @param sw      the status word from the card
     */
    public GPException(String message, int sw) {
        super(message + String.format(" (SW=%04X)", sw));
        this.statusWord = sw;
    }

    /**
     * Creates a GP exception wrapping another exception.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public GPException(String message, Throwable cause) {
        super(message, cause);
        this.statusWord = -1;
    }

    /**
     * Returns the status word that caused this error, or -1 if not applicable.
     *
     * @return the 2-byte status word, or -1
     */
    public int statusWord() {
        return statusWord;
    }
}
