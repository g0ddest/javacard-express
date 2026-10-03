package javacardx.framework.tlv;

/**
 * A BER tag in the constructed form.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class ConstructedBERTag extends BERTag {

    /**
     * Creates an uninitialized tag.
     */
    public ConstructedBERTag() {
        throw new RuntimeException("stub");
    }

    /**
     * Initializes this tag from its class and number.
     *
     * @param tagClass  one of the {@code BER_TAG_CLASS_MASK_*} constants
     * @param tagNumber the tag number
     * @throws TLVException with INVALID_PARAM if a component is out of range
     */
    public void init(byte tagClass, short tagNumber) throws TLVException {
        throw new RuntimeException("stub");
    }

    /**
     * Initializes this tag from an encoding, which must use the constructed form.
     *
     * @param bArray array holding the encoded tag
     * @param bOff   offset of the encoding
     * @throws TLVException with MALFORMED_TAG if the encoding is not a valid constructed tag
     */
    @Override
    public void init(byte[] bArray, short bOff) throws TLVException {
        throw new RuntimeException("stub");
    }
}
