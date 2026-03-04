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
    }

    @Override
    public boolean check(byte[] pin, short offset, byte length) throws PINException {
        return false;
    }

    @Override
    public boolean isValidated() {
        return false;
    }

    @Override
    public byte getTriesRemaining() {
        return 0;
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
}
