package javacard.security;

/**
 * The MessageDigest class is the base class for hashing algorithms.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class MessageDigest {

    public static final byte ALG_SHA = 1;
    public static final byte ALG_MD5 = 2;
    public static final byte ALG_SHA_256 = 4;
    public static final byte ALG_SHA_384 = 5;
    public static final byte ALG_SHA_512 = 6;
    public static final byte ALG_SHA_224 = 7;

    /**
     * Creates a MessageDigest instance for the specified algorithm.
     *
     * @param algorithm the algorithm type
     * @param externalAccess if true, the instance can be accessed from any applet context
     * @return the MessageDigest instance
     * @throws CryptoException with NO_SUCH_ALGORITHM if the requested algorithm is not supported
     */
    public static MessageDigest getInstance(byte algorithm, boolean externalAccess) throws CryptoException {
        return null;
    }

    /**
     * Returns the algorithm type.
     *
     * @return the algorithm type
     */
    public abstract byte getAlgorithm();

    /**
     * Returns the byte length of the digest.
     *
     * @return the digest length
     */
    public abstract byte getLength();

    /**
     * Generates a hash of all/last input data.
     *
     * @param inBuff input buffer
     * @param inOffset offset into input buffer
     * @param inLength length of input data
     * @param outBuff output buffer
     * @param outOffset offset into output buffer
     * @return number of bytes of hash output
     */
    public abstract short doFinal(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset);

    /**
     * Accumulates a hash of the input data.
     *
     * @param inBuff input buffer
     * @param inOffset offset into input buffer
     * @param inLength length of input data
     */
    public abstract void update(byte[] inBuff, short inOffset, short inLength);

    /**
     * Resets the MessageDigest to the initial state.
     */
    public abstract void reset();
}
