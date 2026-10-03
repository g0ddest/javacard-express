package javacardx.framework.util.intx;

import javacard.framework.SystemException;
import javacard.framework.TransactionException;

/**
 * Helpers for the optional 32-bit {@code int} type: building values, reading and writing them big-endian in
 * byte arrays, and creating transient {@code int} arrays. Only platforms that support {@code int} provide this
 * class.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class JCint {

    private JCint() {
    }

    /**
     * Combines four bytes into an int, most significant first.
     *
     * @param b1 the most significant byte
     * @param b2 the second byte
     * @param b3 the third byte
     * @param b4 the least significant byte
     * @return the int value
     */
    public static final int makeInt(byte b1, byte b2, byte b3, byte b4) {
        throw new RuntimeException("stub");
    }

    /**
     * Combines two shorts into an int, high word first.
     *
     * @param s1 the most significant short
     * @param s2 the least significant short
     * @return the int value
     */
    public static final int makeInt(short s1, short s2) {
        throw new RuntimeException("stub");
    }

    /**
     * Reads a big-endian int from a byte array.
     *
     * @param bArray the byte array
     * @param bOff   offset of the first byte
     * @return the int value
     * @throws NullPointerException           if {@code bArray} is {@code null}
     * @throws ArrayIndexOutOfBoundsException if fewer than four bytes are available at {@code bOff}
     */
    public static final int getInt(byte[] bArray, short bOff)
            throws NullPointerException, ArrayIndexOutOfBoundsException {
        throw new RuntimeException("stub");
    }

    /**
     * Writes an int big-endian into a byte array.
     *
     * @param bArray the byte array
     * @param bOff   offset of the first byte
     * @param iValue the value
     * @return {@code bOff + 4}
     * @throws TransactionException           if the write overflows the commit buffer
     * @throws NullPointerException           if {@code bArray} is {@code null}
     * @throws ArrayIndexOutOfBoundsException if fewer than four bytes are available at {@code bOff}
     */
    public static final short setInt(byte[] bArray, short bOff, int iValue)
            throws TransactionException, NullPointerException, ArrayIndexOutOfBoundsException {
        throw new RuntimeException("stub");
    }

    /**
     * Creates a transient int array.
     *
     * @param length number of elements
     * @param event  {@code JCSystem.CLEAR_ON_RESET} or {@code JCSystem.CLEAR_ON_DESELECT}
     * @return the new array
     * @throws NegativeArraySizeException if {@code length} is negative
     * @throws SystemException            if the event is not valid or there is not enough transient memory
     */
    public static int[] makeTransientIntArray(short length, byte event)
            throws NegativeArraySizeException, SystemException {
        throw new RuntimeException("stub");
    }
}
