package javacardx.framework.util;

import javacard.framework.CardRuntimeException;

/**
 * Exception thrown by {@link ArrayLogic}.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class UtilException extends CardRuntimeException {

    /** A parameter is not valid. */
    public static final short ILLEGAL_VALUE = 1;
    /** The arrays or the value have incompatible types. */
    public static final short TYPE_MISMATCHED = 2;

    /**
     * Constructs a UtilException with the given reason code.
     *
     * @param reason the reason code
     */
    public UtilException(short reason) {
        super(reason);
        throw new RuntimeException("stub");
    }

    /**
     * Throws a UtilException with the given reason code.
     *
     * @param reason the reason code
     * @throws UtilException always
     */
    public static void throwIt(short reason) throws UtilException {
        throw new UtilException(reason);
    }
}
