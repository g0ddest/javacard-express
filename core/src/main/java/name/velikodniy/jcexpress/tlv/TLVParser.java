package name.velikodniy.jcexpress.tlv;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stateless BER-TLV parser (ISO/IEC 7816-4:2005 5.2.2).
 *
 * <p>Parses BER-TLV encoded data from byte arrays, hex strings, or APDU responses:</p>
 * <ul>
 *   <li>tag fields of one, two or three bytes (5.2.2.1); longer tag fields, which ISO/IEC 7816 reserves, and
 *       a first subsequent tag byte without tag number bits ('80', or '00' in a two-byte tag) are rejected.
 *       Two-byte tags with tag numbers below 31, as EMV uses them (e.g. '9F02'), are accepted;</li>
 *   <li>length fields of one to five bytes ('00'-'7F', '81'-'84', Table 8); '80' (indefinite) and
 *       '85'-'FF' are rejected;</li>
 *   <li>'00' and 'FF' bytes before, between and after data objects are padding (5.2.2.1, default coding);</li>
 *   <li>constructed data objects are parsed eagerly, up to {@value #MAX_DEPTH} levels of nesting.</li>
 * </ul>
 * <p>Malformed data is reported with {@link TLVException}.</p>
 */
public final class TLVParser {

    /** Maximum nesting depth of constructed data objects (deeper data is rejected, not parsed recursively). */
    public static final int MAX_DEPTH = 64;

    private TLVParser() {
    }

    /** A decoded length field: the length and the offset of the first value byte. */
    private record Length(long value, int end) {
    }

    /**
     * Parses TLV data from a byte array.
     *
     * @param data raw BER-TLV encoded bytes
     * @return parsed TLV list
     * @throws TLVException if the data is malformed
     */
    public static TLVList parse(byte[] data) {
        return parseInternal(data, 0);
    }

    /**
     * Parses TLV data from a hex string (spaces allowed).
     *
     * @param hex BER-TLV encoded data as hex
     * @return parsed TLV list
     * @throws TLVException if the data is malformed
     */
    public static TLVList parse(String hex) {
        return parse(Hex.decode(hex));
    }

    /**
     * Parses TLV data from an APDU response's data field.
     *
     * @param response the APDU response
     * @return parsed TLV list
     * @throws TLVException if the data is malformed
     */
    public static TLVList parse(APDUResponse response) {
        return parse(response.data());
    }

    /**
     * Parses the data objects of one level; also used by {@link TLV} for the value of a constructed data
     * object, one level deeper.
     */
    static TLVList parseInternal(byte[] data, int depth) {
        if (depth > MAX_DEPTH) {
            throw new TLVException("Constructed data objects nested deeper than " + MAX_DEPTH + " levels");
        }
        List<TLV> elements = new ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            if (data[offset] == 0x00 || (data[offset] & 0xFF) == 0xFF) {
                offset++;
                continue;
            }
            int tagStart = offset;
            int tag = readTag(data, offset);
            offset += Tags.tagSize(tag);
            if (offset >= data.length) {
                throw new TLVException("Truncated TLV: no length after tag " + hexTag(tag) + " at offset " + tagStart);
            }
            Length length = readLength(data, offset);
            offset = length.end();
            if (length.value() > data.length - offset) {
                throw new TLVException("Truncated TLV: tag " + hexTag(tag) + " declares " + length.value()
                        + " bytes but only " + (data.length - offset) + " available at offset " + tagStart);
            }
            byte[] value = Arrays.copyOfRange(data, offset, offset + (int) length.value());
            offset += value.length;
            elements.add(new TLV(tag, value, depth + 1));
        }
        return new TLVList(elements);
    }

    /**
     * Reads a tag field of one to three bytes (ISO/IEC 7816-4:2005 5.2.2.1).
     */
    private static int readTag(byte[] data, int offset) {
        int b0 = data[offset] & 0xFF;
        if ((b0 & 0x1F) != 0x1F) {
            return b0;
        }
        int b1 = byteAt(data, offset + 1, "Truncated multi-byte tag at offset " + offset);
        if ((b1 & 0x7F) == 0) {
            throw new TLVException(String.format("Invalid tag at offset %d: bits 7 to 1 of the first subsequent"
                    + " byte ('%02X%02X') shall not be all 0 (ISO/IEC 7816-4:2005 5.2.2.1)", offset, b0, b1));
        }
        if ((b1 & 0x80) == 0) {
            return (b0 << 8) | b1;
        }
        int b2 = byteAt(data, offset + 2, "Truncated 3-byte tag at offset " + offset);
        if ((b2 & 0x80) != 0) {
            throw new TLVException(String.format("Tag field longer than three bytes at offset %d ('%02X%02X%02X..."
                    + "'): ISO/IEC 7816-4:2005 5.2.2.1 reserves longer tag fields", offset, b0, b1, b2));
        }
        return (b0 << 16) | (b1 << 8) | b2;
    }

    /**
     * Reads a length field of one to five bytes (ISO/IEC 7816-4:2005 5.2.2.2, Table 8).
     */
    private static Length readLength(byte[] data, int offset) {
        int b0 = data[offset] & 0xFF;
        if (b0 <= 0x7F) {
            return new Length(b0, offset + 1);
        }
        int numBytes = b0 & 0x7F;
        if (numBytes == 0 || numBytes > 4) {
            throw new TLVException(String.format("Invalid length field '%02X' at offset %d: '80' (indefinite length)"
                    + " and '85' to 'FF' are invalid (ISO/IEC 7816-4:2005 5.2.2.2)", b0, offset));
        }
        if (offset + 1 + numBytes > data.length) {
            throw new TLVException("Truncated length at offset " + offset);
        }
        long length = 0;
        for (int i = 0; i < numBytes; i++) {
            length = (length << 8) | (data[offset + 1 + i] & 0xFF);
        }
        return new Length(length, offset + 1 + numBytes);
    }

    private static int byteAt(byte[] data, int index, String truncatedMessage) {
        if (index >= data.length) {
            throw new TLVException(truncatedMessage);
        }
        return data[index] & 0xFF;
    }

    private static String hexTag(int tag) {
        return String.format(tag > 0xFFFF ? "%06X" : tag > 0xFF ? "%04X" : "%02X", tag);
    }
}
