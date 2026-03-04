package javacard.framework;

/**
 * Base class for checked exceptions in the Java Card framework.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // Stub class: params are API contract, RuntimeException is intentional
public class CardException extends Exception {

    private short reason;

    /**
     * Constructs a CardException with the given reason code.
     *
     * @param reason the reason code
     */
    public CardException(short reason) {
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
     * Throws a CardException with the given reason code.
     *
     * @param reason the reason code
     * @throws CardException always
     */
    public static void throwIt(short reason) throws CardException {
        throw new CardException(reason);
    }
}
