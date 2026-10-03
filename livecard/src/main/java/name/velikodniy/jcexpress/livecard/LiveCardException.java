package name.velikodniy.jcexpress.livecard;

/**
 * A live-card run cannot continue: invalid settings, a conversion or verification failure before loading, a
 * card content change that did not happen, a cleanup that left objects on the card, or an aborted run.
 */
public class LiveCardException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message what went wrong and, where possible, what to do
     */
    public LiveCardException(String message) {
        super(message);
    }

    /**
     * Creates the exception with a cause.
     *
     * @param message what went wrong
     * @param cause   the underlying failure
     */
    public LiveCardException(String message, Throwable cause) {
        super(message, cause);
    }
}
