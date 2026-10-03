package javacard.framework;

/**
 * Owner PIN whose try counter must be decremented explicitly before each verification. This lets an applet
 * commit the decrement before it compares the PIN, as a countermeasure against tearing attacks.
 */
public interface OwnerPINxWithPredecrement extends OwnerPINx {

    /**
     * Decrements the try counter ahead of a following {@link #check(byte[], short, byte)}.
     *
     * @return the number of tries remaining after the decrement
     */
    byte decrementTriesRemaining();

    /**
     * Compares a candidate with the PIN value. The try counter must have been decremented beforehand with
     * {@link #decrementTriesRemaining()}; on a match it is reset to the try limit.
     *
     * @param pin    array holding the candidate value
     * @param offset offset of the candidate in {@code pin}
     * @param length length of the candidate
     * @return {@code true} if the candidate matches
     * @throws PINException                   if the try counter was not decremented before the call
     * @throws ArrayIndexOutOfBoundsException if the candidate lies outside {@code pin}
     * @throws NullPointerException           if {@code pin} is {@code null}
     */
    @Override
    boolean check(byte[] pin, short offset, byte length)
            throws PINException, ArrayIndexOutOfBoundsException, NullPointerException;
}
