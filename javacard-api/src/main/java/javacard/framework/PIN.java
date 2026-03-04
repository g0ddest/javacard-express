package javacard.framework;

/**
 * Represents a personal identification number used for security.
 */
public interface PIN {

    /**
     * Checks the PIN value.
     *
     * @param pin    byte array containing the PIN
     * @param offset starting offset in the pin array
     * @param length length of the PIN
     * @return true if the PIN matched
     * @throws PINException if an error occurs
     */
    boolean check(byte[] pin, short offset, byte length) throws PINException;

    /**
     * Returns whether the PIN has been successfully validated.
     *
     * @return true if validated
     */
    boolean isValidated();

    /**
     * Returns the number of remaining PIN tries.
     *
     * @return tries remaining
     */
    byte getTriesRemaining();

    /**
     * Resets the validated flag.
     */
    void reset();
}
