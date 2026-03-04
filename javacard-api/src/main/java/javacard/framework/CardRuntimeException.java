package javacard.framework;

/**
 * Base class for unchecked exceptions in the Java Card framework.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class CardRuntimeException extends RuntimeException {

    private short reason;

    /**
     * Constructs a CardRuntimeException with the given reason code.
     *
     * @param reason the reason code
     */
    public CardRuntimeException(short reason) {
        this.reason = reason;
    }

    /**
     * Returns the reason code.
     *
     * @return the reason code
     */
    public short getReason() {
        return reason;
    }

    /**
     * Sets the reason code.
     *
     * @param reason the new reason code
     */
    public void setReason(short reason) {
        this.reason = reason;
    }

    /**
     * Throws a CardRuntimeException with the given reason code.
     *
     * @param reason the reason code
     * @throws CardRuntimeException always
     */
    public static void throwIt(short reason) throws CardRuntimeException {
        throw new CardRuntimeException(reason);
    }
}
