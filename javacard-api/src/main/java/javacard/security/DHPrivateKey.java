package javacard.security;

/**
 * Private key of a finite-field Diffie-Hellman key pair: the secret exponent x together with the domain
 * parameters of {@link DHKey}.
 */
public interface DHPrivateKey extends PrivateKey, DHKey {

    /**
     * Sets the secret exponent x.
     *
     * @param buffer array holding the value
     * @param offset offset of the value in {@code buffer}
     * @param length length of the value in bytes
     * @throws CryptoException with ILLEGAL_VALUE if the length does not fit the key size
     */
    void setX(byte[] buffer, short offset, short length) throws CryptoException;

    /**
     * Copies the secret exponent x into a buffer.
     *
     * @param buffer destination array
     * @param offset offset in {@code buffer}
     * @return the number of bytes written
     */
    short getX(byte[] buffer, short offset);
}
