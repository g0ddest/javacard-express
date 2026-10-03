package javacardx.biometry;

import javacard.framework.CardRuntimeException;

/**
 * Exception thrown by the biometry API.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class BioException extends CardRuntimeException {

    /** A parameter is not valid. */
    public static final short ILLEGAL_VALUE = 1;
    /** Enrollment or candidate data is malformed. */
    public static final short INVALID_DATA = 2;
    /** The requested biometric type or matching algorithm is not available. */
    public static final short NO_SUCH_BIO_TEMPLATE = 3;
    /** Matching was requested but no reference template is enrolled. */
    public static final short NO_TEMPLATES_ENROLLED = 4;
    /** The method was called in a state where it is not allowed. */
    public static final short ILLEGAL_USE = 5;

    /**
     * Constructs a BioException with the given reason code.
     *
     * @param reason the reason code
     */
    public BioException(short reason) {
        super(reason);
        throw new RuntimeException("stub");
    }

    /**
     * Throws a BioException with the given reason code.
     *
     * @param reason the reason code
     * @throws BioException always
     */
    public static void throwIt(short reason) throws BioException {
        throw new BioException(reason);
    }
}
