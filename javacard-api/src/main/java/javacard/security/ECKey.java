package javacard.security;

/**
 * The ECKey interface provides methods to set and get elliptic curve key domain parameters.
 */
public interface ECKey {

    /**
     * Sets the field element for the prime field Fp.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setFieldFP(byte[] buffer, short offset, short length);

    /**
     * Sets the field of a characteristic-2 curve defined by a trinomial basis x^m + x^e + 1. The exponent m is
     * the key size.
     *
     * @param e the middle exponent of the reduction polynomial
     * @throws CryptoException with ILLEGAL_VALUE if the value is out of range for the key size
     */
    void setFieldF2M(short e) throws CryptoException;

    /**
     * Sets the field of a characteristic-2 curve defined by a pentanomial basis x^m + x^e1 + x^e2 + x^e3 + 1.
     * The exponent m is the key size.
     *
     * @param e1 the highest middle exponent
     * @param e2 the second middle exponent
     * @param e3 the lowest middle exponent
     * @throws CryptoException with ILLEGAL_VALUE if the exponents are not in strictly decreasing order or are out
     *                         of range for the key size
     */
    void setFieldF2M(short e1, short e2, short e3) throws CryptoException;

    /**
     * Sets the first coefficient a of the curve.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setA(byte[] buffer, short offset, short length);

    /**
     * Sets the second coefficient b of the curve.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setB(byte[] buffer, short offset, short length);

    /**
     * Sets the fixed point G of the curve.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setG(byte[] buffer, short offset, short length);

    /**
     * Sets the order of the fixed point G of the curve.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setR(byte[] buffer, short offset, short length);

    /**
     * Sets the cofactor K.
     *
     * @param K the cofactor
     */
    void setK(short K);

    /**
     * Returns the field element value for the prime field Fp.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getField(byte[] buffer, short offset);

    /**
     * Returns the first coefficient a of the curve.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getA(byte[] buffer, short offset);

    /**
     * Returns the second coefficient b of the curve.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getB(byte[] buffer, short offset);

    /**
     * Returns the fixed point G of the curve.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getG(byte[] buffer, short offset);

    /**
     * Returns the order of the fixed point G of the curve.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getR(byte[] buffer, short offset);

    /**
     * Returns the cofactor K.
     *
     * @return the cofactor
     */
    short getK();

    /**
     * Copies all domain parameters (field, a, b, G, r and K) from another EC key of the same type and size.
     *
     * @param eckey the key to copy the domain parameters from
     * @throws CryptoException with ILLEGAL_VALUE if the keys differ in type or size, or UNINITIALIZED_KEY if
     *                         the source key's domain parameters are not set
     */
    void copyDomainParametersFrom(ECKey eckey) throws CryptoException;
}
