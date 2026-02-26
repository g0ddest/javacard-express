package name.velikodniy.jcexpress.tlv;

import name.velikodniy.jcexpress.Hex;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

/**
 * Fluent builder for constructing BER-TLV encoded data.
 *
 * <p>Example:</p>
 * <pre>
 * byte[] data = TLVBuilder.create()
 *     .add(Tags.DF_NAME, "A0000000031010")
 *     .addConstructed(Tags.FCI_PROPRIETARY, b -&gt; b
 *         .add(Tags.SFI, new byte[]{0x01})
 *     )
 *     .build();
 * </pre>
 */
public final class TLVBuilder {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    private TLVBuilder() {
    }

    /**
     * Creates a new TLV builder.
     *
     * @return a new builder
     */
    public static TLVBuilder create() {
        return new TLVBuilder();
    }

    /**
     * Adds a primitive TLV with a raw byte value.
     *
     * @param tag   the tag
     * @param value the value bytes
     * @return this builder
     */
    public TLVBuilder add(int tag, byte[] value) {
        writeTag(tag);
        writeLength(value.length);
        out.writeBytes(value);
        return this;
    }

    /**
     * Adds a primitive TLV with a hex-encoded value.
     *
     * @param tag      the tag
     * @param hexValue the value as a hex string
     * @return this builder
     */
    public TLVBuilder add(int tag, String hexValue) {
        return add(tag, Hex.decode(hexValue));
    }

    /**
     * Adds a constructed TLV, building children via the consumer.
     *
     * @param tag      the tag (should have bit 6 set for constructed)
     * @param children consumer that builds child TLVs
     * @return this builder
     */
    public TLVBuilder addConstructed(int tag, Consumer<TLVBuilder> children) {
        TLVBuilder inner = new TLVBuilder();
        children.accept(inner);
        byte[] childBytes = inner.build();
        writeTag(tag);
        writeLength(childBytes.length);
        out.writeBytes(childBytes);
        return this;
    }

    /**
     * Builds the TLV encoded byte array.
     *
     * @return the encoded bytes
     */
    public byte[] build() {
        return out.toByteArray();
    }

    private void writeTag(int tag) {
        int size = Tags.tagSize(tag);
        if (size == 3) {
            out.write((tag >> 16) & 0xFF);
            out.write((tag >> 8) & 0xFF);
            out.write(tag & 0xFF);
        } else if (size == 2) {
            out.write((tag >> 8) & 0xFF);
            out.write(tag & 0xFF);
        } else {
            out.write(tag & 0xFF);
        }
    }

    private void writeLength(int length) {
        if (length <= 0x7F) {
            out.write(length);
        } else if (length <= 0xFF) {
            out.write(0x81);
            out.write(length);
        } else if (length <= 0xFFFF) {
            out.write(0x82);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        } else {
            out.write(0x83);
            out.write((length >> 16) & 0xFF);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        }
    }
}
