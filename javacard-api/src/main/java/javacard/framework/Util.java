package javacard.framework;

/**
 * Provides utility methods for array manipulation and short conversions.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class Util {

    private Util() {
    }

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
    public static final short arrayCopy(byte[] src, short srcOff, byte[] dest, short destOff, short length) {
        throw new RuntimeException("stub");
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
    public static final short arrayCopyNonAtomic(byte[] src, short srcOff, byte[] dest, short destOff, short length) {
        throw new RuntimeException("stub");
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
    public static final short arrayFill(byte[] bArray, short bOff, short bLen, byte bValue) {
        throw new RuntimeException("stub");
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
    public static final short arrayFillNonAtomic(byte[] bArray, short bOff, short bLen, byte bValue) {
        throw new RuntimeException("stub");
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
    public static final byte arrayCompare(byte[] src, short srcOff, byte[] dest, short destOff, short length) {
        throw new RuntimeException("stub");
    }

    /**
     * Constructs a short from two bytes.
     *
     * @param b1 the high byte
     * @param b2 the low byte
     * @return the short value
     */
    public static final short makeShort(byte b1, byte b2) {
        throw new RuntimeException("stub");
    }

    /**
     * Reads a short value from a byte array.
     *
     * @param bArray the byte array
     * @param bOff   the offset
     * @return the short value
     */
    public static final short getShort(byte[] bArray, short bOff) {
        throw new RuntimeException("stub");
    }

    /**
     * Writes a short value into a byte array.
     *
     * @param bArray the byte array
     * @param bOff   the offset
     * @param sValue the short value
     * @return bOff + 2
     */
    public static final short setShort(byte[] bArray, short bOff, short sValue) {
        throw new RuntimeException("stub");
    }
}
