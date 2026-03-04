package javacard.security;

/**
 * AESKey contains an AES key for use with the AES algorithm.
 */
public interface AESKey extends SecretKey {

    /**
     * Sets the key data.
     *
     * @param keyData byte array containing key data
     * @param kOff offset into keyData
     * @throws CryptoException with reason ILLEGAL_VALUE if input data is invalid
     */
    void setKey(byte[] keyData, short kOff) throws CryptoException;

    /**
     * Returns the key data.
     *
     * @param keyData byte array to receive the key data
     * @param kOff offset into keyData
     * @return the length of the key data
     */
    byte getKey(byte[] keyData, short kOff);
}
