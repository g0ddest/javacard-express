package javacardx.crypto;

import javacard.security.CryptoException;
import javacard.security.Key;

/**
 * The Cipher class is the abstract base class for Cipher algorithms.
 *
 * <p>The {@code ALG_*} constants name complete algorithms for {@link #getInstance(byte, boolean)}. The
 * {@code CIPHER_*} and {@code PAD_*} constants name the cipher primitive and the padding scheme for
 * {@link #getInstance(byte, byte, boolean)}; the {@code PAD_*} constants are also used by
 * {@code javacard.security.Signature}.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class Cipher {

    /**
     * Constructor for algorithm implementations; applets obtain instances through {@code getInstance}.
     */
    protected Cipher() {
        throw new RuntimeException("stub");
    }

    /** DES or triple DES (by key length) in CBC mode without padding (input must be block aligned). */
    public static final byte ALG_DES_CBC_NOPAD = 1;
    /** DES or triple DES (by key length) in CBC mode with ISO/IEC 9797-1 padding method 1. */
    public static final byte ALG_DES_CBC_ISO9797_M1 = 2;
    /** DES or triple DES (by key length) in CBC mode with ISO/IEC 9797-1 padding method 2. */
    public static final byte ALG_DES_CBC_ISO9797_M2 = 3;
    /** DES or triple DES (by key length) in CBC mode with PKCS #5 padding. */
    public static final byte ALG_DES_CBC_PKCS5 = 4;
    /** DES or triple DES (by key length) in ECB mode without padding (input must be block aligned). */
    public static final byte ALG_DES_ECB_NOPAD = 5;
    /** DES or triple DES (by key length) in ECB mode with ISO/IEC 9797-1 padding method 1. */
    public static final byte ALG_DES_ECB_ISO9797_M1 = 6;
    /** DES or triple DES (by key length) in ECB mode with ISO/IEC 9797-1 padding method 2. */
    public static final byte ALG_DES_ECB_ISO9797_M2 = 7;
    /** DES or triple DES (by key length) in ECB mode with PKCS #5 padding. */
    public static final byte ALG_DES_ECB_PKCS5 = 8;
    /** RSA with ISO/IEC 14888 padding. */
    public static final byte ALG_RSA_ISO14888 = 9;
    /** RSA with PKCS #1 v1.5 padding. */
    public static final byte ALG_RSA_PKCS1 = 10;
    /** RSA with ISO/IEC 9796 padding. */
    public static final byte ALG_RSA_ISO9796 = 11;
    /** Raw RSA without padding; the input is as long as the modulus. */
    public static final byte ALG_RSA_NOPAD = 12;
    /** AES in CBC mode without padding (input must be block aligned). */
    public static final byte ALG_AES_BLOCK_128_CBC_NOPAD = 13;
    /** AES in ECB mode without padding (input must be block aligned). */
    public static final byte ALG_AES_BLOCK_128_ECB_NOPAD = 14;
    /** RSA with PKCS #1 OAEP padding. */
    public static final byte ALG_RSA_PKCS1_OAEP = 15;
    /** SEED in ECB mode without padding (input must be block aligned). */
    public static final byte ALG_KOREAN_SEED_ECB_NOPAD = 16;
    /** SEED in CBC mode without padding (input must be block aligned). */
    public static final byte ALG_KOREAN_SEED_CBC_NOPAD = 17;
    /** Rijndael with a 192-bit block in CBC mode without padding (input must be block aligned). */
    public static final byte ALG_AES_BLOCK_192_CBC_NOPAD = 18;
    /** Rijndael with a 192-bit block in ECB mode without padding (input must be block aligned). */
    public static final byte ALG_AES_BLOCK_192_ECB_NOPAD = 19;
    /** Rijndael with a 256-bit block in CBC mode without padding (input must be block aligned). */
    public static final byte ALG_AES_BLOCK_256_CBC_NOPAD = 20;
    /** Rijndael with a 256-bit block in ECB mode without padding (input must be block aligned). */
    public static final byte ALG_AES_BLOCK_256_ECB_NOPAD = 21;
    /** AES in CBC mode with ISO/IEC 9797-1 padding method 1. */
    public static final byte ALG_AES_CBC_ISO9797_M1 = 22;
    /** AES in CBC mode with ISO/IEC 9797-1 padding method 2. */
    public static final byte ALG_AES_CBC_ISO9797_M2 = 23;
    /** AES in CBC mode with PKCS #5 padding. */
    public static final byte ALG_AES_CBC_PKCS5 = 24;
    /** AES in ECB mode with ISO/IEC 9797-1 padding method 1. */
    public static final byte ALG_AES_ECB_ISO9797_M1 = 25;
    /** AES in ECB mode with ISO/IEC 9797-1 padding method 2. */
    public static final byte ALG_AES_ECB_ISO9797_M2 = 26;
    /** AES in ECB mode with PKCS #5 padding. */
    public static final byte ALG_AES_ECB_PKCS5 = 27;
    /** AES in counter mode. */
    public static final byte ALG_AES_CTR = (byte) 0xF0;

    /** Cipher selector for {@code getInstance(byte, byte, boolean)}: AES in CBC mode. */
    public static final byte CIPHER_AES_CBC = 1;
    /** Cipher selector for {@code getInstance(byte, byte, boolean)}: AES in ECB mode. */
    public static final byte CIPHER_AES_ECB = 2;
    /** Cipher selector for {@code getInstance(byte, byte, boolean)}: DES or triple DES in CBC mode. */
    public static final byte CIPHER_DES_CBC = 3;
    /** Cipher selector for {@code getInstance(byte, byte, boolean)}: DES or triple DES in ECB mode. */
    public static final byte CIPHER_DES_ECB = 4;
    /** Cipher selector for {@code getInstance(byte, byte, boolean)}: SEED in CBC mode. */
    public static final byte CIPHER_KOREAN_SEED_CBC = 5;
    /** Cipher selector for {@code getInstance(byte, byte, boolean)}: SEED in ECB mode. */
    public static final byte CIPHER_KOREAN_SEED_ECB = 6;
    /** Cipher selector for {@code getInstance(byte, byte, boolean)}: RSA. */
    public static final byte CIPHER_RSA = 7;

    /** No padding scheme applies (for example for a primitive that does not pad). */
    public static final byte PAD_NULL = 0;
    /** Padding selector: no padding; the input must be block aligned. */
    public static final byte PAD_NOPAD = 1;
    /** Padding selector: ISO/IEC 9797-1 padding method 1 (zero bytes). */
    public static final byte PAD_ISO9797_M1 = 2;
    /** Padding selector: ISO/IEC 9797-1 padding method 2 ('80' followed by zero bytes). */
    public static final byte PAD_ISO9797_M2 = 3;
    /** Padding selector: ISO/IEC 9797-1 padding method 1 for MAC algorithm 3. */
    public static final byte PAD_ISO9797_1_M1_ALG3 = 4;
    /** Padding selector: ISO/IEC 9797-1 padding method 2 for MAC algorithm 3. */
    public static final byte PAD_ISO9797_1_M2_ALG3 = 5;
    /** Padding selector: PKCS #5 padding. */
    public static final byte PAD_PKCS5 = 6;
    /** Padding selector: PKCS #1 v1.5 padding. */
    public static final byte PAD_PKCS1 = 7;
    /** Padding selector: PKCS #1 PSS encoding. */
    public static final byte PAD_PKCS1_PSS = 8;
    /** Padding selector: PKCS #1 OAEP padding. */
    public static final byte PAD_PKCS1_OAEP = 9;
    /** Padding selector: PKCS #1 OAEP padding using SHA-224. */
    public static final byte PAD_PKCS1_OAEP_SHA224 = 13;
    /** Padding selector: PKCS #1 OAEP padding using SHA-256. */
    public static final byte PAD_PKCS1_OAEP_SHA256 = 14;
    /** Padding selector: PKCS #1 OAEP padding using SHA3-84. */
    public static final byte PAD_PKCS1_OAEP_SHA384 = 15;
    /** Padding selector: PKCS #1 OAEP padding using SHA-512. */
    public static final byte PAD_PKCS1_OAEP_SHA512 = 16;
    /** Padding selector: PKCS #1 OAEP padding using SHA3-224. */
    public static final byte PAD_PKCS1_OAEP_SHA3_224 = 17;
    /** Padding selector: PKCS #1 OAEP padding using SHA3-256. */
    public static final byte PAD_PKCS1_OAEP_SHA3_256 = 18;
    /** Padding selector: PKCS #1 OAEP padding using SHA3-384. */
    public static final byte PAD_PKCS1_OAEP_SHA3_384 = 19;
    /** Padding selector: PKCS #1 OAEP padding using SHA3-512. */
    public static final byte PAD_PKCS1_OAEP_SHA3_512 = 20;
    /** Padding selector: ISO/IEC 9796 padding. */
    public static final byte PAD_ISO9796 = 10;
    /** Padding selector: ISO/IEC 9796-2 padding with message recovery. */
    public static final byte PAD_ISO9796_MR = 11;
    /** Padding selector: padding as specified in RFC 2409. */
    public static final byte PAD_RFC2409 = 12;

    /** Mode for {@code init}: decrypt. */
    public static final byte MODE_DECRYPT = 1;
    /** Mode for {@code init}: encrypt. */
    public static final byte MODE_ENCRYPT = 2;

    /**
     * Creates a Cipher object instance of the selected algorithm.
     *
     * @param algorithm       the desired cipher algorithm
     * @param externalAccess  indicates whether the instance will be shared across contexts
     * @return the Cipher object instance of the requested algorithm
     * @throws CryptoException if the requested algorithm is not supported
     */
    public static final Cipher getInstance(byte algorithm, boolean externalAccess) throws CryptoException {
        throw new RuntimeException("stub");
    }

    /**
     * Creates a Cipher instance from a cipher primitive and a padding scheme.
     *
     * @param cipherAlgorithm  one of the {@code CIPHER_*} constants
     * @param paddingAlgorithm one of the {@code PAD_*} constants
     * @param externalAccess   {@code true} if the instance may be used from other applet contexts
     * @return the Cipher instance
     * @throws CryptoException with NO_SUCH_ALGORITHM if the combination is not supported
     */
    public static final Cipher getInstance(byte cipherAlgorithm, byte paddingAlgorithm, boolean externalAccess)
            throws CryptoException {
        throw new RuntimeException("stub");
    }

    /**
     * Initializes the Cipher object with the appropriate Key.
     *
     * @param theKey  the key object to use for encrypting or decrypting
     * @param theMode one of MODE_DECRYPT or MODE_ENCRYPT
     * @throws CryptoException if the key or mode is invalid
     */
    public abstract void init(Key theKey, byte theMode) throws CryptoException;

    /**
     * Initializes the Cipher object with the appropriate Key and algorithm specific parameters.
     *
     * @param theKey  the key object to use for encrypting or decrypting
     * @param theMode one of MODE_DECRYPT or MODE_ENCRYPT
     * @param bArray  byte array containing algorithm specific initialization info
     * @param bOff    offset within bArray where the initialization info begins
     * @param bLen    byte length of the initialization info
     * @throws CryptoException if the key, mode, or parameters are invalid
     */
    public abstract void init(Key theKey, byte theMode, byte[] bArray, short bOff, short bLen) throws CryptoException;

    /**
     * Gets the cipher algorithm.
     *
     * @return the algorithm code defined above
     */
    public abstract byte getAlgorithm();

    /**
     * Returns the cipher primitive of this instance.
     *
     * @return one of the {@code CIPHER_*} constants
     */
    public abstract byte getCipherAlgorithm();

    /**
     * Returns the padding scheme of this instance.
     *
     * @return one of the {@code PAD_*} constants
     */
    public abstract byte getPaddingAlgorithm();

    /**
     * Generates encrypted/decrypted output from all/last input data.
     *
     * @param inBuff    the input buffer of data to be encrypted/decrypted
     * @param inOffset  the offset into the input buffer at which to begin encryption/decryption
     * @param inLength  the byte length to be encrypted/decrypted
     * @param outBuff   the output buffer
     * @param outOffset the offset into the output buffer where the resulting output data begins
     * @return number of bytes output in outBuff
     * @throws CryptoException if a cipher operation error occurs
     */
    public abstract short doFinal(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset)
            throws CryptoException;

    /**
     * Generates encrypted/decrypted output from input data.
     *
     * @param inBuff    the input buffer of data to be encrypted/decrypted
     * @param inOffset  the offset into the input buffer at which to begin encryption/decryption
     * @param inLength  the byte length to be encrypted/decrypted
     * @param outBuff   the output buffer
     * @param outOffset the offset into the output buffer where the resulting output data begins
     * @return number of bytes output in outBuff
     * @throws CryptoException if a cipher operation error occurs
     */
    public abstract short update(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset)
            throws CryptoException;

    /**
     * Cipher for a single message; instances come from a platform pool through {@link #open(byte, byte)} and
     * are returned to it with {@link #close()}, so no persistent memory is consumed. The data must be processed
     * in one {@code doFinal} call.
     */
    public static final class OneShot extends Cipher {

        private OneShot() {
        }

        /**
         * Takes an instance for the given cipher primitive and padding from the platform pool.
         *
         * @param cipherAlgorithm  one of the {@code Cipher.CIPHER_*} constants
         * @param paddingAlgorithm one of the {@code Cipher.PAD_*} constants
         * @return an open instance
         * @throws CryptoException with NO_SUCH_ALGORITHM if the combination is not supported, or ILLEGAL_USE if
         *                         no instance is available
         */
        public static final OneShot open(byte cipherAlgorithm, byte paddingAlgorithm) throws CryptoException {
            throw new RuntimeException("stub");
        }

        /**
         * Returns this instance to the platform pool; it must not be used afterwards.
         */
        public void close() {
            throw new RuntimeException("stub");
        }

        /**
         * Not supported: a one-shot cipher processes all data in {@code doFinal}.
         *
         * @param inBuff    input buffer
         * @param inOffset  offset into input buffer
         * @param inLength  length of input data
         * @param outBuff   output buffer
         * @param outOffset offset into output buffer
         * @return never returns normally
         * @throws CryptoException with ILLEGAL_USE always
         */
        @Override
        public short update(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset)
                throws CryptoException {
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
        public byte getAlgorithm() {
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
        public short doFinal(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset)
                throws CryptoException {
            throw new RuntimeException("stub");
        }
    }
}
