package javacard.security;

/**
 * The KeyBuilder class is a key object factory.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class KeyBuilder {

    public static final byte TYPE_DES_TRANSIENT_RESET = 1;
    public static final byte TYPE_DES_TRANSIENT_DESELECT = 2;
    public static final byte TYPE_DES = 3;
    public static final byte TYPE_RSA_PUBLIC = 4;
    public static final byte TYPE_RSA_PRIVATE = 5;
    public static final byte TYPE_RSA_CRT_PRIVATE = 6;
    public static final byte TYPE_DSA_PUBLIC = 7;
    public static final byte TYPE_DSA_PRIVATE = 8;
    public static final byte TYPE_EC_F2M_PUBLIC = 9;
    public static final byte TYPE_EC_F2M_PRIVATE = 10;
    public static final byte TYPE_EC_FP_PUBLIC = 11;
    public static final byte TYPE_EC_FP_PRIVATE = 12;
    public static final byte TYPE_AES_TRANSIENT_RESET = 13;
    public static final byte TYPE_AES_TRANSIENT_DESELECT = 14;
    public static final byte TYPE_AES = 15;
    public static final byte TYPE_HMAC_TRANSIENT_RESET = 18;
    public static final byte TYPE_HMAC_TRANSIENT_DESELECT = 19;
    public static final byte TYPE_HMAC = 20;

    public static final short LENGTH_DES = 64;
    public static final short LENGTH_DES3_2KEY = 128;
    public static final short LENGTH_DES3_3KEY = 192;
    public static final short LENGTH_RSA_512 = 512;
    public static final short LENGTH_RSA_1024 = 1024;
    public static final short LENGTH_RSA_2048 = 2048;
    public static final short LENGTH_EC_FP_128 = 128;
    public static final short LENGTH_EC_FP_160 = 160;
    public static final short LENGTH_EC_FP_192 = 192;
    public static final short LENGTH_EC_FP_224 = 224;
    public static final short LENGTH_EC_FP_256 = 256;
    public static final short LENGTH_EC_FP_384 = 384;
    public static final short LENGTH_EC_FP_521 = 521;
    public static final short LENGTH_AES_128 = 128;
    public static final short LENGTH_AES_192 = 192;
    public static final short LENGTH_AES_256 = 256;
    public static final short LENGTH_HMAC_SHA_1_BLOCK_64 = 64;
    public static final short LENGTH_HMAC_SHA_256_BLOCK_64 = 64;
    public static final short LENGTH_HMAC_SHA_384_BLOCK_128 = 128;
    public static final short LENGTH_HMAC_SHA_512_BLOCK_128 = 128;

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
        return null;
    }
}
