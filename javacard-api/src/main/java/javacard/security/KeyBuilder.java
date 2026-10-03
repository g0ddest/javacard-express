package javacard.security;

/**
 * Factory for uninitialized key objects.
 *
 * <p>The {@code TYPE_*} constants select the kind of key and where its value is stored (persistent memory, or
 * transient memory cleared on reset or on deselect). The {@code ALG_TYPE_*} constants are the algorithm types of
 * the four-argument {@link #buildKey(byte, byte, short, boolean)}, and the {@code LENGTH_*} constants are
 * common key sizes in bits.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class KeyBuilder {

    private KeyBuilder() {
    }

    /**
     * Key type for {@code buildKey(byte, short, boolean)}: DES or triple-DES key whose key data is kept in
     * CLEAR_ON_RESET transient memory.
     */
    public static final byte TYPE_DES_TRANSIENT_RESET = 1;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: DES or triple-DES key whose key data is kept in
     * CLEAR_ON_DESELECT transient memory.
     */
    public static final byte TYPE_DES_TRANSIENT_DESELECT = 2;
    /** Key type for {@code buildKey(byte, short, boolean)}: DES or triple-DES key in persistent memory. */
    public static final byte TYPE_DES = 3;
    /** Key type for {@code buildKey(byte, short, boolean)}: RSA public key in persistent memory. */
    public static final byte TYPE_RSA_PUBLIC = 4;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: RSA private key (modulus and exponent form) in persistent
     * memory.
     */
    public static final byte TYPE_RSA_PRIVATE = 5;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: RSA private key in Chinese Remainder Theorem form in
     * persistent memory.
     */
    public static final byte TYPE_RSA_CRT_PRIVATE = 6;
    /** Key type for {@code buildKey(byte, short, boolean)}: DSA public key in persistent memory. */
    public static final byte TYPE_DSA_PUBLIC = 7;
    /** Key type for {@code buildKey(byte, short, boolean)}: DSA private key in persistent memory. */
    public static final byte TYPE_DSA_PRIVATE = 8;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: EC public key over a binary field F(2^m) in persistent
     * memory.
     */
    public static final byte TYPE_EC_F2M_PUBLIC = 9;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: EC private key over a binary field F(2^m) in persistent
     * memory.
     */
    public static final byte TYPE_EC_F2M_PRIVATE = 10;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: EC public key over a prime field F(p) in persistent memory.
     */
    public static final byte TYPE_EC_FP_PUBLIC = 11;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: EC private key over a prime field F(p) in persistent memory.
     */
    public static final byte TYPE_EC_FP_PRIVATE = 12;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: AES key whose key data is kept in CLEAR_ON_RESET transient
     * memory.
     */
    public static final byte TYPE_AES_TRANSIENT_RESET = 13;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: AES key whose key data is kept in CLEAR_ON_DESELECT
     * transient memory.
     */
    public static final byte TYPE_AES_TRANSIENT_DESELECT = 14;
    /** Key type for {@code buildKey(byte, short, boolean)}: AES key in persistent memory. */
    public static final byte TYPE_AES = 15;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: SEED key whose key data is kept in CLEAR_ON_RESET transient
     * memory.
     */
    public static final byte TYPE_KOREAN_SEED_TRANSIENT_RESET = 16;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: SEED key whose key data is kept in CLEAR_ON_DESELECT
     * transient memory.
     */
    public static final byte TYPE_KOREAN_SEED_TRANSIENT_DESELECT = 17;
    /** Key type for {@code buildKey(byte, short, boolean)}: SEED key in persistent memory. */
    public static final byte TYPE_KOREAN_SEED = 18;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: HMAC key whose key data is kept in CLEAR_ON_RESET transient
     * memory.
     */
    public static final byte TYPE_HMAC_TRANSIENT_RESET = 19;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: HMAC key whose key data is kept in CLEAR_ON_DESELECT
     * transient memory.
     */
    public static final byte TYPE_HMAC_TRANSIENT_DESELECT = 20;
    /** Key type for {@code buildKey(byte, short, boolean)}: HMAC key in persistent memory. */
    public static final byte TYPE_HMAC = 21;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: RSA private key (modulus and exponent form) whose key data
     * is kept in CLEAR_ON_RESET transient memory.
     */
    public static final byte TYPE_RSA_PRIVATE_TRANSIENT_RESET = 22;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: RSA private key (modulus and exponent form) whose key data
     * is kept in CLEAR_ON_DESELECT transient memory.
     */
    public static final byte TYPE_RSA_PRIVATE_TRANSIENT_DESELECT = 23;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: RSA private key in Chinese Remainder Theorem form whose key
     * data is kept in CLEAR_ON_RESET transient memory.
     */
    public static final byte TYPE_RSA_CRT_PRIVATE_TRANSIENT_RESET = 24;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: RSA private key in Chinese Remainder Theorem form whose key
     * data is kept in CLEAR_ON_DESELECT transient memory.
     */
    public static final byte TYPE_RSA_CRT_PRIVATE_TRANSIENT_DESELECT = 25;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: DSA private key whose key data is kept in CLEAR_ON_RESET
     * transient memory.
     */
    public static final byte TYPE_DSA_PRIVATE_TRANSIENT_RESET = 26;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: DSA private key whose key data is kept in CLEAR_ON_DESELECT
     * transient memory.
     */
    public static final byte TYPE_DSA_PRIVATE_TRANSIENT_DESELECT = 27;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: EC private key over a binary field F(2^m) whose key data is
     * kept in CLEAR_ON_RESET transient memory.
     */
    public static final byte TYPE_EC_F2M_PRIVATE_TRANSIENT_RESET = 28;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: EC private key over a binary field F(2^m) whose key data is
     * kept in CLEAR_ON_DESELECT transient memory.
     */
    public static final byte TYPE_EC_F2M_PRIVATE_TRANSIENT_DESELECT = 29;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: EC private key over a prime field F(p) whose key data is
     * kept in CLEAR_ON_RESET transient memory.
     */
    public static final byte TYPE_EC_FP_PRIVATE_TRANSIENT_RESET = 30;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: EC private key over a prime field F(p) whose key data is
     * kept in CLEAR_ON_DESELECT transient memory.
     */
    public static final byte TYPE_EC_FP_PRIVATE_TRANSIENT_DESELECT = 31;
    /** Key type for {@code buildKey(byte, short, boolean)}: Diffie-Hellman public key in persistent memory. */
    public static final byte TYPE_DH_PUBLIC = 32;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: Diffie-Hellman public key whose key data is kept in
     * CLEAR_ON_DESELECT transient memory.
     */
    public static final byte TYPE_DH_PUBLIC_TRANSIENT_DESELECT = 33;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: Diffie-Hellman public key whose key data is kept in
     * CLEAR_ON_RESET transient memory.
     */
    public static final byte TYPE_DH_PUBLIC_TRANSIENT_RESET = 34;
    /** Key type for {@code buildKey(byte, short, boolean)}: Diffie-Hellman private key in persistent memory. */
    public static final byte TYPE_DH_PRIVATE = 35;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: Diffie-Hellman private key whose key data is kept in
     * CLEAR_ON_DESELECT transient memory.
     */
    public static final byte TYPE_DH_PRIVATE_TRANSIENT_DESELECT = 36;
    /**
     * Key type for {@code buildKey(byte, short, boolean)}: Diffie-Hellman private key whose key data is kept in
     * CLEAR_ON_RESET transient memory.
     */
    public static final byte TYPE_DH_PRIVATE_TRANSIENT_RESET = 37;

    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): DES or triple-DES
     * key.
     */
    public static final byte ALG_TYPE_DES = 1;
    /** Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): AES key. */
    public static final byte ALG_TYPE_AES = 2;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): DSA public key.
     */
    public static final byte ALG_TYPE_DSA_PUBLIC = 3;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): DSA private key.
     */
    public static final byte ALG_TYPE_DSA_PRIVATE = 4;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): EC public key
     * over a binary field F(2^m).
     */
    public static final byte ALG_TYPE_EC_F2M_PUBLIC = 5;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): EC private key
     * over a binary field F(2^m).
     */
    public static final byte ALG_TYPE_EC_F2M_PRIVATE = 6;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): EC public key
     * over a prime field F(p).
     */
    public static final byte ALG_TYPE_EC_FP_PUBLIC = 7;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): EC private key
     * over a prime field F(p).
     */
    public static final byte ALG_TYPE_EC_FP_PRIVATE = 8;
    /** Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): HMAC key. */
    public static final byte ALG_TYPE_HMAC = 9;
    /** Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): SEED key. */
    public static final byte ALG_TYPE_KOREAN_SEED = 10;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): RSA public key.
     */
    public static final byte ALG_TYPE_RSA_PUBLIC = 11;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): RSA private key
     * (modulus and exponent form).
     */
    public static final byte ALG_TYPE_RSA_PRIVATE = 12;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): RSA private key
     * in Chinese Remainder Theorem form.
     */
    public static final byte ALG_TYPE_RSA_CRT_PRIVATE = 13;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): Diffie-Hellman
     * public key.
     */
    public static final byte ALG_TYPE_DH_PUBLIC = 14;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)} (memory type given separately): Diffie-Hellman
     * private key.
     */
    public static final byte ALG_TYPE_DH_PRIVATE = 15;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)}: an object holding only EC domain parameters over
     * F(2^m), which keys built with {@code buildKeyWithSharedDomain} can share.
     */
    public static final byte ALG_TYPE_EC_F2M_PARAMETERS = 16;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)}: an object holding only EC domain parameters over
     * F(p), which keys built with {@code buildKeyWithSharedDomain} can share.
     */
    public static final byte ALG_TYPE_EC_FP_PARAMETERS = 17;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)}: an object holding only DSA domain parameters,
     * which keys built with {@code buildKeyWithSharedDomain} can share.
     */
    public static final byte ALG_TYPE_DSA_PARAMETERS = 18;
    /**
     * Algorithm type for {@code buildKey(byte, byte, short, boolean)}: an object holding only Diffie-Hellman domain
     * parameters, which keys built with {@code buildKeyWithSharedDomain} can share.
     */
    public static final byte ALG_TYPE_DH_PARAMETERS = 19;

    /** Key length in bits of a single-DES key (64, parity bits included). */
    public static final short LENGTH_DES = 64;
    /** Key length in bits of a two-key triple-DES key (128). */
    public static final short LENGTH_DES3_2KEY = 128;
    /** Key length in bits of a three-key triple-DES key (192). */
    public static final short LENGTH_DES3_3KEY = 192;
    /** Key length in bits of an RSA modulus: 512. */
    public static final short LENGTH_RSA_512 = 512;
    /** Key length in bits of an RSA modulus: 736. */
    public static final short LENGTH_RSA_736 = 736;
    /** Key length in bits of an RSA modulus: 768. */
    public static final short LENGTH_RSA_768 = 768;
    /** Key length in bits of an RSA modulus: 896. */
    public static final short LENGTH_RSA_896 = 896;
    /** Key length in bits of an RSA modulus: 1024. */
    public static final short LENGTH_RSA_1024 = 1024;
    /** Key length in bits of an RSA modulus: 1280. */
    public static final short LENGTH_RSA_1280 = 1280;
    /** Key length in bits of an RSA modulus: 1536. */
    public static final short LENGTH_RSA_1536 = 1536;
    /** Key length in bits of an RSA modulus: 1984. */
    public static final short LENGTH_RSA_1984 = 1984;
    /** Key length in bits of an RSA modulus: 2048. */
    public static final short LENGTH_RSA_2048 = 2048;
    /** Key length in bits of an RSA modulus: 3072. */
    public static final short LENGTH_RSA_3072 = 3072;
    /** Key length in bits of an RSA modulus: 4096. */
    public static final short LENGTH_RSA_4096 = 4096;
    /** Key length in bits of a DSA prime p: 512. */
    public static final short LENGTH_DSA_512 = 512;
    /** Key length in bits of a DSA prime p: 768. */
    public static final short LENGTH_DSA_768 = 768;
    /** Key length in bits of a DSA prime p: 1024. */
    public static final short LENGTH_DSA_1024 = 1024;
    /** Key length in bits of an EC key over a prime field F(p) (size of p): 112. */
    public static final short LENGTH_EC_FP_112 = 112;
    /** Key length in bits of an EC key over a binary field F(2^m) (m): 113. */
    public static final short LENGTH_EC_F2M_113 = 113;
    /** Key length in bits of an EC key over a prime field F(p) (size of p): 128. */
    public static final short LENGTH_EC_FP_128 = 128;
    /** Key length in bits of an EC key over a binary field F(2^m) (m): 131. */
    public static final short LENGTH_EC_F2M_131 = 131;
    /** Key length in bits of an EC key over a prime field F(p) (size of p): 160. */
    public static final short LENGTH_EC_FP_160 = 160;
    /** Key length in bits of an EC key over a binary field F(2^m) (m): 163. */
    public static final short LENGTH_EC_F2M_163 = 163;
    /** Key length in bits of an EC key over a prime field F(p) (size of p): 192. */
    public static final short LENGTH_EC_FP_192 = 192;
    /** Key length in bits of an EC key over a binary field F(2^m) (m): 193. */
    public static final short LENGTH_EC_F2M_193 = 193;
    /** Key length in bits of an EC key over a prime field F(p) (size of p): 224. */
    public static final short LENGTH_EC_FP_224 = 224;
    /** Key length in bits of an EC key over a prime field F(p) (size of p): 256. */
    public static final short LENGTH_EC_FP_256 = 256;
    /** Key length in bits of an EC key over a prime field F(p) (size of p): 384. */
    public static final short LENGTH_EC_FP_384 = 384;
    /** Key length in bits of an EC key over a prime field F(p) (size of p): 521. */
    public static final short LENGTH_EC_FP_521 = 521;
    /** Key length in bits of an AES key: 128. */
    public static final short LENGTH_AES_128 = 128;
    /** Key length in bits of an AES key: 192. */
    public static final short LENGTH_AES_192 = 192;
    /** Key length in bits of an AES key: 256. */
    public static final short LENGTH_AES_256 = 256;
    /** Key length in bits of a SEED key (128). */
    public static final short LENGTH_KOREAN_SEED_128 = 128;
    /** Length constant for HMAC keys used with SHA-1 (64-byte hash block). */
    public static final short LENGTH_HMAC_SHA_1_BLOCK_64 = 64;
    /** Length constant for HMAC keys used with SHA-256 (64-byte hash block). */
    public static final short LENGTH_HMAC_SHA_256_BLOCK_64 = 64;
    /** Length constant for HMAC keys used with SHA-384 (128-byte hash block). */
    public static final short LENGTH_HMAC_SHA_384_BLOCK_128 = 128;
    /** Length constant for HMAC keys used with SHA-512 (128-byte hash block). */
    public static final short LENGTH_HMAC_SHA_512_BLOCK_128 = 128;
    /** Key length in bits of a Diffie-Hellman modulus: 1024. */
    public static final short LENGTH_DH_1024 = 1024;
    /** Key length in bits of a Diffie-Hellman modulus: 2048. */
    public static final short LENGTH_DH_2048 = 2048;

    /**
     * Creates an uninitialized key object of the specified type and length.
     *
     * @param keyType the type of the key
     * @param keyLength the length of the key in bits
     * @param keyEncryption if true, the key is for encryption
     * @return the key object
     * @throws CryptoException with NO_SUCH_ALGORITHM if the requested key type or length is not supported
     */
    public static Key buildKey(byte keyType, short keyLength, boolean keyEncryption) throws CryptoException {
        throw new RuntimeException("stub");
    }

    /**
     * Creates an uninitialized key object, choosing the algorithm and the memory type separately.
     *
     * @param algorithmicKeyType one of the {@code ALG_TYPE_*} constants
     * @param keyMemoryType      a {@code JCSystem.MEMORY_TYPE_*} constant selecting where the key value is stored
     * @param keyLength          the key size in bits
     * @param keyEncryption      {@code true} to request a key implementation that is encrypted internally
     * @return the key object
     * @throws CryptoException with NO_SUCH_ALGORITHM if the combination is not supported
     */
    public static Key buildKey(byte algorithmicKeyType, byte keyMemoryType, short keyLength, boolean keyEncryption)
            throws CryptoException {
        throw new RuntimeException("stub");
    }

    /**
     * Creates an uninitialized key that shares the domain parameters of another key (for example the curve of
     * an EC key) instead of holding its own copy.
     *
     * @param algorithmicKeyType one of the {@code ALG_TYPE_*} constants
     * @param keyMemoryType      a {@code JCSystem.MEMORY_TYPE_*} constant
     * @param domainParameters   key whose domain parameters are shared; it must be fully initialized
     * @param keyEncryption      {@code true} to request a key implementation that is encrypted internally
     * @return the key object
     * @throws CryptoException with NO_SUCH_ALGORITHM if the combination is not supported, or ILLEGAL_VALUE if
     *                         the domain parameter key does not match the requested type
     */
    public static Key buildKeyWithSharedDomain(byte algorithmicKeyType, byte keyMemoryType, Key domainParameters,
            boolean keyEncryption) throws CryptoException {
        throw new RuntimeException("stub");
    }
}
