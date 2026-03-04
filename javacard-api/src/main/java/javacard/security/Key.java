package javacard.security;

/**
 * The Key interface is the base interface for all keys.
 */
public interface Key {

    /**
     * Returns the key size in bits.
     *
     * @return the key size in bits
     */
    short getSize();

    /**
     * Returns the key type.
     *
     * @return the key type
     */
    byte getType();

    /**
     * Checks whether the key has been initialized.
     *
     * @return true if the key has been initialized, false otherwise
     */
    boolean isInitialized();

    /**
     * Clears the key and sets it to an uninitialized state.
     */
    void clearKey();
}
