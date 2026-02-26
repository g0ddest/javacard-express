package name.velikodniy.jcexpress.pace;

/**
 * Unchecked exception for PACE protocol errors.
 *
 * <p>Thrown when the PACE protocol fails, e.g., due to authentication failure,
 * invalid card response, or cryptographic error.</p>
 */
public class PaceException extends RuntimeException {

    /**
     * Creates a new PACE exception with a message.
     *
     * @param message the detail message
     */
    public PaceException(String message) {
        super(message);
    }

    /**
     * Creates a new PACE exception with a message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public PaceException(String message, Throwable cause) {
        super(message, cause);
    }
}
