package javacard.security;

/**
 * The DSAPrivateKey interface provides methods to set and get the DSA private key x value.
 */
public interface DSAPrivateKey extends PrivateKey, DSAKey {

    /**
     * Sets the private key value x.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the value
     */
    void setX(byte[] buffer, short offset, short length);

    /**
     * Returns the private key value x.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the value
     */
    short getX(byte[] buffer, short offset);
}
