package javacardx.framework.math;

/**
 * Converts between packed binary-coded decimal (BCD, two decimal digits per byte) and unsigned binary numbers
 * stored big-endian in byte arrays.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class BCDUtil {

    /**
     * Creates an instance; all methods are static.
     */
    public BCDUtil() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the largest number of bytes the conversion methods accept.
     *
     * @return the maximum supported length in bytes
     */
    public static short getMaxBytesSupported() {
        throw new RuntimeException("stub");
    }

    /**
     * Converts a packed BCD number into an unsigned binary number.
     *
     * @param bcdArray array holding the BCD number
     * @param bOff     offset of the BCD number
     * @param bLen     length of the BCD number in bytes
     * @param hexArray destination array
     * @param outOff   offset in {@code hexArray}
     * @return the number of bytes written
     * @throws javacard.framework.SystemException if a length exceeds {@link #getMaxBytesSupported()}
     */
    public static short convertToHex(byte[] bcdArray, short bOff, short bLen, byte[] hexArray, short outOff) {
        throw new RuntimeException("stub");
    }

    /**
     * Converts an unsigned binary number into a packed BCD number.
     *
     * @param hexArray array holding the binary number
     * @param bOff     offset of the binary number
     * @param bLen     length of the binary number in bytes
     * @param bcdArray destination array
     * @param outOff   offset in {@code bcdArray}
     * @return the number of bytes written
     * @throws javacard.framework.SystemException if a length exceeds {@link #getMaxBytesSupported()}
     */
    public static short convertToBCD(byte[] hexArray, short bOff, short bLen, byte[] bcdArray, short outOff) {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether every nibble of the data is a decimal digit (0 to 9).
     *
     * @param bcdArray array holding the data
     * @param bOff     offset of the data
     * @param bLen     length of the data in bytes
     * @return {@code true} if the data is valid packed BCD
     */
    public static boolean isBCDFormat(byte[] bcdArray, short bOff, short bLen) {
        throw new RuntimeException("stub");
    }
}
