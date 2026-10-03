package javacardx.crypto;

import javacard.security.CryptoException;
import javacard.security.Key;

/**
 * Authenticated encryption with associated data (AEAD), such as AES-GCM and AES-CCM. Besides encrypting or
 * decrypting, an instance authenticates additional data supplied with {@link #updateAAD(byte[], short, short)}
 * and produces or checks an authentication tag. Instances are obtained through {@link Cipher#getInstance} with
 * one of the AEAD algorithm constants and cast to this class.
 */
public abstract class AEADCipher extends Cipher {

    /** Cipher primitive selector for AES in Galois/Counter Mode. */
    public static final byte CIPHER_AES_GCM = (byte) 0xF1;
    /** Cipher primitive selector for AES in Counter with CBC-MAC mode. */
    public static final byte CIPHER_AES_CCM = (byte) 0xF2;
    /** Algorithm constant for AES-GCM. */
    public static final byte ALG_AES_GCM = (byte) 0xF3;
    /** Algorithm constant for AES-CCM. */
    public static final byte ALG_AES_CCM = (byte) 0xF4;

    /**
     * Constructor for algorithm implementations; applets obtain instances through {@code Cipher.getInstance}.
     */
    protected AEADCipher() {
        throw new RuntimeException("stub");
    }

    /**
     * Initializes the cipher with a key and a mode, without an explicit nonce.
     *
     * @param theKey  the key
     * @param theMode {@link Cipher#MODE_ENCRYPT} or {@link Cipher#MODE_DECRYPT}
     * @throws CryptoException with ILLEGAL_VALUE if the key or mode is not suitable
     */
    @Override
    public abstract void init(Key theKey, byte theMode) throws CryptoException;

    /**
     * Initializes the cipher with a key, a mode and a nonce.
     *
     * @param theKey   the key
     * @param theMode  {@link Cipher#MODE_ENCRYPT} or {@link Cipher#MODE_DECRYPT}
     * @param nonceBuf buffer holding the nonce (initialization vector)
     * @param nonceOff offset of the nonce
     * @param nonceLen length of the nonce
     * @throws CryptoException with ILLEGAL_VALUE if a parameter is not suitable
     */
    @Override
    public abstract void init(Key theKey, byte theMode, byte[] nonceBuf, short nonceOff, short nonceLen)
            throws CryptoException;

    /**
     * Initializes the cipher with a key, a mode, a nonce and the lengths that modes such as CCM need in advance.
     *
     * @param theKey     the key
     * @param theMode    {@link Cipher#MODE_ENCRYPT} or {@link Cipher#MODE_DECRYPT}
     * @param nonceBuf   buffer holding the nonce
     * @param nonceOff   offset of the nonce
     * @param nonceLen   length of the nonce
     * @param adataLen   total length of the associated data
     * @param messageLen total length of the message
     * @param tagSize    length of the authentication tag in bytes
     * @throws CryptoException with ILLEGAL_VALUE if a parameter is not suitable
     */
    public abstract void init(Key theKey, byte theMode, byte[] nonceBuf, short nonceOff, short nonceLen,
            short adataLen, short messageLen, short tagSize) throws CryptoException;

    /**
     * Supplies associated data, which is authenticated but not encrypted. It must be supplied before any
     * message data.
     *
     * @param aadBuf buffer holding the associated data
     * @param aadOff offset of the associated data
     * @param aadLen length of the associated data
     * @throws CryptoException with ILLEGAL_USE if message data has already been processed
     */
    public abstract void updateAAD(byte[] aadBuf, short aadOff, short aadLen) throws CryptoException;

    @Override
    public abstract short update(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset)
            throws CryptoException;

    @Override
    public abstract short doFinal(byte[] inBuff, short inOffset, short inLength, byte[] outBuff, short outOffset)
            throws CryptoException;

    /**
     * After encryption, copies the authentication tag into a buffer.
     *
     * @param tagBuf buffer receiving the tag
     * @param tagOff offset in {@code tagBuf}
     * @param tagLen requested tag length
     * @return the number of tag bytes written
     * @throws CryptoException with ILLEGAL_USE if the operation is not finished or was a decryption
     */
    public abstract short retrieveTag(byte[] tagBuf, short tagOff, short tagLen) throws CryptoException;

    /**
     * After decryption, compares the computed authentication tag with a received one.
     *
     * @param receivedTagBuf buffer holding the received tag
     * @param receivedTagOff offset of the received tag
     * @param receivedTagLen length of the received tag
     * @param requiredTagLen minimum number of tag bytes that must match
     * @return {@code true} if the tags match
     * @throws CryptoException with ILLEGAL_USE if the operation is not finished or was an encryption
     */
    public abstract boolean verifyTag(byte[] receivedTagBuf, short receivedTagOff, short receivedTagLen,
            short requiredTagLen) throws CryptoException;
}
