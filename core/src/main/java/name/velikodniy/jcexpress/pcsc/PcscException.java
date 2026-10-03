package name.velikodniy.jcexpress.pcsc;

/**
 * Thrown when a PC/SC operation of {@link PcscSession} fails: no reader or card, a connection or
 * transmission error reported by {@code javax.smartcardio} (the {@code CardException} or
 * {@code IllegalStateException} is the cause), or a card connection that is held by another session or
 * thread.
 *
 * <p>Status words returned by the card are not failures of the PC/SC layer: they are returned in the
 * response ({@link PcscSession#select} reports a failed SELECT as
 * {@link name.velikodniy.jcexpress.SelectException}).</p>
 */
public class PcscException extends RuntimeException {

    /**
     * Creates an exception with a message.
     *
     * @param message the detail message
     */
    public PcscException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a message and the underlying cause.
     *
     * @param message the detail message
     * @param cause   the {@code javax.smartcardio} exception
     */
    public PcscException(String message, Throwable cause) {
        super(message, cause);
    }
}
