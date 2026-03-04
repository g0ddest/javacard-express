package javacard.framework;

/**
 * Exception for PIN-related errors.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class PINException extends CardRuntimeException {

    public static final short ILLEGAL_VALUE = 1;
    public static final short ILLEGAL_STATE = 2;

    /**
     * Constructs a PINException with the given reason code.
     *
     * @param reason the reason code
     */
    public PINException(short reason) {
        super(reason);
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
