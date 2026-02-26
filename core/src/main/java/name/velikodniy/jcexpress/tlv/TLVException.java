package name.velikodniy.jcexpress.tlv;

/**
 * Thrown when TLV data cannot be parsed or is malformed.
 */
public class TLVException extends RuntimeException {

    public TLVException(String message) {
        super(message);
    }

    public TLVException(String message, Throwable cause) {
        super(message, cause);
    }
}
