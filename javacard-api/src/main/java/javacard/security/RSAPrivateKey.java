package javacard.security;

/**
 * The RSAPrivateKey interface provides methods to set and get the RSA private key components.
 */
public interface RSAPrivateKey extends PrivateKey {

    /**
     * Sets the modulus value of the key.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the modulus
     */
    void setModulus(byte[] buffer, short offset, short length);

    /**
     * Sets the private exponent value of the key.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the exponent
     */
    void setExponent(byte[] buffer, short offset, short length);

    /**
     * Returns the modulus value of the key.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the modulus
     */
    short getModulus(byte[] buffer, short offset);

    /**
     * Returns the private exponent value of the key.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the exponent
     */
    short getExponent(byte[] buffer, short offset);
}
