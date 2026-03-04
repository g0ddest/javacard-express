package javacard.security;

/**
 * The ECPublicKey interface provides methods to set and get the EC public key point W.
 */
public interface ECPublicKey extends PublicKey, ECKey {

    /**
     * Sets the public key point W.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the point
     */
    void setW(byte[] buffer, short offset, short length);

    /**
     * Returns the public key point W.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the point
     */
    short getW(byte[] buffer, short offset);
}
