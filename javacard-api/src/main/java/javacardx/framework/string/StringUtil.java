package javacardx.framework.string;

/**
 * Text operations on UTF-8 strings stored in byte arrays: code point navigation, comparison, search, case
 * conversion, number formatting and parsing, and conversion to and from other encodings. Every string is passed
 * as array, offset and length in bytes; indices and counts named "code point" refer to characters.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class StringUtil {

    /** Encoding selector: UTF-8. */
    public static final byte UTF_8 = 1;
    /** Encoding selector: UTF-16 with a byte order mark. */
    public static final byte UTF_16 = 2;
    /** Encoding selector: UTF-16, little-endian. */
    public static final byte UTF_16_LE = 3;
    /** Encoding selector: UTF-16, big-endian. */
    public static final byte UTF_16_BE = 4;
    /** Encoding selector: UCS-2. */
    public static final byte UCS_2 = 5;
    /** Encoding selector: GSM 7-bit default alphabet. */
    public static final byte GSM_7 = 6;
    /** Encoding selector: ISO/IEC 8859-1. */
    public static final byte ISO_8859_1 = 7;
    /** Lowest encoding selector reserved for proprietary encodings. */
    public static final byte PROP_ENCODING_EXT = 64;

    private StringUtil() {
    }

    /**
     * Counts the code points of a string.
     *
     * @param aString array holding the string
     * @param offset  offset of the string
     * @param length  length of the string in bytes
     * @return the number of code points
     */
    public static short codePointCount(byte[] aString, short offset, short length) {
        throw new RuntimeException("stub");
    }

    /**
     * Copies the encoding of the code point at a code point index.
     *
     * @param aString   array holding the string
     * @param offset    offset of the string
     * @param length    length of the string in bytes
     * @param index     code point index
     * @param dstBuffer destination array
     * @param dstOffset offset in {@code dstBuffer}
     * @return the number of bytes written
     */
    public static short codePointAt(byte[] aString, short offset, short length, short index, byte[] dstBuffer,
            short dstOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Copies the encoding of the code point just before a code point index.
     *
     * @param aString   array holding the string
     * @param offset    offset of the string
     * @param length    length of the string in bytes
     * @param index     code point index
     * @param dstBuffer destination array
     * @param dstOffset offset in {@code dstBuffer}
     * @return the number of bytes written
     */
    public static short codePointBefore(byte[] aString, short offset, short length, short index,
            byte[] dstBuffer, short dstOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Converts a code point index plus a code point displacement into a byte offset.
     *
     * @param aString         array holding the string
     * @param offset          offset of the string
     * @param length          length of the string in bytes
     * @param index           starting code point index
     * @param codePointOffset number of code points to move, possibly negative
     * @return the byte offset, relative to {@code offset}, of the resulting position
     */
    public static short offsetByCodePoints(byte[] aString, short offset, short length, short index,
            short codePointOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Compares two strings lexicographically by code point.
     *
     * @param ignoreCase    {@code true} to ignore case differences
     * @param aString       array holding the first string
     * @param offset        offset of the first string
     * @param length        length of the first string in bytes
     * @param anotherString array holding the second string
     * @param oOffset       offset of the second string
     * @param oLength       length of the second string in bytes
     * @return a negative value, zero or a positive value if the first string is less than, equal to or greater
     *         than the second
     */
    public static short compare(boolean ignoreCase, byte[] aString, short offset, short length,
            byte[] anotherString, short oOffset, short oLength) {
        throw new RuntimeException("stub");
    }

    /**
     * Finds the first occurrence of a substring.
     *
     * @param aString   array holding the string to search
     * @param offset    offset of the string
     * @param length    length of the string in bytes
     * @param subString array holding the substring
     * @param sOffset   offset of the substring
     * @param sLength   length of the substring in bytes
     * @return the code point index of the first occurrence, or -1 if there is none
     */
    public static short indexOf(byte[] aString, short offset, short length, byte[] subString, short sOffset,
            short sLength) {
        throw new RuntimeException("stub");
    }

    /**
     * Copies a string, replacing every occurrence of one substring by another.
     *
     * @param srcString    array holding the source string
     * @param srcOffset    offset of the source string
     * @param srcLength    length of the source string in bytes
     * @param oldSubstring array holding the substring to replace
     * @param oldOffset    offset of that substring
     * @param oldLength    length of that substring in bytes
     * @param newSubstring array holding the replacement
     * @param newOffset    offset of the replacement
     * @param newLength    length of the replacement in bytes
     * @param dstString    destination array
     * @param dstOffset    offset in {@code dstString}
     * @return the number of bytes written
     */
    public static short replace(byte[] srcString, short srcOffset, short srcLength, byte[] oldSubstring,
            short oldOffset, short oldLength, byte[] newSubstring, short newOffset, short newLength,
            byte[] dstString, short dstOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Copies a string converted to lower case.
     *
     * @param srcString array holding the source string
     * @param srcOffset offset of the source string
     * @param srcLength length of the source string in bytes
     * @param dstString destination array
     * @param dstOffset offset in {@code dstString}
     * @return the number of bytes written
     */
    public static short toLowerCase(byte[] srcString, short srcOffset, short srcLength, byte[] dstString,
            short dstOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Copies a string converted to upper case.
     *
     * @param srcString array holding the source string
     * @param srcOffset offset of the source string
     * @param srcLength length of the source string in bytes
     * @param dstString destination array
     * @param dstOffset offset in {@code dstString}
     * @return the number of bytes written
     */
    public static short toUpperCase(byte[] srcString, short srcOffset, short srcLength, byte[] dstString,
            short dstOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Copies a string without its leading and trailing white space.
     *
     * @param srcString array holding the source string
     * @param srcOffset offset of the source string
     * @param srcLength length of the source string in bytes
     * @param dstString destination array
     * @param dstOffset offset in {@code dstString}
     * @return the number of bytes written
     */
    public static short trim(byte[] srcString, short srcOffset, short srcLength, byte[] dstString,
            short dstOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Writes the text "true" or "false".
     *
     * @param b         the value
     * @param dstString destination array
     * @param dstOffset offset in {@code dstString}
     * @return the number of bytes written
     */
    public static short valueOf(boolean b, byte[] dstString, short dstOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Parses the text "true" (ignoring case) as {@code true}; anything else is {@code false}.
     *
     * @param aString array holding the text
     * @param offset  offset of the text
     * @param length  length of the text in bytes
     * @return the parsed value
     */
    public static boolean parseBoolean(byte[] aString, short offset, short length) {
        throw new RuntimeException("stub");
    }

    /**
     * Writes the decimal text of a short value.
     *
     * @param s         the value
     * @param dstString destination array
     * @param dstOffset offset in {@code dstString}
     * @return the number of bytes written
     */
    public static short valueOf(short s, byte[] dstString, short dstOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Parses decimal text as a short value.
     *
     * @param aString array holding the text
     * @param offset  offset of the text
     * @param length  length of the text in bytes
     * @return the parsed value
     * @throws StringException with ILLEGAL_NUMBER_FORMAT if the text is not a valid short value
     */
    public static short parseShortInteger(byte[] aString, short offset, short length) {
        throw new RuntimeException("stub");
    }

    /**
     * Writes the decimal text of a long integer value held in an array of shorts, most significant first.
     *
     * @param l         array of shorts holding the value
     * @param dstString destination array
     * @param dstOffset offset in {@code dstString}
     * @return the number of bytes written
     */
    public static short valueOf(short[] l, byte[] dstString, short dstOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Parses decimal text as a long integer value and stores it as consecutive shorts, most significant first.
     *
     * @param aString   array holding the text
     * @param offset    offset of the text
     * @param length    length of the text in bytes
     * @param dst       array receiving the value
     * @param dstOffset index of the first short in {@code dst}
     * @return the number of shorts written
     * @throws StringException with ILLEGAL_NUMBER_FORMAT if the text is not a valid value
     */
    public static short parseLongInteger(byte[] aString, short offset, short length, short[] dst,
            short dstOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Converts a UTF-8 string into another encoding.
     *
     * @param srcString array holding the UTF-8 string
     * @param srcOffset offset of the string
     * @param srcLength length of the string in bytes
     * @param dstString destination array
     * @param dstOffset offset in {@code dstString}
     * @param encoding  one of the encoding selectors of this class
     * @return the number of bytes written
     * @throws StringException with UNSUPPORTED_ENCODING if the encoding is not supported
     */
    public static short convertTo(byte[] srcString, short srcOffset, short srcLength, byte[] dstString,
            short dstOffset, byte encoding) {
        throw new RuntimeException("stub");
    }

    /**
     * Converts a string in another encoding into UTF-8.
     *
     * @param srcString array holding the encoded string
     * @param srcOffset offset of the string
     * @param srcLength length of the string in bytes
     * @param dstString destination array
     * @param dstOffset offset in {@code dstString}
     * @param encoding  one of the encoding selectors of this class
     * @return the number of bytes written
     * @throws StringException with UNSUPPORTED_ENCODING if the encoding is not supported, or
     *                         INVALID_BYTE_SEQUENCE if the source is malformed
     */
    public static short convertFrom(byte[] srcString, short srcOffset, short srcLength, byte[] dstString,
            short dstOffset, byte encoding) {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether a byte sequence is well-formed UTF-8.
     *
     * @param aString array holding the bytes
     * @param offset  offset of the bytes
     * @param length  number of bytes
     * @return {@code true} if the bytes are valid UTF-8
     */
    public static boolean check(byte[] aString, short offset, short length) {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether a string, starting at a code point offset, begins with a prefix.
     *
     * @param aString         array holding the string
     * @param offset          offset of the string
     * @param length          length of the string in bytes
     * @param prefix          array holding the prefix
     * @param pOffset         offset of the prefix
     * @param pLength         length of the prefix in bytes
     * @param codePointOffset code point index in the string where the comparison starts
     * @return {@code true} if the prefix matches
     */
    public static boolean startsWith(byte[] aString, short offset, short length, byte[] prefix, short pOffset,
            short pLength, short codePointOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether a string, ignoring a number of trailing code points, ends with a suffix.
     *
     * @param aString         array holding the string
     * @param offset          offset of the string
     * @param length          length of the string in bytes
     * @param suffix          array holding the suffix
     * @param sOffset         offset of the suffix
     * @param sLength         length of the suffix in bytes
     * @param codePointOffset number of code points at the end of the string to ignore
     * @return {@code true} if the suffix matches
     */
    public static boolean endsWith(byte[] aString, short offset, short length, byte[] suffix, short sOffset,
            short sLength, short codePointOffset) {
        throw new RuntimeException("stub");
    }

    /**
     * Copies the code points between two code point indices.
     *
     * @param aString    array holding the string
     * @param offset     offset of the string
     * @param length     length of the string in bytes
     * @param beginIndex code point index of the first code point to copy
     * @param endIndex   code point index just after the last code point to copy
     * @param dstString  destination array
     * @param dstOffset  offset in {@code dstString}
     * @return the number of bytes written
     */
    public static short substring(byte[] aString, short offset, short length, short beginIndex, short endIndex,
            byte[] dstString, short dstOffset) {
        throw new RuntimeException("stub");
    }
}
