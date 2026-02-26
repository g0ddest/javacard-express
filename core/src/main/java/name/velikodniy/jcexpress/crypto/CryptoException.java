package name.velikodniy.jcexpress.crypto;

/**
 * Unchecked exception for cryptographic operation failures.
 *
 * <p>Wraps checked JCE exceptions (e.g., {@link java.security.NoSuchAlgorithmException})
 * since algorithm unavailability indicates a broken JVM, not a runtime condition.</p>
 */
public class CryptoException extends RuntimeException {

    /**
     * Creates a new crypto exception with a message.
     *
     * @param message the detail message
     */
    public CryptoException(String message) {
        super(message);
    }

    /**
     * Creates a new crypto exception with a message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
