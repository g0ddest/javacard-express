package javacardx.framework.tlv;

/**
 * A BER TLV whose value is a sequence of nested TLVs (constructed tag).
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class ConstructedBERTLV extends BERTLV {

    /**
     * Creates an empty TLV with room for a value of the given size.
     *
     * @param numValueBytes the value capacity in bytes
     * @throws TLVException with ILLEGAL_SIZE if the capacity is not valid
     */
    public ConstructedBERTLV(short numValueBytes) {
        throw new RuntimeException("stub");
    }

    /**
     * Initializes this TLV from an encoding.
     *
     * @param bArray array holding the encoded TLV
     * @param bOff   offset of the encoding
     * @param bLen   length of the encoding
     * @return the size of the resulting TLV in bytes
     * @throws TLVException with MALFORMED_TLV if the encoding is not a valid constructed TLV, or
     *                      INSUFFICIENT_STORAGE if it does not fit this object
     */
    @Override
    public short init(byte[] bArray, short bOff, short bLen) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Initializes this TLV with a tag and one nested TLV.
     *
     * @param tag  the constructed tag
     * @param aTLV the nested TLV
     * @return the size of the resulting TLV in bytes
     * @throws TLVException with EMPTY_TAG or EMPTY_TLV if an argument is not initialized, or
     *                      INSUFFICIENT_STORAGE if it does not fit this object
     */
    public short init(ConstructedBERTag tag, BERTLV aTLV) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Initializes this TLV with a tag and a value made of encoded TLVs.
     *
     * @param tag    the constructed tag
     * @param vArray array holding the value
     * @param vOff   offset of the value
     * @param vLen   length of the value
     * @return the size of the resulting TLV in bytes
     * @throws TLVException with MALFORMED_TLV if the value is not a sequence of TLVs, or INSUFFICIENT_STORAGE if
     *                      it does not fit this object
     */
    public short init(ConstructedBERTag tag, byte[] vArray, short vOff, short vLen) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Appends a nested TLV at the end of the value.
     *
     * @param aTLV the TLV to append
     * @return the new size of this TLV in bytes
     * @throws TLVException with EMPTY_TLV if an object is not initialized, or INSUFFICIENT_STORAGE if the result
     *                      does not fit
     */
    public short append(BERTLV aTLV) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Removes an occurrence of a nested TLV.
     *
     * @param aTLV          the TLV to remove
     * @param occurrenceNum which occurrence to remove, starting at 1
     * @return the new size of this TLV in bytes
     * @throws TLVException with INVALID_PARAM if there is no such occurrence
     */
    public short delete(BERTLV aTLV, short occurrenceNum) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Finds the first nested TLV with the given tag.
     *
     * @param tag the tag to look for, or {@code null} for the first nested TLV
     * @return the nested TLV, or {@code null} if none is found
     */
    public BERTLV find(BERTag tag) {
        throw new RuntimeException("stub");
    }

    /**
     * Finds a further occurrence of a nested TLV with the given tag, after a given nested TLV.
     *
     * @param tag           the tag to look for
     * @param aTLV          the nested TLV after which the search starts
     * @param occurrenceNum which further occurrence to return, starting at 1
     * @return the nested TLV, or {@code null} if none is found
     */
    public BERTLV findNext(BERTag tag, BERTLV aTLV, short occurrenceNum) {
        throw new RuntimeException("stub");
    }

    /**
     * Appends an encoded TLV to the value of a constructed TLV encoding in place; the array must have room.
     *
     * @param berTlvInArray  array holding the TLV to append
     * @param bTlvInOff      offset of the TLV to append
     * @param berTlvOutArray array holding the constructed TLV
     * @param bTlvOutOff     offset of the constructed TLV
     * @return the new size of the constructed TLV encoding in bytes
     * @throws TLVException with MALFORMED_TLV if an encoding is not valid
     */
    public static short append(byte[] berTlvInArray, short bTlvInOff, byte[] berTlvOutArray, short bTlvOutOff)
            throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Finds the first nested TLV with a given tag in a constructed TLV encoding.
     *
     * @param berTlvArray array holding the constructed TLV
     * @param bTlvOff     offset of the constructed TLV
     * @param berTagArray array holding the encoded tag to look for
     * @param bTagOff     offset of the tag
     * @return the offset of the nested TLV in {@code berTlvArray}, or -1 if none is found
     * @throws TLVException with MALFORMED_TLV or MALFORMED_TAG if an encoding is not valid
     */
    public static short find(byte[] berTlvArray, short bTlvOff, byte[] berTagArray, short bTagOff)
            throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Finds the next nested TLV with a given tag after a given position in a constructed TLV encoding.
     *
     * @param berTlvArray array holding the constructed TLV
     * @param bTlvOff     offset of the constructed TLV
     * @param startOffset offset of the nested TLV after which the search starts
     * @param berTagArray array holding the encoded tag to look for
     * @param bTagOff     offset of the tag
     * @return the offset of the nested TLV in {@code berTlvArray}, or -1 if none is found
     * @throws TLVException with MALFORMED_TLV or MALFORMED_TAG if an encoding is not valid
     */
    public static short findNext(byte[] berTlvArray, short bTlvOff, short startOffset, byte[] berTagArray,
            short bTagOff) throws TLVException {
        throw new RuntimeException("stub");
    }
}
