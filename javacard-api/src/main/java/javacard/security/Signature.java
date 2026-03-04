package javacard.security;

/**
 * The Signature class is the base class for signature algorithms.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class Signature {

    public static final byte ALG_DES_MAC4_NOPAD = 1;
    public static final byte ALG_DES_MAC8_NOPAD = 2;
    public static final byte ALG_DES_MAC4_ISO9797_M1 = 3;
    public static final byte ALG_DES_MAC8_ISO9797_M1 = 4;
    public static final byte ALG_DES_MAC4_ISO9797_M2 = 5;
    public static final byte ALG_DES_MAC8_ISO9797_M2 = 6;
    public static final byte ALG_DES_MAC4_PKCS5 = 7;
    public static final byte ALG_DES_MAC8_PKCS5 = 8;
    public static final byte ALG_RSA_SHA_ISO9796 = 9;
    public static final byte ALG_RSA_SHA_PKCS1 = 10;
    public static final byte ALG_RSA_MD5_PKCS1 = 11;
    public static final byte ALG_RSA_RIPEMD160_ISO9796 = 12;
    public static final byte ALG_RSA_RIPEMD160_PKCS1 = 13;
    public static final byte ALG_DSA_SHA = 14;
    public static final byte ALG_RSA_SHA_RFC2409 = 15;
    public static final byte ALG_RSA_MD5_RFC2409 = 16;
    public static final byte ALG_ECDSA_SHA = 17;
    public static final byte ALG_AES_MAC_128_NOPAD = 18;
    public static final byte ALG_HMAC_SHA1 = 24;
    public static final byte ALG_HMAC_SHA_256 = 25;
    public static final byte ALG_HMAC_SHA_384 = 26;
    public static final byte ALG_HMAC_SHA_512 = 27;
    public static final byte ALG_ECDSA_SHA_256 = 33;
    public static final byte ALG_ECDSA_SHA_384 = 34;
    public static final byte ALG_ECDSA_SHA_512 = 38;
    public static final byte ALG_RSA_SHA_256_PKCS1 = 40;
    public static final byte ALG_RSA_SHA_384_PKCS1 = 41;
    public static final byte ALG_RSA_SHA_512_PKCS1 = 42;
    public static final byte ALG_AES_CMAC_128 = 49;

    public static final byte MODE_SIGN = 1;
    public static final byte MODE_VERIFY = 2;

    /**
     * Creates a Signature instance for the specified algorithm.
     *
     * @param algorithm the algorithm type
     * @param externalAccess if true, the instance can be accessed from any applet context
     * @return the Signature instance
     * @throws CryptoException with NO_SUCH_ALGORITHM if the requested algorithm is not supported
     */
    public static Signature getInstance(byte algorithm, boolean externalAccess) throws CryptoException {
        return null;
    }

    /**
     * Initializes the Signature object with the given key and mode.
     *
     * @param theKey the key object
     * @param theMode the signature mode (MODE_SIGN or MODE_VERIFY)
     * @throws CryptoException with ILLEGAL_VALUE or UNINITIALIZED_KEY
     */
    public abstract void init(Key theKey, byte theMode) throws CryptoException;

    /**
     * Initializes the Signature object with the given key, mode, and initial data.
     *
     * @param theKey the key object
     * @param theMode the signature mode (MODE_SIGN or MODE_VERIFY)
     * @param bArray byte array containing initial data (e.g., IV)
     * @param bOff offset into bArray
     * @param bLen length of initial data
     * @throws CryptoException with ILLEGAL_VALUE or UNINITIALIZED_KEY
     */
    public abstract void init(Key theKey, byte theMode, byte[] bArray, short bOff, short bLen) throws CryptoException;

    /**
     * Returns the algorithm type.
     *
     * @return the algorithm type
     */
    public abstract byte getAlgorithm();

    /**
     * Returns the byte length of the signature data.
     *
     * @return the signature length
     * @throws CryptoException with INVALID_INIT if not initialized
     */
    public abstract byte getLength() throws CryptoException;

    /**
     * Generates the signature of all/last input data.
     *
     * @param inBuff input buffer
     * @param inOffset offset into input buffer
     * @param inLength length of input data
     * @param sigBuff signature output buffer
     * @param sigOffset offset into signature buffer
     * @return number of bytes of signature output
     * @throws CryptoException if signing fails
     */
    public abstract short sign(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset) throws CryptoException;

    /**
     * Verifies the signature of all/last input data.
     *
     * @param inBuff input buffer
     * @param inOffset offset into input buffer
     * @param inLength length of input data
     * @param sigBuff signature buffer
     * @param sigOffset offset into signature buffer
     * @param sigLength length of the signature
     * @return true if the signature is valid, false otherwise
     * @throws CryptoException if verification fails
     */
    public abstract boolean verify(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset, short sigLength) throws CryptoException;

    /**
     * Accumulates input data for signing or verification.
     *
     * @param inBuff input buffer
     * @param inOffset offset into input buffer
     * @param inLength length of input data
     * @throws CryptoException if update fails
     */
    public abstract void update(byte[] inBuff, short inOffset, short inLength) throws CryptoException;
}
