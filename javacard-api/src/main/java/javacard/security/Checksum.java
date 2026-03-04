package javacard.security;

/**
 * The Checksum abstract class is the base class for CRC (cyclic redundancy check) checksum algorithms.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class Checksum {

    public static final byte ALG_ISO3309_CRC16 = 1;
    public static final byte ALG_ISO3309_CRC32 = 2;

    /**
     * Creates a Checksum instance for the specified algorithm.
     *
     * @param algorithm the algorithm type
     * @param externalAccess if true, the instance can be accessed from any applet context
     * @return the Checksum instance
     * @throws CryptoException with NO_SUCH_ALGORITHM if the requested algorithm is not supported
     */
    public static Checksum getInstance(byte algorithm, boolean externalAccess) throws CryptoException {
        return null;
    }

    /**
     * Returns the algorithm type.
     *
     * @return the algorithm type
     */
    public abstract byte getAlgorithm();

    /**
     * Generates a checksum of all/last input data.
     *
     * @param inBuff input buffer
     * @param inOffset offset into input buffer
     * @param inLength length of input data
     * @param outBuff output buffer
     * @param outOffset offset into output buffer
     * @return number of bytes of checksum output
     */
    public abstract short doFinal(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset);

    /**
     * Accumulates input data for checksum computation.
     *
     * @param inBuff input buffer
     * @param inOffset offset into input buffer
     * @param inLength length of input data
     */
    public abstract void update(byte[] inBuff, short inOffset, short inLength);

    /**
     * Initializes the Checksum object with the given initial value.
     *
     * @param bArray byte array containing initial value
     * @param bOff offset into bArray
     * @param bLen length of the initial value
     * @throws CryptoException with ILLEGAL_VALUE if the initial value is invalid
     */
    public abstract void init(byte[] bArray, short bOff, short bLen) throws CryptoException;
}
