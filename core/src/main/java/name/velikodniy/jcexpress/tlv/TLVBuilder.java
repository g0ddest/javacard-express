package name.velikodniy.jcexpress.tlv;

import name.velikodniy.jcexpress.Hex;

import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

/**
 * Fluent builder for constructing BER-TLV encoded data (ISO/IEC 7816-4:2005 5.2.2).
 *
 * <p>Tags are checked against ISO/IEC 7816-4:2005 5.2.2.1 so that the output can be parsed back by
 * {@link TLVParser}: one to three bytes, not '00', a first byte with b5-b1 = 11111 exactly for two- and
 * three-byte tags, no first byte 'FF' (padding by default), a first subsequent byte with tag number bits,
 * b8 = 1 only on the second byte of a three-byte tag. Lengths use the shortest form of Table 8.</p>
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
     * @throws IllegalArgumentException if the tag is not a valid BER-TLV tag field of one to three bytes
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
     * @throws IllegalArgumentException if the tag is not a valid BER-TLV tag field of one to three bytes
     */
    public TLVBuilder addConstructed(int tag, Consumer<TLVBuilder> children) {
        checkTag(tag);
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

    /** ISO/IEC 7816-4:2005 5.2.2.1 and Table 7. */
    private static void checkTag(int tag) {
        if (tag <= 0 || tag > 0xFFFFFF) {
            throw new IllegalArgumentException(String.format("Tag %X is not a tag field of one to three bytes"
                    + " ('00' is invalid, ISO/IEC 7816-4:2005 5.2.2.1)", tag));
        }
        int size = Tags.tagSize(tag);
        int first = Tags.firstByte(tag);
        boolean longForm = (first & 0x1F) == 0x1F;
        if (size == 1 ? longForm : !longForm) {
            throw invalidTag(tag, size == 1 ? "b5-b1 = 11111 announce subsequent tag bytes"
                    : "the first byte is a complete one-byte tag");
        }
        if (size > 1) {
            checkSubsequentBytes(tag, size, first);
        }
    }

    private static void checkSubsequentBytes(int tag, int size, int first) {
        int second = (size == 2 ? tag : tag >> 8) & 0xFF;
        if (first == 0xFF) {
            throw invalidTag(tag, "the first byte 'FF' is padding unless the data coding byte allows it");
        }
        if ((second & 0x7F) == 0) {
            throw invalidTag(tag, "bits 7 to 1 of the first subsequent byte shall not be all 0");
        }
        if ((second & 0x80) != 0 && size == 2) {
            throw invalidTag(tag, "b8 = 1 in the second byte announces a third byte");
        }
        if (size == 3 && ((second & 0x80) == 0 || (tag & 0x80) != 0)) {
            throw invalidTag(tag, "a three-byte tag has b8 = 1 in the second and b8 = 0 in the third byte");
        }
    }

    private static IllegalArgumentException invalidTag(int tag, String reason) {
        return new IllegalArgumentException(String.format("Invalid BER-TLV tag %X: %s (ISO/IEC 7816-4:2005"
                + " 5.2.2.1)", tag, reason));
    }

    private void writeTag(int tag) {
        checkTag(tag);
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
        } else if (length <= 0xFFFFFF) {
            out.write(0x83);
            out.write((length >> 16) & 0xFF);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        } else {
            out.write(0x84);
            out.write((length >> 24) & 0xFF);
            out.write((length >> 16) & 0xFF);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        }
    }
}
