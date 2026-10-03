package javacard.security;

import javacard.framework.CardRuntimeException;

/**
 * CryptoException represents a cryptography-related exception.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class CryptoException extends CardRuntimeException {

    /** Reason code: a parameter value, such as a key length, is not supported. */
    public static final short ILLEGAL_VALUE = 1;
    /** Reason code: the key has not been initialized. */
    public static final short UNINITIALIZED_KEY = 2;
    /** Reason code: the requested algorithm is not supported. */
    public static final short NO_SUCH_ALGORITHM = 3;
    /** Reason code: the object was not initialized for the requested operation. */
    public static final short INVALID_INIT = 4;
    /** Reason code: the operation is not allowed in the current state or for the input. */
    public static final short ILLEGAL_USE = 5;

    /**
     * Constructs a CryptoException with the given reason code.
     *
     * @param reason the reason code
     */
    public CryptoException(short reason) {
        super(reason);
        throw new RuntimeException("stub");
    }

    /**
     * Throws a CryptoException with the given reason code.
     *
     * @param reason the reason code
     * @throws CryptoException always
     */
    public static void throwIt(short reason) throws CryptoException {
        throw new CryptoException(reason);
    }
}
