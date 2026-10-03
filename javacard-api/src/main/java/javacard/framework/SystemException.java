package javacard.framework;

/**
 * Exception for system-level errors in the Java Card runtime.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class SystemException extends CardRuntimeException {

    /** Reason code: a parameter value is not allowed. */
    public static final short ILLEGAL_VALUE = 1;
    /** Reason code: there is not enough transient memory. */
    public static final short NO_TRANSIENT_SPACE = 2;
    /** Reason code: transient objects cannot be created in the current context. */
    public static final short ILLEGAL_TRANSIENT = 3;
    /** Reason code: the AID is not valid here, for example already registered. */
    public static final short ILLEGAL_AID = 4;
    /** Reason code: a resource, such as memory for a new object, is exhausted. */
    public static final short NO_RESOURCE = 5;
    /** Reason code: the operation is not allowed in the current state. */
    public static final short ILLEGAL_USE = 6;

    /**
     * Constructs a SystemException with the given reason code.
     *
     * @param reason the reason code
     */
    public SystemException(short reason) {
        super(reason);
        throw new RuntimeException("stub");
    }

    /**
     * Throws a SystemException with the given reason code.
     *
     * @param reason the reason code
     * @throws SystemException always
     */
    public static void throwIt(short reason) throws SystemException {
        throw new SystemException(reason);
    }
}
