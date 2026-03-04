package javacard.framework;

/**
 * Exception for APDU-related errors.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class APDUException extends CardRuntimeException {

    public static final short ILLEGAL_USE = 1;
    public static final short BUFFER_BOUNDS = 2;
    public static final short BAD_LENGTH = 3;
    public static final short IO_ERROR = 4;
    public static final short NO_T0_GETRESPONSE = (short) 0xAA;
    public static final short NO_T0_REISSUE = (short) 0xAB;
    public static final short T1_IFD_ABORT = 1;

    /**
     * Constructs an APDUException with the given reason code.
     *
     * @param reason the reason code
     */
    public APDUException(short reason) {
        super(reason);
    }

    /**
     * Throws an APDUException with the given reason code.
     *
     * @param reason the reason code
     * @throws APDUException always
     */
    public static void throwIt(short reason) throws APDUException {
        throw new APDUException(reason);
    }
}
