package javacard.security;

/**
 * The RSAPublicKey interface provides methods to set and get the RSA public key components.
 */
public interface RSAPublicKey extends PublicKey {

    /**
     * Sets the modulus value of the key.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the modulus
     */
    void setModulus(byte[] buffer, short offset, short length);

    /**
     * Sets the public exponent value of the key.
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
     * Returns the public exponent value of the key.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the exponent
     */
    short getExponent(byte[] buffer, short offset);
}
