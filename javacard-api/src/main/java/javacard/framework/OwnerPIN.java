package javacard.framework;

/**
 * Implements a PIN with an owner-managed try counter.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class OwnerPIN implements PIN {

    /**
     * Constructs an OwnerPIN with the given try limit and maximum PIN size.
     *
     * @param tryLimit   the maximum number of incorrect tries
     * @param maxPINSize the maximum PIN length in bytes
     */
    public OwnerPIN(byte tryLimit, byte maxPINSize) {
        throw new RuntimeException("stub");
    }

    @Override
    public boolean check(byte[] pin, short offset, byte length) throws PINException {
        throw new RuntimeException("stub");
    }

    @Override
    public boolean isValidated() {
        throw new RuntimeException("stub");
    }

    @Override
    public byte getTriesRemaining() {
        throw new RuntimeException("stub");
    }

    @Override
    public void reset() {
        throw new RuntimeException("stub");
    }

    /**
     * Updates the PIN value and resets the try counter.
     *
     * @param pin    byte array containing the new PIN
     * @param offset starting offset
     * @param length length of the new PIN
     * @throws PINException on error
     */
    public void update(byte[] pin, short offset, byte length) throws PINException {
        throw new RuntimeException("stub");
    }

    /**
     * Resets the try counter and unblocks the PIN.
     */
    public void resetAndUnblock() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the value of the "validated" flag. Subclasses use this hook to keep the flag in storage of their
     * choice (for example a transient array).
     *
     * @return the current value of the validated flag
     */
    protected boolean getValidatedFlag() {
        throw new RuntimeException("stub");
    }

    /**
     * Sets the value of the "validated" flag. Subclasses use this hook to keep the flag in storage of their
     * choice (for example a transient array).
     *
     * @param value the new value of the validated flag
     */
    protected void setValidatedFlag(boolean value) {
        throw new RuntimeException("stub");
    }
}
