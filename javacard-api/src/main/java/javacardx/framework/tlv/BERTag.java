package javacardx.framework.tlv;

/**
 * A BER tag (ISO/IEC 8825-1): tag class, primitive or constructed form and tag number. Instances hold an
 * encoded tag; the static methods work directly on encodings in byte arrays.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class BERTag {

    /** Tag class: universal. */
    public static final byte BER_TAG_CLASS_MASK_UNIVERSAL = 0;
    /** Tag class: application. */
    public static final byte BER_TAG_CLASS_MASK_APPLICATION = 1;
    /** Tag class: context-specific. */
    public static final byte BER_TAG_CLASS_MASK_CONTEXT_SPECIFIC = 2;
    /** Tag class: private. */
    public static final byte BER_TAG_CLASS_MASK_PRIVATE = 3;
    /** Encoding form flag: constructed. */
    public static final boolean BER_TAG_TYPE_CONSTRUCTED = true;
    /** Encoding form flag: primitive. */
    public static final boolean BER_TAG_TYPE_PRIMITIVE = false;

    /**
     * Constructor for the concrete tag classes.
     */
    protected BERTag() {
        throw new RuntimeException("stub");
    }

    /**
     * Initializes this tag from an encoding.
     *
     * @param bArray array holding the encoded tag
     * @param bOff   offset of the encoding
     * @throws TLVException with MALFORMED_TAG if the encoding is not valid for this kind of tag
     */
    public abstract void init(byte[] bArray, short bOff) throws TLVException;

    /**
     * Creates a primitive or constructed tag object from an encoding.
     *
     * @param bArray array holding the encoded tag
     * @param bOff   offset of the encoding
     * @return the new tag object
     * @throws TLVException with MALFORMED_TAG if the encoding is not valid
     */
    public static BERTag getInstance(byte[] bArray, short bOff) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the length of the encoded tag.
     *
     * @return the length in bytes
     * @throws TLVException with EMPTY_TAG if the tag is not initialized
     */
    public byte size() throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Writes the encoded tag into a buffer.
     *
     * @param outBuf  destination array
     * @param bOffset offset in {@code outBuf}
     * @return the number of bytes written
     * @throws TLVException with EMPTY_TAG if the tag is not initialized
     */
    public short toBytes(byte[] outBuf, short bOffset) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the tag number.
     *
     * @return the tag number
     * @throws TLVException with EMPTY_TAG if the tag is not initialized
     */
    public short tagNumber() throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether the tag uses the constructed form.
     *
     * @return {@code true} for a constructed tag
     */
    public boolean isConstructed() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the tag class.
     *
     * @return one of the {@code BER_TAG_CLASS_MASK_*} constants
     */
    public byte tagClass() {
        throw new RuntimeException("stub");
    }

    /**
     * Compares the encodings of two tags.
     *
     * @param otherTag the tag to compare with
     * @return {@code true} if both tags have the same encoding
     */
    public boolean equals(BERTag otherTag) {
        throw new RuntimeException("stub");
    }

    /**
     * Compares this tag with an object.
     *
     * @param obj the object to compare with
     * @return {@code true} if {@code obj} is a tag with the same encoding
     */
    @Override
    @SuppressWarnings("java:S1206") // API stub: the published API declares no hashCode override
    public boolean equals(Object obj) {
        throw new RuntimeException("stub");
    }

    /**
     * Encodes a tag from its components.
     *
     * @param tagClass      one of the {@code BER_TAG_CLASS_MASK_*} constants
     * @param isConstructed {@code true} for the constructed form
     * @param tagNumber     the tag number
     * @param outArray      destination array
     * @param bOff          offset in {@code outArray}
     * @return the number of bytes written
     * @throws TLVException with INVALID_PARAM if a component is out of range
     */
    public static short toBytes(short tagClass, boolean isConstructed, short tagNumber, byte[] outArray,
            short bOff) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the length of a tag encoding.
     *
     * @param berTagArray array holding the encoded tag
     * @param bOff        offset of the encoding
     * @return the length in bytes
     * @throws TLVException with MALFORMED_TAG if the encoding is not valid
     */
    public static byte size(byte[] berTagArray, short bOff) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the tag number of a tag encoding.
     *
     * @param berTagArray array holding the encoded tag
     * @param bOff        offset of the encoding
     * @return the tag number
     * @throws TLVException with MALFORMED_TAG if the encoding is not valid
     */
    public static short tagNumber(byte[] berTagArray, short bOff) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether a tag encoding uses the constructed form.
     *
     * @param berTagArray array holding the encoded tag
     * @param bOff        offset of the encoding
     * @return {@code true} for a constructed tag
     */
    public static boolean isConstructed(byte[] berTagArray, short bOff) {
        throw new RuntimeException("stub");
    }

    /**
     * Returns the tag class of a tag encoding.
     *
     * @param berTagArray array holding the encoded tag
     * @param bOff        offset of the encoding
     * @return one of the {@code BER_TAG_CLASS_MASK_*} constants
     */
    public static byte tagClass(byte[] berTagArray, short bOff) {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether a tag encoding is well-formed.
     *
     * @param berTagArray array holding the encoded tag
     * @param bOff        offset of the encoding
     * @return {@code true} if the encoding is valid
     */
    public static boolean verifyFormat(byte[] berTagArray, short bOff) {
        throw new RuntimeException("stub");
    }
}
