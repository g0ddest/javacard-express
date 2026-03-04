package javacard.security;

/**
 * The KeyPair class is a container for a pair of keys (public and private).
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class KeyPair {

    public static final byte ALG_RSA = 1;
    public static final byte ALG_RSA_CRT = 2;
    public static final byte ALG_DSA = 3;
    public static final byte ALG_EC_F2M = 4;
    public static final byte ALG_EC_FP = 5;

    /**
     * Constructs a KeyPair for the specified algorithm and key length.
     *
     * @param algorithm the algorithm type
     * @param keyLength the key length in bits
     * @throws CryptoException with NO_SUCH_ALGORITHM if the requested algorithm or key length is not supported
     */
    public KeyPair(byte algorithm, short keyLength) throws CryptoException {
    }

    /**
     * Constructs a KeyPair using the specified public and private keys.
     *
     * @param publicKey the public key
     * @param privateKey the private key
     * @throws CryptoException if the keys are not a valid pair
     */
    public KeyPair(PublicKey publicKey, PrivateKey privateKey) throws CryptoException {
    }

    /**
     * Generates a new key pair.
     *
     * @throws CryptoException if key generation fails
     */
    public void genKeyPair() throws CryptoException {
    }

    /**
     * Returns the public key component.
     *
     * @return the public key, or null if not available
     */
    public PublicKey getPublic() {
        return null;
    }

    /**
     * Returns the private key component.
     *
     * @return the private key, or null if not available
     */
    public PrivateKey getPrivate() {
        return null;
    }
}
