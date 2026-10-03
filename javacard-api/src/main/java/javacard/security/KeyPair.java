package javacard.security;

/**
 * The KeyPair class is a container for a pair of keys (public and private).
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class KeyPair {

    /** Key pair algorithm: RSA with a modulus and exponent private key. */
    public static final byte ALG_RSA = 1;
    /** Key pair algorithm: RSA with a Chinese Remainder Theorem private key. */
    public static final byte ALG_RSA_CRT = 2;
    /** Key pair algorithm: DSA. */
    public static final byte ALG_DSA = 3;
    /** Key pair algorithm: elliptic curve over a binary field F(2^m). */
    public static final byte ALG_EC_F2M = 4;
    /** Key pair algorithm: elliptic curve over a prime field F(p). */
    public static final byte ALG_EC_FP = 5;
    /** Finite-field Diffie-Hellman key pair ({@link DHPublicKey} and {@link DHPrivateKey}). */
    public static final byte ALG_DH = 6;

    /**
     * Constructs a KeyPair for the specified algorithm and key length.
     *
     * @param algorithm the algorithm type
     * @param keyLength the key length in bits
     * @throws CryptoException with NO_SUCH_ALGORITHM if the requested algorithm or key length is not supported
     */
    public KeyPair(byte algorithm, short keyLength) throws CryptoException {
        throw new RuntimeException("stub");
    }

    /**
     * Constructs a KeyPair using the specified public and private keys.
     *
     * @param publicKey the public key
     * @param privateKey the private key
     * @throws CryptoException if the keys are not a valid pair
     */
    public KeyPair(PublicKey publicKey, PrivateKey privateKey) throws CryptoException {
        throw new RuntimeException("stub");
    }

    /**
     * Generates a new key pair.
     *
     * @throws CryptoException if key generation fails
     */
    public final void genKeyPair() throws CryptoException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the public key component.
     *
     * @return the public key, or null if not available
     */
    public PublicKey getPublic() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the private key component.
     *
     * @return the private key, or null if not available
     */
    public PrivateKey getPrivate() {
        throw new RuntimeException("stub");
    }
}
