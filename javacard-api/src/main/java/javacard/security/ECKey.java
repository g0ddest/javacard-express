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
}
