package name.velikodniy.jcexpress.pace;

/**
 * Unchecked exception for Basic Access Control errors (ICAO Doc 9303-11, 4.3).
 *
 * <p>Thrown when the chip rejects GET CHALLENGE or EXTERNAL AUTHENTICATE, or when its response fails the checks
 * of the inspection system (4.3.1 step 4): wrong length, wrong checksum M_IC, or a nonce that does not match.</p>
 */
public class BacException extends RuntimeException {

    /**
     * Creates a new BAC exception with a message.
     *
     * @param message the detail message
     */
    public BacException(String message) {
        super(message);
    }
}
