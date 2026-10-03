package javacard.security;

/**
 * Domain parameters of a finite-field Diffie-Hellman key: the prime p, the subprime q and the generator g.
 * Values are unsigned big-endian integers.
 */
public interface DHKey {

    /**
     * Sets the prime p.
     *
     * @param buffer array holding the value
     * @param offset offset of the value in {@code buffer}
     * @param length length of the value in bytes
     * @throws CryptoException with ILLEGAL_VALUE if the length does not fit the key size
     */
    void setP(byte[] buffer, short offset, short length) throws CryptoException;

    /**
     * Sets the subprime q.
     *
     * @param buffer array holding the value
     * @param offset offset of the value in {@code buffer}
     * @param length length of the value in bytes
     * @throws CryptoException with ILLEGAL_VALUE if the length does not fit the key size
     */
    void setQ(byte[] buffer, short offset, short length) throws CryptoException;

    /**
     * Sets the generator g.
     *
     * @param buffer array holding the value
     * @param offset offset of the value in {@code buffer}
     * @param length length of the value in bytes
     * @throws CryptoException with ILLEGAL_VALUE if the length does not fit the key size
     */
    void setG(byte[] buffer, short offset, short length) throws CryptoException;

    /**
     * Copies the prime p into a buffer.
     *
     * @param buffer destination array
     * @param offset offset in {@code buffer}
     * @return the number of bytes written
     */
    short getP(byte[] buffer, short offset);

    /**
     * Copies the subprime q into a buffer.
     *
     * @param buffer destination array
     * @param offset offset in {@code buffer}
     * @return the number of bytes written
     */
    short getQ(byte[] buffer, short offset);

    /**
     * Copies the generator g into a buffer.
     *
     * @param buffer destination array
     * @param offset offset in {@code buffer}
     * @return the number of bytes written
     */
    short getG(byte[] buffer, short offset);
}
