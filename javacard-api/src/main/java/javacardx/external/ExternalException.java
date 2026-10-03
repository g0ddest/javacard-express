package javacardx.external;

import javacard.framework.CardRuntimeException;

/**
 * Exception thrown when access to an external memory subsystem fails.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class ExternalException extends CardRuntimeException {

    /** The requested memory subsystem is not available. */
    public static final short NO_SUCH_SUBSYSTEM = 1;
    /** A parameter is not valid for the memory subsystem. */
    public static final short INVALID_PARAM = 2;
    /** The memory subsystem reported an internal error. */
    public static final short INTERNAL_ERROR = 3;

    /**
     * Constructs an ExternalException with the given reason code.
     *
     * @param reason the reason code
     */
    public ExternalException(short reason) {
        super(reason);
        throw new RuntimeException("stub");
    }

    /**
     * Throws an ExternalException with the given reason code.
     *
     * @param reason the reason code
     * @throws ExternalException always
     */
    public static void throwIt(short reason) throws ExternalException {
        throw new ExternalException(reason);
    }
}
