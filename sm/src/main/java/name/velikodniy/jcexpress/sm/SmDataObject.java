package name.velikodniy.jcexpress.sm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One Secure Messaging data object as received in an APDU data field.
 *
 * <p>SM data objects are BER-TLV encoded with a single-byte context-specific tag (ISO/IEC 7816-4,
 * secure messaging data objects; ICAO Doc 9303-11, 9.8.4: "All SM Data Objects MUST be encoded in
 * BER TLV"). The length is in short form or in long form {@code '81' xx} / {@code '82' xx xx}.
 * The received byte range is kept so that the MAC can be verified over exactly the bytes the chip
 * sent, whatever length encoding it chose.</p>
 *
 * @param tag   the one-byte tag
 * @param value the value field
 * @param start offset of the tag byte in the parsed array
 * @param end   offset just after the value field
 */
record SmDataObject(int tag, byte[] value, int start, int end) {

    /**
     * Parses consecutive SM data objects from {@code data[0 .. limit)}.
     *
     * @param data  the bytes to parse
     * @param limit the end of the region to parse (exclusive)
     * @return the data objects in received order
     * @throws SMException if a data object is truncated or uses an unsupported encoding
     */
    static List<SmDataObject> parseAll(byte[] data, int limit) {
        List<SmDataObject> objects = new ArrayList<>();
        int offset = 0;
        while (offset < limit) {
            SmDataObject object = parseOne(data, offset, limit);
            objects.add(object);
            offset = object.end();
        }
        return objects;
    }

    private static SmDataObject parseOne(byte[] data, int start, int limit) {
        int tag = data[start] & 0xFF;
        if ((tag & 0x1F) == 0x1F) {
            throw new SMException(String.format("Multi-byte tag %02X.. is not an SM data object", tag));
        }
        int lengthOffset = start + 1;
        int length = readLength(data, lengthOffset, limit, tag);
        int valueOffset = lengthOffset + lengthFieldSize(data[lengthOffset] & 0xFF);
        if (length > limit - valueOffset) {
            throw new SMException(String.format("SM data object 0x%02X extends beyond the data field", tag));
        }
        return new SmDataObject(tag, Arrays.copyOfRange(data, valueOffset, valueOffset + length),
                start, valueOffset + length);
    }

    private static int readLength(byte[] data, int offset, int limit, int tag) {
        if (offset >= limit) {
            throw new SMException(String.format("Truncated SM data object at tag 0x%02X", tag));
        }
        int first = data[offset] & 0xFF;
        if (first <= 0x7F) {
            return first;
        }
        if (first != 0x81 && first != 0x82) {
            throw new SMException(String.format("Unsupported length encoding 0x%02X in SM data object 0x%02X",
                    first, tag));
        }
        int count = first & 0x0F;
        if (offset + count >= limit) {
            throw new SMException(String.format("Truncated length in SM data object 0x%02X", tag));
        }
        int length = 0;
        for (int i = 1; i <= count; i++) {
            length = (length << 8) | (data[offset + i] & 0xFF);
        }
        return length;
    }

    private static int lengthFieldSize(int firstLengthByte) {
        return firstLengthByte <= 0x7F ? 1 : 1 + (firstLengthByte & 0x0F);
    }
}
