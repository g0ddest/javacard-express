package javacard.security;

/**
 * The DSAKey interface provides methods to set and get DSA key domain parameters.
 */
public interface DSAKey {

    /**
     * Sets the prime parameter p.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setP(byte[] buffer, short offset, short length);

    /**
     * Sets the subprime parameter q.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setQ(byte[] buffer, short offset, short length);

    /**
     * Sets the base parameter g.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the parameter
     */
    void setG(byte[] buffer, short offset, short length);

    /**
     * Returns the prime parameter p.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getP(byte[] buffer, short offset);

    /**
     * Returns the subprime parameter q.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getQ(byte[] buffer, short offset);

    /**
     * Returns the base parameter g.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the parameter
     */
    short getG(byte[] buffer, short offset);
}
