package javacard.security;

import javacard.framework.CardRuntimeException;

/**
 * CryptoException represents a cryptography-related exception.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class CryptoException extends CardRuntimeException {

    public static final short ILLEGAL_VALUE = 1;
    public static final short UNINITIALIZED_KEY = 2;
    public static final short NO_SUCH_ALGORITHM = 3;
    public static final short INVALID_INIT = 4;
    public static final short ILLEGAL_USE = 5;

    /**
     * Constructs a CryptoException with the given reason code.
     *
     * @param reason the reason code
     */
    public CryptoException(short reason) {
        super(reason);
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
