package javacard.security;

/**
 * HMACKey contains an HMAC key for use with HMAC algorithms.
 */
public interface HMACKey extends SecretKey {

    /**
     * Sets the key data.
     *
     * @param keyData byte array containing key data
     * @param kOff offset into keyData
     * @param kLen length of the key data
     * @throws CryptoException with reason ILLEGAL_VALUE if input data is invalid
     */
    void setKey(byte[] keyData, short kOff, short kLen) throws CryptoException;

    /**
     * Returns the key data.
     *
     * @param keyData byte array to receive the key data
     * @param kOff offset into keyData
     * @return the length of the key data
     */
    byte getKey(byte[] keyData, short kOff);
}
