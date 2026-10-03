package javacard.security;

/**
 * The KeyAgreement class is the base class for key agreement algorithms.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class KeyAgreement {

    /**
     * Constructor for algorithm implementations; applets obtain instances through {@code getInstance}.
     */
    protected KeyAgreement() {
        throw new RuntimeException("stub");
    }

    /** Elliptic curve Diffie-Hellman (IEEE 1363 ECSVDP-DH); the result is the SHA-1 hash of the shared secret. */
    public static final byte ALG_EC_SVDP_DH = 1;
    /** Same value as {@link #ALG_EC_SVDP_DH}: ECDH whose shared secret is hashed with SHA-1. */
    public static final byte ALG_EC_SVDP_DH_KDF = 1;
    /**
     * Elliptic curve Diffie-Hellman with cofactor (IEEE 1363 ECSVDP-DHC); the result is the SHA-1 hash of the shared
     * secret.
     */
    public static final byte ALG_EC_SVDP_DHC = 2;
    /** Same value as {@link #ALG_EC_SVDP_DHC}: ECDH with cofactor whose shared secret is hashed with SHA-1. */
    public static final byte ALG_EC_SVDP_DHC_KDF = 2;
    /** Elliptic curve Diffie-Hellman (IEEE 1363 ECSVDP-DH); the result is the shared secret itself. */
    public static final byte ALG_EC_SVDP_DH_PLAIN = 3;
    /** Elliptic curve Diffie-Hellman with cofactor (IEEE 1363 ECSVDP-DHC); the result is the shared secret itself. */
    public static final byte ALG_EC_SVDP_DHC_PLAIN = 4;
    /** Generic mapping of the PACE protocol (ICAO Doc 9303 / BSI TR-03110): returns the mapped generator point. */
    public static final byte ALG_EC_PACE_GM = 5;
    /** ECDH that returns both coordinates of the shared point, without hashing. */
    public static final byte ALG_EC_SVDP_DH_PLAIN_XY = 6;
    /** Finite-field Diffie-Hellman that returns the shared secret without hashing. */
    public static final byte ALG_DH_PLAIN = 7;

    /**
     * Creates a KeyAgreement instance for the specified algorithm.
     *
     * @param algorithm the algorithm type
     * @param externalAccess if true, the instance can be accessed from any applet context
     * @return the KeyAgreement instance
     * @throws CryptoException with NO_SUCH_ALGORITHM if the requested algorithm is not supported
     */
    public static final KeyAgreement getInstance(byte algorithm, boolean externalAccess) throws CryptoException {
        throw new RuntimeException("stub");
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
