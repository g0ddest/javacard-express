package javacardx.framework.tlv;

/**
 * A BER TLV (ISO/IEC 8825-1): a tag, a length and a value. Instances hold an encoded TLV; the static methods work
 * directly on encodings in byte arrays.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class BERTLV {

    /**
     * Constructor for the concrete TLV classes.
     */
    protected BERTLV() {
        throw new RuntimeException("stub");
    }

    /**
     * Initializes this TLV from an encoding.
     *
     * @param bArray array holding the encoded TLV
     * @param bOff   offset of the encoding
     * @param bLen   length of the encoding
     * @return the size of the resulting TLV in bytes
     * @throws TLVException with MALFORMED_TLV if the encoding is not valid, or INSUFFICIENT_STORAGE if it does
     *                      not fit this object
     */
    public abstract short init(byte[] bArray, short bOff, short bLen) throws TLVException;

    /**
     * Creates a primitive or constructed TLV object from an encoding.
     *
     * @param bArray array holding the encoded TLV
     * @param bOff   offset of the encoding
     * @param bLen   length of the encoding
     * @return the new TLV object
     * @throws TLVException with MALFORMED_TLV if the encoding is not valid
     */
    public static BERTLV getInstance(byte[] bArray, short bOff, short bLen) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Writes the encoded TLV into a buffer.
     *
     * @param outBuf destination array
     * @param bOff   offset in {@code outBuf}
     * @return the number of bytes written
     * @throws TLVException with EMPTY_TLV if the TLV is not initialized
     */
    public short toBytes(byte[] outBuf, short bOff) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the tag of this TLV.
     *
     * @return the tag object
     * @throws TLVException with EMPTY_TLV if the TLV is not initialized
     */
    public BERTag getTag() throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the length of the value of this TLV.
     *
     * @return the value length in bytes
     * @throws TLVException with EMPTY_TLV if the TLV is not initialized
     */
    public short getLength() throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the length of the complete encoding of this TLV.
     *
     * @return the size in bytes
     */
    public short size() {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether a TLV encoding is well-formed.
     *
     * @param berTlvArray array holding the encoded TLV
     * @param bOff        offset of the encoding
     * @param bLen        length of the encoding
     * @return {@code true} if the encoding is valid
     */
    public static boolean verifyFormat(byte[] berTlvArray, short bOff, short bLen) {
        throw new RuntimeException("stub");
    }

    /**
     * Copies the tag of a TLV encoding.
     *
     * @param berTlvArray array holding the encoded TLV
     * @param bTlvOff     offset of the encoding
     * @param berTagArray destination array for the tag
     * @param bTagOff     offset in {@code berTagArray}
     * @return the number of bytes written
     * @throws TLVException with MALFORMED_TLV if the encoding is not valid
     */
    public static short getTag(byte[] berTlvArray, short bTlvOff, byte[] berTagArray, short bTagOff)
            throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the value length of a TLV encoding.
     *
     * @param berTlvArray array holding the encoded TLV
     * @param bOff        offset of the encoding
     * @return the value length in bytes
     * @throws TLVException with MALFORMED_TLV if the encoding is not valid
     */
    public static short getLength(byte[] berTlvArray, short bOff) throws TLVException {
        throw new RuntimeException("stub");
    }
}
