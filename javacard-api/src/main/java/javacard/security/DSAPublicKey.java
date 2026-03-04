package javacard.security;

/**
 * The DSAPublicKey interface provides methods to set and get the DSA public key y value.
 */
public interface DSAPublicKey extends PublicKey, DSAKey {

    /**
     * Sets the public key value y.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the value
     */
    void setY(byte[] buffer, short offset, short length);

    /**
     * Returns the public key value y.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the value
     */
    short getY(byte[] buffer, short offset);
}
