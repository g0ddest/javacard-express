package javacard.framework;

/**
 * Exception for system-level errors in the Java Card runtime.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class SystemException extends CardRuntimeException {

    public static final short ILLEGAL_VALUE = 1;
    public static final short NO_TRANSIENT_SPACE = 2;
    public static final short ILLEGAL_TRANSIENT = 3;
    public static final short ILLEGAL_AID = 4;
    public static final short NO_RESOURCE = 5;
    public static final short ILLEGAL_USE = 6;

    /**
     * Constructs a SystemException with the given reason code.
     *
     * @param reason the reason code
     */
    public SystemException(short reason) {
        super(reason);
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
