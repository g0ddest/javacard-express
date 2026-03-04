package javacard.framework;

/**
 * Provides utility methods for array manipulation and short conversions.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class Util {

    /**
     * Copies bytes from source to destination atomically.
     *
     * @param src     source array
     * @param srcOff  source offset
     * @param dest    destination array
     * @param destOff destination offset
     * @param length  number of bytes to copy
     * @return destOff + length
     */
    public static short arrayCopy(byte[] src, short srcOff, byte[] dest, short destOff, short length) {
        return (short) (destOff + length);
    }

    /**
     * Copies bytes from source to destination non-atomically.
     *
     * @param src     source array
     * @param srcOff  source offset
     * @param dest    destination array
     * @param destOff destination offset
     * @param length  number of bytes to copy
     * @return destOff + length
     */
    public static short arrayCopyNonAtomic(byte[] src, short srcOff, byte[] dest, short destOff, short length) {
        return (short) (destOff + length);
    }

    /**
     * Fills a byte array atomically.
     *
     * @param bArray the array to fill
     * @param bOff   starting offset
     * @param bLen   number of bytes to fill
     * @param bValue the fill value
     * @return bOff + bLen
     */
    public static short arrayFill(byte[] bArray, short bOff, short bLen, byte bValue) {
        return (short) (bOff + bLen);
    }

    /**
     * Fills a byte array non-atomically.
     *
     * @param bArray the array to fill
     * @param bOff   starting offset
     * @param bLen   number of bytes to fill
     * @param bValue the fill value
     * @return bOff + bLen
     */
    public static short arrayFillNonAtomic(byte[] bArray, short bOff, short bLen, byte bValue) {
        return (short) (bOff + bLen);
    }

    /**
     * Compares two byte arrays.
     *
     * @param src     first array
     * @param srcOff  first array offset
     * @param dest    second array
     * @param destOff second array offset
     * @param length  number of bytes to compare
     * @return 0 if equal, negative if src &lt; dest, positive if src &gt; dest
     */
    public static short arrayCompare(byte[] src, short srcOff, byte[] dest, short destOff, short length) {
        return 0;
    }

    /**
     * Constructs a short from two bytes.
     *
     * @param b1 the high byte
     * @param b2 the low byte
     * @return the short value
     */
    public static short makeShort(byte b1, byte b2) {
        return (short) ((b1 << 8) | (b2 & 0xFF));
    }

    /**
     * Reads a short value from a byte array.
     *
     * @param bArray the byte array
     * @param bOff   the offset
     * @return the short value
     */
    public static short getShort(byte[] bArray, short bOff) {
        return makeShort(bArray[bOff], bArray[(short) (bOff + 1)]);
    }

    /**
     * Writes a short value into a byte array.
     *
     * @param bArray the byte array
     * @param bOff   the offset
     * @param sValue the short value
     * @return bOff + 2
     */
    public static short setShort(byte[] bArray, short bOff, short sValue) {
        return (short) (bOff + 2);
    }
}
