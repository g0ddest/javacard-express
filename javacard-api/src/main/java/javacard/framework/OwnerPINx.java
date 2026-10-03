package javacard.framework;

/**
 * Owner PIN whose value, try limit and remaining tries the owning applet can manage. Instances are created with
 * {@link OwnerPINBuilder#buildOwnerPIN(byte, byte, byte)}.
 */
public interface OwnerPINx extends PIN {

    /**
     * Replaces the PIN value and resets the try counter.
     *
     * @param pin    array holding the new PIN value
     * @param offset offset of the new value in {@code pin}
     * @param length length of the new value
     * @throws PINException if the length exceeds the maximum PIN size
     */
    void update(byte[] pin, short offset, byte length) throws PINException;

    /**
     * Returns the number of consecutive wrong presentations allowed before the PIN is blocked.
     *
     * @return the try limit
     */
    byte getTryLimit();

    /**
     * Changes the try limit; the remaining tries are set to the new limit.
     *
     * @param tryLimit the new try limit, greater than zero
     */
    void setTryLimit(byte tryLimit);

    /**
     * Sets the number of remaining tries.
     *
     * @param remaining the new number of remaining tries, at most the try limit
     */
    void setTriesRemaining(byte remaining);
}
