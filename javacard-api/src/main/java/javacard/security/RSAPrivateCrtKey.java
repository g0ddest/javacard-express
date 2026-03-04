package javacard.security;

/**
 * The RSAPrivateCrtKey interface provides methods to set and get the RSA private key
 * components in Chinese Remainder Theorem (CRT) form.
 */
public interface RSAPrivateCrtKey extends PrivateKey {

    /**
     * Sets the prime factor p.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setP(byte[] buffer, short offset, short length);

    /**
     * Sets the prime factor q.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setQ(byte[] buffer, short offset, short length);

    /**
     * Sets the value dp1 (d mod (p-1)).
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setDP1(byte[] buffer, short offset, short length);

    /**
     * Sets the value dq1 (d mod (q-1)).
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setDQ1(byte[] buffer, short offset, short length);

    /**
     * Sets the value pq (q^-1 mod p).
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setPQ(byte[] buffer, short offset, short length);

    /**
     * Returns the prime factor p.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getP(byte[] buffer, short offset);

    /**
     * Returns the prime factor q.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getQ(byte[] buffer, short offset);

    /**
     * Returns the value dp1 (d mod (p-1)).
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getDP1(byte[] buffer, short offset);

    /**
     * Returns the value dq1 (d mod (q-1)).
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getDQ1(byte[] buffer, short offset);

    /**
     * Returns the value pq (q^-1 mod p).
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getPQ(byte[] buffer, short offset);
}
