package javacardx.framework.math;

/**
 * An unsigned integer of fixed maximum size with addition, subtraction, multiplication and comparison. Operands
 * are passed as byte arrays in big-endian binary ({@link #FORMAT_HEX}) or packed BCD ({@link #FORMAT_BCD}).
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class BigNumber {

    /** Operand format: packed binary-coded decimal. */
    public static final byte FORMAT_BCD = 1;
    /** Operand format: big-endian unsigned binary. */
    public static final byte FORMAT_HEX = 2;

    /**
     * Creates a number, initially zero, that can hold up to the given number of bytes.
     *
     * @param maxBytes the maximum size in bytes
     * @throws ArithmeticException if {@code maxBytes} exceeds {@link #getMaxBytesSupported()}
     */
    public BigNumber(short maxBytes) {
        throw new RuntimeException("stub");
    }

    /**
     * Sets the value above which arithmetic results are treated as an overflow.
     *
     * @param maxValue    array holding the maximum value
     * @param bOff        offset of the value
     * @param bLen        length of the value in bytes
     * @param arrayFormat {@link #FORMAT_BCD} or {@link #FORMAT_HEX}
     */
    public void setMaximum(byte[] maxValue, short bOff, short bLen, byte arrayFormat) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the largest size in bytes that a number can have on this platform.
     *
     * @return the maximum supported size in bytes
     */
    public static short getMaxBytesSupported() {
        throw new RuntimeException("stub");
    }

    /**
     * Sets the value of this number.
     *
     * @param bArray      array holding the value
     * @param bOff        offset of the value
     * @param bLen        length of the value in bytes
     * @param arrayFormat {@link #FORMAT_BCD} or {@link #FORMAT_HEX}
     * @throws NullPointerException           if {@code bArray} is {@code null}
     * @throws ArrayIndexOutOfBoundsException if the value lies outside {@code bArray}
     * @throws ArithmeticException            if the value is too large or not in the given format
     */
    public void init(byte[] bArray, short bOff, short bLen, byte arrayFormat)
            throws NullPointerException, ArrayIndexOutOfBoundsException, ArithmeticException {
        throw new RuntimeException("stub");
    }

    /**
     * Adds an operand to this number.
     *
     * @param bArray      array holding the operand
     * @param bOff        offset of the operand
     * @param bLen        length of the operand in bytes
     * @param arrayFormat {@link #FORMAT_BCD} or {@link #FORMAT_HEX}
     * @throws NullPointerException           if {@code bArray} is {@code null}
     * @throws ArrayIndexOutOfBoundsException if the operand lies outside {@code bArray}
     * @throws ArithmeticException            if the result overflows
     */
    public void add(byte[] bArray, short bOff, short bLen, byte arrayFormat)
            throws NullPointerException, ArrayIndexOutOfBoundsException, ArithmeticException {
        throw new RuntimeException("stub");
    }

    /**
     * Subtracts an operand from this number.
     *
     * @param bArray      array holding the operand
     * @param bOff        offset of the operand
     * @param bLen        length of the operand in bytes
     * @param arrayFormat {@link #FORMAT_BCD} or {@link #FORMAT_HEX}
     * @throws ArithmeticException if the result would be negative
     */
    public void subtract(byte[] bArray, short bOff, short bLen, byte arrayFormat) throws ArithmeticException {
        throw new RuntimeException("stub");
    }

    /**
     * Multiplies this number by an operand.
     *
     * @param bArray      array holding the operand
     * @param bOff        offset of the operand
     * @param bLen        length of the operand in bytes
     * @param arrayFormat {@link #FORMAT_BCD} or {@link #FORMAT_HEX}
     * @throws ArithmeticException if the result overflows
     */
    public void multiply(byte[] bArray, short bOff, short bLen, byte arrayFormat) throws ArithmeticException {
        throw new RuntimeException("stub");
    }

    /**
     * Compares this number with another one.
     *
     * @param operand the number to compare with
     * @return -1, 0 or 1 if this number is less than, equal to or greater than {@code operand}
     */
    public byte compareTo(BigNumber operand) {
        throw new RuntimeException("stub");
    }

    /**
     * Compares this number with an operand in a byte array.
     *
     * @param bArray      array holding the operand
     * @param bOff        offset of the operand
     * @param bLen        length of the operand in bytes
     * @param arrayFormat {@link #FORMAT_BCD} or {@link #FORMAT_HEX}
     * @return -1, 0 or 1 if this number is less than, equal to or greater than the operand
     */
    public byte compareTo(byte[] bArray, short bOff, short bLen, byte arrayFormat) {
        throw new RuntimeException("stub");
    }

    /**
     * Writes the value of this number into a byte array.
     *
     * @param outBuf      destination array
     * @param bOff        offset in {@code outBuf}
     * @param numBytes    number of bytes to write (leading zero padding as needed)
     * @param arrayFormat {@link #FORMAT_BCD} or {@link #FORMAT_HEX}
     * @throws ArrayIndexOutOfBoundsException if the output does not fit {@code outBuf}
     * @throws NullPointerException           if {@code outBuf} is {@code null}
     */
    public void toBytes(byte[] outBuf, short bOff, short numBytes, byte arrayFormat)
            throws ArrayIndexOutOfBoundsException, NullPointerException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the number of bytes needed to represent the current value.
     *
     * @param arrayFormat {@link #FORMAT_BCD} or {@link #FORMAT_HEX}
     * @return the length in bytes
     */
    public short getByteLength(byte arrayFormat) {
        throw new RuntimeException("stub");
    }

    /**
     * Sets this number to zero.
     */
    public void reset() {
        throw new RuntimeException("stub");
    }
}
