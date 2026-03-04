package javacardx.crypto;

/**
 * KeyEncryption interface defines the methods for setting and getting
 * a Cipher object for key encryption/decryption operations.
 */
public interface KeyEncryption {

    /**
     * Sets the Cipher object to be used to decrypt the input key data
     * and key parameters.
     *
     * @param keyCipher the Cipher object to use for decrypting the key data
     */
    void setKeyCipher(Cipher keyCipher);

    /**
     * Returns the Cipher object to be used to decrypt the input key data
     * and key parameters.
     *
     * @return the Cipher object, or null if not set
     */
    Cipher getKeyCipher();
}
