package name.velikodniy.jcexpress.tlv;

/**
 * Thrown when TLV data cannot be parsed or is malformed.
 */
public class TLVException extends RuntimeException {

    /**
     * Creates a new TLV exception with the given message.
     *
     * @param message the detail message
     */
    public TLVException(String message) {
        super(message);
    }

    /**
     * Creates a new TLV exception with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public TLVException(String message, Throwable cause) {
        super(message, cause);
    }
}
