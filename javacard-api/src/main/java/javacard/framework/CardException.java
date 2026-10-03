package javacard.framework;

/**
 * Base class for checked exceptions in the Java Card framework.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // Stub class: params are API contract, RuntimeException is intentional
public class CardException extends Exception {

    /**
     * Constructs a CardException with the given reason code.
     *
     * @param reason the reason code
     */
    public CardException(short reason) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the reason code.
     *
     * @return the reason code
     */
    public short getReason() {
        throw new RuntimeException("stub");
    }

    /**
     * Sets the reason code.
     *
     * @param reason the new reason code
     */
    public void setReason(short reason) {
        throw new RuntimeException("stub");
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
