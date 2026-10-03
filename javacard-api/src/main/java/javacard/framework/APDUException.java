package javacard.framework;

/**
 * Exception for APDU-related errors.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class APDUException extends CardRuntimeException {

    /** Reason code: the method is not allowed in the current state of the APDU. */
    public static final short ILLEGAL_USE = 1;
    /** Reason code: an offset or length lies outside the APDU buffer. */
    public static final short BUFFER_BOUNDS = 2;
    /** Reason code: a requested length is invalid or too large. */
    public static final short BAD_LENGTH = 3;
    /** Reason code: an unrecoverable transmission error occurred. */
    public static final short IO_ERROR = 4;
    /** Reason code: with T=0 the terminal did not send the expected GET RESPONSE. */
    public static final short NO_T0_GETRESPONSE = (short) 0xAA;
    /** Reason code: with T=0 the terminal did not re-send the command with the corrected Le. */
    public static final short NO_T0_REISSUE = 0xAC;
    /** Reason code: with T=1 the terminal aborted the block chain. */
    public static final short T1_IFD_ABORT = 0xAB;

    /**
     * Constructs an APDUException with the given reason code.
     *
     * @param reason the reason code
     */
    public APDUException(short reason) {
        super(reason);
        throw new RuntimeException("stub");
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
