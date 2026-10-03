package javacard.security;

/**
 * The Signature class is the base class for signature and MAC algorithms.
 *
 * <p>The {@code ALG_*} constants name complete algorithms for {@link #getInstance(byte, boolean)}. The
 * {@code SIG_CIPHER_*} constants name the signature primitive for
 * {@link #getInstance(byte, byte, byte, boolean)}, which combines it with a hash algorithm
 * ({@code MessageDigest.ALG_*}) and a padding scheme ({@code javacardx.crypto.Cipher.PAD_*}).
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class Signature {

    /**
     * Constructor for algorithm implementations; applets obtain instances through {@code getInstance}.
     */
    protected Signature() {
        throw new RuntimeException("stub");
    }

    /** 4-byte CBC-MAC with DES or triple DES (by key length); input must be block aligned. */
    public static final byte ALG_DES_MAC4_NOPAD = 1;
    /** 8-byte CBC-MAC with DES or triple DES (by key length); input must be block aligned. */
    public static final byte ALG_DES_MAC8_NOPAD = 2;
    /** 4-byte DES or triple-DES CBC-MAC, ISO/IEC 9797-1 padding method 1. */
    public static final byte ALG_DES_MAC4_ISO9797_M1 = 3;
    /** 8-byte DES or triple-DES CBC-MAC, ISO/IEC 9797-1 padding method 1. */
    public static final byte ALG_DES_MAC8_ISO9797_M1 = 4;
    /** 4-byte DES or triple-DES CBC-MAC, ISO/IEC 9797-1 padding method 2. */
    public static final byte ALG_DES_MAC4_ISO9797_M2 = 5;
    /** 8-byte DES or triple-DES CBC-MAC, ISO/IEC 9797-1 padding method 2. */
    public static final byte ALG_DES_MAC8_ISO9797_M2 = 6;
    /** 4-byte DES or triple-DES CBC-MAC with PKCS #5 padding. */
    public static final byte ALG_DES_MAC4_PKCS5 = 7;
    /** 8-byte DES or triple-DES CBC-MAC with PKCS #5 padding. */
    public static final byte ALG_DES_MAC8_PKCS5 = 8;
    /** RSA signature of a SHA-1 digest with ISO/IEC 9796 padding. */
    public static final byte ALG_RSA_SHA_ISO9796 = 9;
    /** RSA signature of a SHA-1 digest with PKCS #1 v1.5 padding. */
    public static final byte ALG_RSA_SHA_PKCS1 = 10;
    /** RSA signature of an MD5 digest with PKCS #1 v1.5 padding. */
    public static final byte ALG_RSA_MD5_PKCS1 = 11;
    /** RSA signature of a RIPEMD-160 digest with ISO/IEC 9796 padding. */
    public static final byte ALG_RSA_RIPEMD160_ISO9796 = 12;
    /** RSA signature of a RIPEMD-160 digest with PKCS #1 v1.5 padding. */
    public static final byte ALG_RSA_RIPEMD160_PKCS1 = 13;
    /** DSA signature of a SHA-1 digest. */
    public static final byte ALG_DSA_SHA = 14;
    /** RSA signature of a SHA-1 digest padded as specified in RFC 2409. */
    public static final byte ALG_RSA_SHA_RFC2409 = 15;
    /** RSA signature of an MD5 digest padded as specified in RFC 2409. */
    public static final byte ALG_RSA_MD5_RFC2409 = 16;
    /** ECDSA signature of a SHA-1 digest. */
    public static final byte ALG_ECDSA_SHA = 17;
    /** 16-byte AES CBC-MAC; input must be block aligned. */
    public static final byte ALG_AES_MAC_128_NOPAD = 18;
    /** 16-byte AES-CMAC (NIST SP 800-38B). */
    public static final byte ALG_AES_CMAC_128 = 49;
    /** 4-byte ISO/IEC 9797-1 MAC algorithm 3 (double-length DES key), padding method 2. */
    public static final byte ALG_DES_MAC4_ISO9797_1_M2_ALG3 = 19;
    /** 8-byte ISO/IEC 9797-1 MAC algorithm 3 (double-length DES key), padding method 2. */
    public static final byte ALG_DES_MAC8_ISO9797_1_M2_ALG3 = 20;
    /** RSA-PSS signature (PKCS #1) of a SHA-1 digest. */
    public static final byte ALG_RSA_SHA_PKCS1_PSS = 21;
    /** RSA-PSS signature (PKCS #1) of an MD5 digest. */
    public static final byte ALG_RSA_MD5_PKCS1_PSS = 22;
    /** RSA-PSS signature (PKCS #1) of a RIPEMD-160 digest. */
    public static final byte ALG_RSA_RIPEMD160_PKCS1_PSS = 23;
    /** HMAC (RFC 2104) with SHA-1. */
    public static final byte ALG_HMAC_SHA1 = 24;
    /** HMAC (RFC 2104) with SHA-256. */
    public static final byte ALG_HMAC_SHA_256 = 25;
    /** HMAC (RFC 2104) with SHA-384. */
    public static final byte ALG_HMAC_SHA_384 = 26;
    /** HMAC (RFC 2104) with SHA-512. */
    public static final byte ALG_HMAC_SHA_512 = 27;
    /** HMAC (RFC 2104) with MD5. */
    public static final byte ALG_HMAC_MD5 = 28;
    /** HMAC (RFC 2104) with RIPEMD-160. */
    public static final byte ALG_HMAC_RIPEMD160 = 29;
    /** RSA signature with message recovery (ISO/IEC 9796-2) using SHA-1. */
    public static final byte ALG_RSA_SHA_ISO9796_MR = 30;
    /** RSA signature with message recovery (ISO/IEC 9796-2) using RIPEMD-160. */
    public static final byte ALG_RSA_RIPEMD160_ISO9796_MR = 31;
    /** 16-byte SEED CBC-MAC; input must be block aligned. */
    public static final byte ALG_KOREAN_SEED_MAC_NOPAD = 32;
    /** ECDSA signature of a SHA-256 digest. */
    public static final byte ALG_ECDSA_SHA_256 = 33;
    /** ECDSA signature of a SHA-384 digest. */
    public static final byte ALG_ECDSA_SHA_384 = 34;
    /** CBC-MAC with Rijndael using a 192-bit block; input must be block aligned. */
    public static final byte ALG_AES_MAC_192_NOPAD = 35;
    /** CBC-MAC with Rijndael using a 256-bit block; input must be block aligned. */
    public static final byte ALG_AES_MAC_256_NOPAD = 36;
    /** ECDSA signature of a SHA-224 digest. */
    public static final byte ALG_ECDSA_SHA_224 = 37;
    /** ECDSA signature of a SHA-512 digest. */
    public static final byte ALG_ECDSA_SHA_512 = 38;
    /** RSA signature of a SHA-224 digest with PKCS #1 v1.5 padding. */
    public static final byte ALG_RSA_SHA_224_PKCS1 = 39;
    /** RSA signature of a SHA-256 digest with PKCS #1 v1.5 padding. */
    public static final byte ALG_RSA_SHA_256_PKCS1 = 40;
    /** RSA signature of a SHA-384 digest with PKCS #1 v1.5 padding. */
    public static final byte ALG_RSA_SHA_384_PKCS1 = 41;
    /** RSA signature of a SHA-512 digest with PKCS #1 v1.5 padding. */
    public static final byte ALG_RSA_SHA_512_PKCS1 = 42;
    /** RSA-PSS signature (PKCS #1) of a SHA-224 digest. */
    public static final byte ALG_RSA_SHA_224_PKCS1_PSS = 43;
    /** RSA-PSS signature (PKCS #1) of a SHA-256 digest. */
    public static final byte ALG_RSA_SHA_256_PKCS1_PSS = 44;
    /** RSA-PSS signature (PKCS #1) of a SHA-384 digest. */
    public static final byte ALG_RSA_SHA_384_PKCS1_PSS = 45;
    /** RSA-PSS signature (PKCS #1) of a SHA-512 digest. */
    public static final byte ALG_RSA_SHA_512_PKCS1_PSS = 46;
    /** 4-byte ISO/IEC 9797-1 MAC algorithm 3 (double-length DES key), padding method 1. */
    public static final byte ALG_DES_MAC4_ISO9797_1_M1_ALG3 = 47;
    /** 8-byte ISO/IEC 9797-1 MAC algorithm 3 (double-length DES key), padding method 1. */
    public static final byte ALG_DES_MAC8_ISO9797_1_M1_ALG3 = 48;

    /** Cipher selector for {@code getInstance(byte, byte, byte, boolean)}: 4-byte DES MAC. */
    public static final byte SIG_CIPHER_DES_MAC4 = 1;
    /** Cipher selector for {@code getInstance(byte, byte, byte, boolean)}: 8-byte DES MAC. */
    public static final byte SIG_CIPHER_DES_MAC8 = 2;
    /** Cipher selector for {@code getInstance(byte, byte, byte, boolean)}: RSA. */
    public static final byte SIG_CIPHER_RSA = 3;
    /** Cipher selector for {@code getInstance(byte, byte, byte, boolean)}: DSA. */
    public static final byte SIG_CIPHER_DSA = 4;
    /** Cipher selector for {@code getInstance(byte, byte, byte, boolean)}: ECDSA. */
    public static final byte SIG_CIPHER_ECDSA = 5;
    /** ECDSA whose signature is the plain concatenation of r and s instead of a DER sequence. */
    public static final byte SIG_CIPHER_ECDSA_PLAIN = 9;
    /** Cipher selector for {@code getInstance(byte, byte, byte, boolean)}: 16-byte AES CBC-MAC. */
    public static final byte SIG_CIPHER_AES_MAC128 = 6;
    /** Cipher selector for {@code getInstance(byte, byte, byte, boolean)}: AES-CMAC. */
    public static final byte SIG_CIPHER_AES_CMAC128 = 10;
    /** Cipher selector for {@code getInstance(byte, byte, byte, boolean)}: HMAC. */
    public static final byte SIG_CIPHER_HMAC = 7;
    /** Cipher selector for {@code getInstance(byte, byte, byte, boolean)}: SEED MAC. */
    public static final byte SIG_CIPHER_KOREAN_SEED_MAC = 8;

    /** Mode for {@code init}: compute signatures or MACs. */
    public static final byte MODE_SIGN = 1;
    /** Mode for {@code init}: verify signatures or MACs. */
    public static final byte MODE_VERIFY = 2;

    /**
     * Creates a Signature instance for the specified algorithm.
     *
     * @param algorithm the algorithm type
     * @param externalAccess if true, the instance can be accessed from any applet context
     * @return the Signature instance
     * @throws CryptoException with NO_SUCH_ALGORITHM if the requested algorithm is not supported
     */
    public static final Signature getInstance(byte algorithm, boolean externalAccess) throws CryptoException {
        throw new RuntimeException("stub");
    }

    /**
     * Creates a Signature instance from its building blocks.
     *
     * @param messageDigestAlgorithm a {@code MessageDigest.ALG_*} constant, or {@code MessageDigest.ALG_NULL}
     * @param cipherAlgorithm        one of the {@code SIG_CIPHER_*} constants
     * @param paddingAlgorithm       a {@code javacardx.crypto.Cipher.PAD_*} constant
     * @param externalAccess         {@code true} if the instance may be used from other applet contexts
     * @return the Signature instance
     * @throws CryptoException with NO_SUCH_ALGORITHM if the combination is not supported
     */
    public static final Signature getInstance(byte messageDigestAlgorithm, byte cipherAlgorithm,
            byte paddingAlgorithm, boolean externalAccess) throws CryptoException {
        throw new RuntimeException("stub");
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
     * Presets the intermediate hash value of the message digest used by this signature, so that signing or
     * verification can continue from a hash state computed elsewhere.
     *
     * @param initialDigestBuf     buffer holding the intermediate hash value
     * @param initialDigestOffset  offset of the hash value
     * @param initialDigestLength  length of the hash value
     * @param digestedMsgLenBuf    buffer holding the number of bytes already hashed, big-endian
     * @param digestedMsgLenOffset offset of that number
     * @param digestedMsgLenLength length of that number in bytes
     * @throws CryptoException with ILLEGAL_USE if the algorithm does not hash its input, or ILLEGAL_VALUE if a
     *                         length is not valid
     */
    public abstract void setInitialDigest(byte[] initialDigestBuf, short initialDigestOffset,
            short initialDigestLength, byte[] digestedMsgLenBuf, short digestedMsgLenOffset,
            short digestedMsgLenLength) throws CryptoException;

    /**
     * Returns the algorithm type.
     *
     * @return the algorithm type
     */
    public abstract byte getAlgorithm();

    /**
     * Returns the hash algorithm used by this signature.
     *
     * @return a {@code MessageDigest.ALG_*} constant, {@code MessageDigest.ALG_NULL} if no hash is involved
     */
    public abstract byte getMessageDigestAlgorithm();

    /**
     * Returns the signature primitive used by this signature.
     *
     * @return one of the {@code SIG_CIPHER_*} constants
     */
    public abstract byte getCipherAlgorithm();

    /**
     * Returns the padding scheme used by this signature.
     *
     * @return a {@code javacardx.crypto.Cipher.PAD_*} constant
     */
    public abstract byte getPaddingAlgorithm();

    /**
     * Returns the byte length of the signature data.
     *
     * @return the signature length
     * @throws CryptoException with INVALID_INIT if not initialized
     */
    public abstract short getLength() throws CryptoException;

    /**
     * Accumulates input data for signing or verification.
     *
     * @param inBuff input buffer
     * @param inOffset offset into input buffer
     * @param inLength length of input data
     * @throws CryptoException if update fails
     */
    public abstract void update(byte[] inBuff, short inOffset, short inLength) throws CryptoException;

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
    public abstract short sign(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset)
            throws CryptoException;

    /**
     * Signs a hash value that was computed elsewhere, instead of hashing a message.
     *
     * @param hashBuff   buffer holding the hash value
     * @param hashOffset offset of the hash value
     * @param hashLength length of the hash value; it must match the hash algorithm of this signature
     * @param sigBuff    buffer receiving the signature
     * @param sigOffset  offset of the signature in {@code sigBuff}
     * @return the length of the signature
     * @throws CryptoException with ILLEGAL_USE if the algorithm does not support pre-computed hashes, or
     *                         ILLEGAL_VALUE if the hash length is wrong
     */
    public abstract short signPreComputedHash(byte[] hashBuff, short hashOffset, short hashLength, byte[] sigBuff,
            short sigOffset) throws CryptoException;

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
    public abstract boolean verify(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset,
            short sigLength) throws CryptoException;

    /**
     * Verifies a signature over a hash value that was computed elsewhere.
     *
     * @param hashBuff   buffer holding the hash value
     * @param hashOffset offset of the hash value
     * @param hashLength length of the hash value; it must match the hash algorithm of this signature
     * @param sigBuff    buffer holding the signature
     * @param sigOffset  offset of the signature
     * @param sigLength  length of the signature
     * @return {@code true} if the signature is valid
     * @throws CryptoException with ILLEGAL_USE if the algorithm does not support pre-computed hashes, or
     *                         ILLEGAL_VALUE if the hash length is wrong
     */
    public abstract boolean verifyPreComputedHash(byte[] hashBuff, short hashOffset, short hashLength,
            byte[] sigBuff, short sigOffset, short sigLength) throws CryptoException;

    /**
     * Signature for a single message; instances come from a platform pool through
     * {@link #open(byte, byte, byte)} and are returned to it with {@link #close()}, so no persistent memory is
     * consumed. The message must be processed in one {@code sign} or {@code verify} call.
     */
    public static final class OneShot extends Signature {

        private OneShot() {
        }

        /**
         * Takes an instance for the given algorithm from the platform pool.
         *
         * @param messageDigestAlgorithm a {@code MessageDigest.ALG_*} constant, or {@code MessageDigest.ALG_NULL}
         * @param cipherAlgorithm        one of the {@code Signature.SIG_CIPHER_*} constants
         * @param paddingAlgorithm       a {@code javacardx.crypto.Cipher.PAD_*} constant
         * @return an open instance
         * @throws CryptoException with NO_SUCH_ALGORITHM if the combination is not supported, or ILLEGAL_USE if no
         *                         instance is available
         */
        public static final OneShot open(byte messageDigestAlgorithm, byte cipherAlgorithm, byte paddingAlgorithm)
                throws CryptoException {
            throw new RuntimeException("stub");
        }

        /**
         * Returns this instance to the platform pool; it must not be used afterwards.
         */
        public void close() {
            throw new RuntimeException("stub");
        }

        /**
         * Not supported: a one-shot signature processes the whole message in {@code sign} or {@code verify}.
         *
         * @param inBuff   input buffer
         * @param inOffset offset into input buffer
         * @param inLength length of input data
         * @throws CryptoException with ILLEGAL_USE always
         */
        @Override
        public final void update(byte[] inBuff, short inOffset, short inLength) throws CryptoException {
            throw new RuntimeException("stub");
        }

        @Override
        public void init(Key theKey, byte theMode) throws CryptoException {
            throw new RuntimeException("stub");
        }

        @Override
        public void init(Key theKey, byte theMode, byte[] bArray, short bOff, short bLen) throws CryptoException {
            throw new RuntimeException("stub");
        }

        @Override
        public void setInitialDigest(byte[] initialDigestBuf, short initialDigestOffset,
                short initialDigestLength, byte[] digestedMsgLenBuf, short digestedMsgLenOffset,
                short digestedMsgLenLength) throws CryptoException {
            throw new RuntimeException("stub");
        }

        @Override
        public byte getAlgorithm() {
            throw new RuntimeException("stub");
        }

        @Override
        public byte getMessageDigestAlgorithm() {
            throw new RuntimeException("stub");
        }

        @Override
        public byte getCipherAlgorithm() {
            throw new RuntimeException("stub");
        }

        @Override
        public byte getPaddingAlgorithm() {
            throw new RuntimeException("stub");
        }

        @Override
        public short getLength() throws CryptoException {
            throw new RuntimeException("stub");
        }

        @Override
        public short sign(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset)
                throws CryptoException {
            throw new RuntimeException("stub");
        }

        @Override
        public short signPreComputedHash(byte[] hashBuff, short hashOffset, short hashLength, byte[] sigBuff,
                short sigOffset) throws CryptoException {
            throw new RuntimeException("stub");
        }

        @Override
        public boolean verify(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset,
                short sigLength) throws CryptoException {
            throw new RuntimeException("stub");
        }

        @Override
        public boolean verifyPreComputedHash(byte[] hashBuff, short hashOffset, short hashLength,
                byte[] sigBuff, short sigOffset, short sigLength) throws CryptoException {
            throw new RuntimeException("stub");
        }
    }
}
