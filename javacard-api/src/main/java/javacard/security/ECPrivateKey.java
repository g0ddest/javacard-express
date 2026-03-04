package javacard.security;

/**
 * The ECPrivateKey interface provides methods to set and get the EC private key secret value S.
 */
public interface ECPrivateKey extends PrivateKey, ECKey {

    /**
     * Sets the private key secret value S.
     *
     * @param buffer input buffer
     * @param offset offset into the buffer
     * @param length length of the value
     */
    void setS(byte[] buffer, short offset, short length);

    /**
     * Returns the private key secret value S.
     *
     * @param buffer output buffer
     * @param offset offset into the buffer
     * @return the byte length of the value
     */
    short getS(byte[] buffer, short offset);
}
