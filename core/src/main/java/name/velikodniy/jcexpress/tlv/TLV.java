package name.velikodniy.jcexpress.tlv;

import name.velikodniy.jcexpress.Hex;

import java.util.Optional;

/**
 * A single BER-TLV element with a tag, length, and value.
 *
 * <p>Constructed TLVs contain nested TLV elements in their value,
 * accessible via {@link #children()}, {@link #find(int)}, and {@link #findRecursive(int)}.</p>
 */
public final class TLV {

    private final int tag;
    private final byte[] value;
    private final TLVList children;

    TLV(int tag, byte[] value) {
        this.tag = tag;
        this.value = value.clone();
        this.children = Tags.isConstructed(tag) ? TLVParser.parseInternal(value) : TLVList.empty();
    }

    /**
     * Returns the tag number.
     *
     * @return the tag (e.g., 0x6F, 0x9F38)
     */
    public int tag() {
        return tag;
    }

    /**
     * Returns the raw value bytes.
     *
     * @return a copy of the value
     */
    public byte[] value() {
        return value.clone();
    }

    /**
     * Returns the value as an uppercase hex string.
     *
     * @return hex-encoded value
     */
    public String valueHex() {
        return Hex.encode(value);
    }

    /**
     * Returns the value length in bytes.
     *
     * @return value length
     */
    public int length() {
        return value.length;
    }

    /**
     * Returns true if this is a constructed TLV (contains nested TLVs).
     *
     * @return true if constructed
     */
    public boolean isConstructed() {
        return Tags.isConstructed(tag);
    }

    /**
     * Returns the children of this constructed TLV.
     * Returns an empty list for primitive TLVs.
     *
     * @return child TLV elements
     */
    public TLVList children() {
        return children;
    }

    /**
     * Finds a direct child with the given tag.
     *
     * @param childTag the tag to search for
     * @return the first matching child, or empty
     */
    public Optional<TLV> find(int childTag) {
        return children.find(childTag);
    }

    /**
     * Recursively finds a descendant with the given tag.
     *
     * @param descendantTag the tag to search for
     * @return the first matching descendant, or empty
     */
    public Optional<TLV> findRecursive(int descendantTag) {
        return children.findRecursive(descendantTag);
    }

    @Override
    public String toString() {
        return String.format("TLV[%s, %d bytes%s]",
                String.format(tag > 0xFF ? "%04X" : "%02X", tag),
                value.length,
                isConstructed() ? ", " + children.size() + " children" : "");
    }
}
