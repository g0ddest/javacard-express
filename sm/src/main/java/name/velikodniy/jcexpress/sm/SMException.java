package name.velikodniy.jcexpress.sm;

/**
 * Unchecked exception for ISO 7816-4 Secure Messaging errors.
 *
 * <p>Thrown when command wrapping, response unwrapping, or MAC verification fails.</p>
 */
public class SMException extends RuntimeException {

    /**
     * Creates a new SM exception with a message.
     *
     * @param message the detail message
     */
    public SMException(String message) {
        super(message);
    }

    /**
     * Creates a new SM exception with a message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public SMException(String message, Throwable cause) {
        super(message, cause);
    }
}
