package javacard.security;

/**
 * Secret key of the SEED block cipher (KISA, RFC 4269); the key is always 128 bits long.
 */
public interface KoreanSEEDKey extends SecretKey {

    /**
     * Sets the key value; 16 bytes are read.
     *
     * @param keyData array holding the key value
     * @param kOff    offset of the key value in {@code keyData}
     * @throws CryptoException                with ILLEGAL_VALUE if the data is not a valid key
     * @throws NullPointerException           if {@code keyData} is {@code null}
     * @throws ArrayIndexOutOfBoundsException if fewer than 16 bytes are available at {@code kOff}
     */
    void setKey(byte[] keyData, short kOff)
            throws CryptoException, NullPointerException, ArrayIndexOutOfBoundsException;

    /**
     * Copies the key value into a buffer.
     *
     * @param keyData destination array
     * @param kOff    offset in {@code keyData}
     * @return the number of bytes written (16)
     */
    byte getKey(byte[] keyData, short kOff);
}
