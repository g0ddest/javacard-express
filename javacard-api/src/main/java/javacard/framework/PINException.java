package javacard.framework;

/**
 * Exception for PIN-related errors.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class PINException extends CardRuntimeException {

    /** Reason code: a PIN or try-limit value is not allowed, for example too long. */
    public static final short ILLEGAL_VALUE = 1;
    /** Reason code: the operation is not allowed in the current state of the PIN object. */
    public static final short ILLEGAL_STATE = 2;

    /**
     * Constructs a PINException with the given reason code.
     *
     * @param reason the reason code
     */
    public PINException(short reason) {
        super(reason);
        throw new RuntimeException("stub");
    }

    /**
     * Throws a PINException with the given reason code.
     *
     * @param reason the reason code
     * @throws PINException always
     */
    public static void throwIt(short reason) throws PINException {
        throw new PINException(reason);
    }
}
