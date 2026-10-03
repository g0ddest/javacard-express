package javacardx.framework.util;

import javacard.framework.SystemException;
import javacard.framework.TransactionException;

/**
 * Copy, fill, compare and search operations on arrays of any primitive component type. Positions and lengths
 * are counted in array elements, not bytes. When the component types of source and destination differ, the
 * data is repacked big-endian (for example two bytes per short).
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class ArrayLogic {

    private ArrayLogic() {
    }

    /**
     * Copies elements between arrays of possibly different component types, atomically.
     *
     * @param src       source array
     * @param srcOff    index of the first source element
     * @param srcLen    number of source elements
     * @param dest      destination array
     * @param destOff   index of the first destination element
     * @return {@code destOff} plus the number of destination elements written
     * @throws ArrayIndexOutOfBoundsException if the copy would access outside an array
     * @throws NullPointerException           if an array is {@code null}
     * @throws TransactionException           if the copy overflows the commit buffer
     * @throws UtilException                  with ILLEGAL_VALUE or TYPE_MISMATCHED if the arrays cannot be
     *                                        repacked into each other
     */
    public static final short arrayCopyRepack(Object src, short srcOff, short srcLen, Object dest, short destOff)
            throws ArrayIndexOutOfBoundsException, NullPointerException, TransactionException, UtilException {
        throw new RuntimeException("stub");
    }

    /**
     * Copies elements between arrays of possibly different component types, without atomicity.
     *
     * @param src       source array
     * @param srcOff    index of the first source element
     * @param srcLen    number of source elements
     * @param dest      destination array
     * @param destOff   index of the first destination element
     * @return {@code destOff} plus the number of destination elements written
     * @throws ArrayIndexOutOfBoundsException if the copy would access outside an array
     * @throws NullPointerException           if an array is {@code null}
     * @throws UtilException                  with ILLEGAL_VALUE or TYPE_MISMATCHED if the arrays cannot be
     *                                        repacked into each other
     * @throws SystemException                if a persistent array is written while a transaction is in progress
     */
    public static final short arrayCopyRepackNonAtomic(Object src, short srcOff, short srcLen, Object dest,
            short destOff)
            throws ArrayIndexOutOfBoundsException, NullPointerException, UtilException, SystemException {
        throw new RuntimeException("stub");
    }

    /**
     * Fills a range of an array with copies of one element taken from a value array, atomically.
     *
     * @param theArray  array to fill
     * @param off       index of the first element to fill
     * @param len       number of elements to fill
     * @param valArray  array holding the fill value
     * @param valOff    index of the fill value in {@code valArray}
     * @return {@code off + len}
     * @throws ArrayIndexOutOfBoundsException if the fill would access outside an array
     * @throws NullPointerException           if an array is {@code null}
     * @throws UtilException                  with TYPE_MISMATCHED if the arrays have different component types
     * @throws TransactionException           if the fill overflows the commit buffer
     */
    public static final short arrayFillGeneric(Object theArray, short off, short len, Object valArray,
            short valOff)
            throws ArrayIndexOutOfBoundsException, NullPointerException, UtilException, TransactionException {
        throw new RuntimeException("stub");
    }

    /**
     * Fills a range of an array with copies of one element taken from a value array, without atomicity.
     *
     * @param theArray  array to fill
     * @param off       index of the first element to fill
     * @param len       number of elements to fill
     * @param valArray  array holding the fill value
     * @param valOff    index of the fill value in {@code valArray}
     * @return {@code off + len}
     * @throws ArrayIndexOutOfBoundsException if the fill would access outside an array
     * @throws NullPointerException           if an array is {@code null}
     * @throws UtilException                  with TYPE_MISMATCHED if the arrays have different component types
     * @throws SystemException                if a persistent array is written while a transaction is in progress
     */
    public static final short arrayFillGenericNonAtomic(Object theArray, short off, short len, Object valArray,
            short valOff)
            throws ArrayIndexOutOfBoundsException, NullPointerException, UtilException, SystemException {
        throw new RuntimeException("stub");
    }

    /**
     * Compares ranges of two arrays with the same component type, element by element.
     *
     * @param src     first array
     * @param srcOff  index of the first element in {@code src}
     * @param dest    second array
     * @param destOff index of the first element in {@code dest}
     * @param length  number of elements to compare
     * @return -1, 0 or 1 if the first range is less than, equal to or greater than the second
     * @throws ArrayIndexOutOfBoundsException if the comparison would access outside an array
     * @throws NullPointerException           if an array is {@code null}
     * @throws UtilException                  with TYPE_MISMATCHED if the component types differ
     */
    public static final byte arrayCompareGeneric(Object src, short srcOff, Object dest, short destOff,
            short length) throws ArrayIndexOutOfBoundsException, NullPointerException, UtilException {
        throw new RuntimeException("stub");
    }

    /**
     * Finds the first element of an array equal to a value given in a byte array.
     *
     * @param theArray array to search
     * @param off      index of the first element to examine
     * @param valArray byte array holding the value, big-endian, in the size of one element
     * @param valOff   offset of the value in {@code valArray}
     * @return the index of the first matching element, or -1 if there is none
     * @throws ArrayIndexOutOfBoundsException if the search would access outside an array
     * @throws NullPointerException           if an array is {@code null}
     * @throws UtilException                  with ILLEGAL_VALUE if {@code theArray} is not an array of a
     *                                        primitive type
     */
    public static final short arrayFindGeneric(Object theArray, short off, byte[] valArray, short valOff)
            throws ArrayIndexOutOfBoundsException, NullPointerException, UtilException {
        throw new RuntimeException("stub");
    }
}
