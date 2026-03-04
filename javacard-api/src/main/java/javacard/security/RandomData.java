package javacard.security;

/**
 * The RandomData abstract class is the base class for random number generation.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class RandomData {

    public static final byte ALG_PSEUDO_RANDOM = 1;
    public static final byte ALG_SECURE_RANDOM = 2;

    /**
     * Creates a RandomData instance for the specified algorithm.
     *
     * @param algorithm the algorithm type
     * @return the RandomData instance
     * @throws CryptoException with NO_SUCH_ALGORITHM if the requested algorithm is not supported
     */
    public static RandomData getInstance(byte algorithm) throws CryptoException {
        return null;
    }

    /**
     * Generates random data.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @param length number of bytes to generate
     * @throws CryptoException if generation fails
     */
    public abstract void generateData(byte[] buffer, short offset, short length) throws CryptoException;

    /**
     * Seeds the random data generator.
     *
     * @param buffer seed data buffer
     * @param offset offset into the buffer
     * @param length length of the seed data
     */
    public abstract void setSeed(byte[] buffer, short offset, short length);
}
