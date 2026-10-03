package javacard.security;

/**
 * Public key of a finite-field Diffie-Hellman key pair: the public value y together with the domain parameters
 * of {@link DHKey}.
 */
public interface DHPublicKey extends PublicKey, DHKey {

    /**
     * Sets the public value y.
     *
     * @param buffer array holding the value
     * @param offset offset of the value in {@code buffer}
     * @param length length of the value in bytes
     * @throws CryptoException with ILLEGAL_VALUE if the length does not fit the key size
     */
    void setY(byte[] buffer, short offset, short length) throws CryptoException;

    /**
     * Copies the public value y into a buffer.
     *
     * @param buffer destination array
     * @param offset offset in {@code buffer}
     * @return the number of bytes written
     */
    short getY(byte[] buffer, short offset);
}
