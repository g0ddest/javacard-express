package javacard.framework;

/**
 * Exception for transaction-related errors.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class TransactionException extends CardRuntimeException {

    public static final short IN_PROGRESS = 1;
    public static final short NOT_IN_PROGRESS = 2;
    public static final short BUFFER_FULL = 3;
    public static final short INTERNAL_FAILURE = 4;
    public static final short ILLEGAL_USE = 5;

    /**
     * Constructs a TransactionException with the given reason code.
     *
     * @param reason the reason code
     */
    public TransactionException(short reason) {
        super(reason);
    }

    /**
     * Throws a TransactionException with the given reason code.
     *
     * @param reason the reason code
     * @throws TransactionException always
     */
    public static void throwIt(short reason) throws TransactionException {
        throw new TransactionException(reason);
    }
}
