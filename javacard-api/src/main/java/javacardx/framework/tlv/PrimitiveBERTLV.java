package javacardx.framework.tlv;

/**
 * A BER TLV whose value is plain data (primitive tag).
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class PrimitiveBERTLV extends BERTLV {

    /**
     * Creates an empty TLV with room for a value of the given size.
     *
     * @param numValueBytes the value capacity in bytes
     * @throws TLVException with ILLEGAL_SIZE if the capacity is not valid
     */
    public PrimitiveBERTLV(short numValueBytes) {
        throw new RuntimeException("stub");
    }

    /**
     * Initializes this TLV from an encoding.
     *
     * @param bArray array holding the encoded TLV
     * @param bOff   offset of the encoding
     * @param bLen   length of the encoding
     * @return the size of the resulting TLV in bytes
     * @throws TLVException with MALFORMED_TLV if the encoding is not a valid primitive TLV, or
     *                      INSUFFICIENT_STORAGE if it does not fit this object
     */
    @Override
    public short init(byte[] bArray, short bOff, short bLen) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Initializes this TLV from a tag and a value.
     *
     * @param tag    the primitive tag
     * @param vArray array holding the value
     * @param vOff   offset of the value
     * @param vLen   length of the value
     * @return the size of the resulting TLV in bytes
     * @throws TLVException with EMPTY_TAG if the tag is not initialized, or INSUFFICIENT_STORAGE if the value
     *                      does not fit this object
     */
    public short init(PrimitiveBERTag tag, byte[] vArray, short vOff, short vLen) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Appends data to the value of this TLV.
     *
     * @param vArray array holding the data
     * @param vOff   offset of the data
     * @param vLen   length of the data
     * @return the new size of this TLV in bytes
     * @throws TLVException with EMPTY_TLV if the TLV is not initialized, or INSUFFICIENT_STORAGE if the value
     *                      would not fit
     */
    public short appendValue(byte[] vArray, short vOff, short vLen) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Replaces the value of this TLV.
     *
     * @param vArray array holding the new value
     * @param vOff   offset of the new value
     * @param vLen   length of the new value
     * @return the new size of this TLV in bytes
     * @throws TLVException with EMPTY_TLV if the TLV is not initialized, or INSUFFICIENT_STORAGE if the value
     *                      does not fit
     */
    public short replaceValue(byte[] vArray, short vOff, short vLen) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Copies the value of this TLV into a buffer.
     *
     * @param tlvValue destination array
     * @param tOff     offset in {@code tlvValue}
     * @return the number of bytes written
     * @throws TLVException with EMPTY_TLV if the TLV is not initialized
     */
    public short getValue(byte[] tlvValue, short tOff) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the offset of the value within a primitive TLV encoding.
     *
     * @param berTlvArray array holding the encoded TLV
     * @param bTlvOff     offset of the encoding
     * @return the offset of the first value byte in {@code berTlvArray}
     * @throws TLVException with MALFORMED_TLV if the encoding is not a valid primitive TLV
     */
    public static short getValueOffset(byte[] berTlvArray, short bTlvOff) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Encodes a primitive TLV from an encoded tag and a value.
     *
     * @param berTagArray array holding the encoded tag
     * @param berTagOff   offset of the tag
     * @param valueArray  array holding the value
     * @param vOff        offset of the value
     * @param vLen        length of the value
     * @param outBuf      destination array
     * @param bOff        offset in {@code outBuf}
     * @return the number of bytes written
     * @throws TLVException with MALFORMED_TAG if the tag is not a valid primitive tag
     */
    public static short toBytes(byte[] berTagArray, short berTagOff, byte[] valueArray, short vOff, short vLen,
            byte[] outBuf, short bOff) {
        throw new RuntimeException("stub");
    }

    /**
     * Appends data to the value of a primitive TLV encoding in place; the array must have room for it.
     *
     * @param berTlvArray array holding the encoded TLV
     * @param bTlvOff     offset of the encoding
     * @param vArray      array holding the data to append
     * @param vOff        offset of the data
     * @param vLen        length of the data
     * @return the new size of the encoding in bytes
     * @throws TLVException with MALFORMED_TLV if the encoding is not a valid primitive TLV
     */
    public static short appendValue(byte[] berTlvArray, short bTlvOff, byte[] vArray, short vOff, short vLen)
            throws TLVException {
        throw new RuntimeException("stub");
    }
}
