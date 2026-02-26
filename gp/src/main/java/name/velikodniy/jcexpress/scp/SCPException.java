package name.velikodniy.jcexpress.scp;

/**
 * Unchecked exception for Secure Channel Protocol errors.
 *
 * <p>Wraps checked JCE exceptions (e.g., {@link java.security.NoSuchAlgorithmException})
 * and protocol-level errors (e.g., card cryptogram mismatch).</p>
 */
public class SCPException extends RuntimeException {

    /**
     * Creates a new SCP exception with a message.
     *
     * @param message the detail message
     */
    public SCPException(String message) {
        super(message);
    }

    /**
     * Creates a new SCP exception with a message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public SCPException(String message, Throwable cause) {
        super(message, cause);
    }
}
