package javacardx.biometry1toN;

import javacard.framework.CardRuntimeException;

/**
 * Exception thrown by the one-to-many biometry API.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class Bio1toNException extends CardRuntimeException {

    /** A parameter is not valid. */
    public static final short ILLEGAL_VALUE = 1;
    /** Enrollment or candidate data is malformed. */
    public static final short INVALID_DATA = 2;
    /** The requested biometric type is not supported. */
    public static final short UNSUPPORTED_BIO_TYPE = 3;
    /** Matching was requested but no reference template data is enrolled. */
    public static final short NO_BIO_TEMPLATE_ENROLLED = 4;
    /** The method was called in a state where it is not allowed. */
    public static final short ILLEGAL_USE = 5;
    /** All reference template slots of the matcher are in use. */
    public static final short BIO_TEMPLATE_DATA_CAPACITY_EXCEEDED = 6;
    /** The template data and the matcher have different biometric types. */
    public static final short MISMATCHED_BIO_TYPE = 7;

    /**
     * Constructs a Bio1toNException with the given reason code.
     *
     * @param reason the reason code
     */
    public Bio1toNException(short reason) {
        super(reason);
        throw new RuntimeException("stub");
    }

    /**
     * Throws a Bio1toNException with the given reason code.
     *
     * @param reason the reason code
     * @throws Bio1toNException always
     */
    public static void throwIt(short reason) throws Bio1toNException {
        throw new Bio1toNException(reason);
    }
}
