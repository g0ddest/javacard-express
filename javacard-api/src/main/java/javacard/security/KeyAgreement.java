package javacard.security;

/**
 * The KeyAgreement class is the base class for key agreement algorithms.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class KeyAgreement {

    public static final byte ALG_EC_SVDP_DH = 1;
    public static final byte ALG_EC_SVDP_DHC = 2;
    public static final byte ALG_EC_SVDP_DH_PLAIN = 3;
    public static final byte ALG_EC_SVDP_DHC_PLAIN = 4;

    /**
     * Creates a KeyAgreement instance for the specified algorithm.
     *
     * @param algorithm the algorithm type
     * @param externalAccess if true, the instance can be accessed from any applet context
     * @return the KeyAgreement instance
     * @throws CryptoException with NO_SUCH_ALGORITHM if the requested algorithm is not supported
     */
    public static KeyAgreement getInstance(byte algorithm, boolean externalAccess) throws CryptoException {
        return null;
    }

    /**
     * Initializes the key agreement object with the given private key.
     *
     * @param privKey the private key
     * @throws CryptoException with ILLEGAL_VALUE or UNINITIALIZED_KEY
     */
    public abstract void init(PrivateKey privKey) throws CryptoException;

    /**
     * Generates the shared secret.
     *
     * @param publicData buffer containing the public key data of the other party
     * @param publicOffset offset into the publicData buffer
     * @param publicLength length of the public key data
     * @param secret output buffer for the shared secret
     * @param secretOffset offset into the secret buffer
     * @return the byte length of the shared secret
     * @throws CryptoException if generation fails
     */
    public abstract short generateSecret(byte[] publicData, short publicOffset, short publicLength, byte[] secret, short secretOffset) throws CryptoException;

    /**
     * Returns the algorithm type.
     *
     * @return the algorithm type
     */
    public abstract byte getAlgorithm();
}
