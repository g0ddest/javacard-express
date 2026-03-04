package javacardx.crypto;

import javacard.security.CryptoException;
import javacard.security.Key;

/**
 * The Cipher class is the abstract base class for Cipher algorithms.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class Cipher {

    public static final byte ALG_DES_CBC_NOPAD = 1;
    public static final byte ALG_DES_CBC_ISO9797_M1 = 2;
    public static final byte ALG_DES_CBC_ISO9797_M2 = 3;
    public static final byte ALG_DES_CBC_PKCS5 = 4;
    public static final byte ALG_DES_ECB_NOPAD = 5;
    public static final byte ALG_DES_ECB_ISO9797_M1 = 6;
    public static final byte ALG_DES_ECB_ISO9797_M2 = 7;
    public static final byte ALG_DES_ECB_PKCS5 = 8;
    public static final byte ALG_RSA_ISO14888 = 9;
    public static final byte ALG_RSA_PKCS1 = 10;
    public static final byte ALG_RSA_ISO9796 = 11;
    public static final byte ALG_RSA_NOPAD = 12;
    public static final byte ALG_AES_BLOCK_128_CBC_NOPAD = 13;
    public static final byte ALG_AES_BLOCK_128_ECB_NOPAD = 14;
    public static final byte ALG_RSA_PKCS1_OAEP = 15;
    public static final byte ALG_AES_CBC_ISO9797_M1 = 16;
    public static final byte ALG_AES_CBC_ISO9797_M2 = 17;
    public static final byte ALG_AES_CBC_PKCS5 = 18;
    public static final byte ALG_AES_ECB_ISO9797_M1 = 19;
    public static final byte ALG_AES_ECB_ISO9797_M2 = 20;
    public static final byte ALG_AES_ECB_PKCS5 = 21;

    public static final byte MODE_DECRYPT = 1;
    public static final byte MODE_ENCRYPT = 2;

    /**
     * Creates a Cipher object instance of the selected algorithm.
     *
     * @param algorithm       the desired cipher algorithm
     * @param externalAccess  indicates whether the instance will be shared across contexts
     * @return the Cipher object instance of the requested algorithm
     * @throws CryptoException if the requested algorithm is not supported
     */
    public static Cipher getInstance(byte algorithm, boolean externalAccess) throws CryptoException {
        return null;
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
    public abstract short doFinal(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset) throws CryptoException;

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
    public abstract short update(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset) throws CryptoException;
}
