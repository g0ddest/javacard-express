package name.velikodniy.jcexpress.tlv;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless BER-TLV parser.
 *
 * <p>Parses BER-TLV encoded data from byte arrays, hex strings, or APDU responses.
 * Supports multi-byte tags (up to 3 bytes) and multi-byte lengths (up to 3 bytes).</p>
 */
public final class TLVParser {

    private TLVParser() {
    }

    /**
     * Parses TLV data from a byte array.
     *
     * @param data raw BER-TLV encoded bytes
     * @return parsed TLV list
     * @throws TLVException if the data is malformed
     */
    public static TLVList parse(byte[] data) {
        return parseInternal(data);
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
     * Internal parse method, also used by TLV for parsing constructed values.
     */
    static TLVList parseInternal(byte[] data) {
        List<TLV> elements = new ArrayList<>();
        int offset = 0;

        while (offset < data.length) {
            // Skip padding bytes (0x00 and 0xFF)
            if (data[offset] == 0x00 || (data[offset] & 0xFF) == 0xFF) {
                offset++;
                continue;
            }

            // Read tag
            int tagStart = offset;
            int tag = readTag(data, offset);
            offset += Tags.tagSize(tag);

            if (offset >= data.length) {
                throw new TLVException("Truncated TLV: no length after tag "
                        + String.format(tag > 0xFF ? "%04X" : "%02X", tag)
                        + " at offset " + tagStart);
            }

            // Read length
            int[] lengthResult = readLength(data, offset);
            int length = lengthResult[0];
            offset = lengthResult[1];

            if (offset + length > data.length) {
                throw new TLVException("Truncated TLV: tag "
                        + String.format(tag > 0xFF ? "%04X" : "%02X", tag)
                        + " declares " + length + " bytes but only "
                        + (data.length - offset) + " available at offset " + tagStart);
            }

            // Read value
            byte[] value = new byte[length];
            System.arraycopy(data, offset, value, 0, length);
            offset += length;

            elements.add(new TLV(tag, value));
        }

        return new TLVList(elements);
    }

    /**
     * Reads a BER tag from data at the given offset.
     * Supports 1, 2, or 3-byte tags.
     */
    private static int readTag(byte[] data, int offset) {
        if (offset >= data.length) {
            throw new TLVException("Truncated tag at offset " + offset);
        }
        int b0 = data[offset] & 0xFF;

        // If lower 5 bits are all 1s, it's a multi-byte tag
        if ((b0 & 0x1F) != 0x1F) {
            return b0;
        }

        // Multi-byte tag
        if (offset + 1 >= data.length) {
            throw new TLVException("Truncated multi-byte tag at offset " + offset);
        }
        int b1 = data[offset + 1] & 0xFF;

        // If bit 8 is set, there's a third byte
        if ((b1 & 0x80) != 0) {
            if (offset + 2 >= data.length) {
                throw new TLVException("Truncated 3-byte tag at offset " + offset);
            }
            int b2 = data[offset + 2] & 0xFF;
            return (b0 << 16) | (b1 << 8) | b2;
        }

        return (b0 << 8) | b1;
    }

    /**
     * Reads a BER length from data at the given offset.
     * Returns [length, newOffset].
     */
    private static int[] readLength(byte[] data, int offset) {
        if (offset >= data.length) {
            throw new TLVException("Truncated length at offset " + offset);
        }
        int b0 = data[offset] & 0xFF;

        if (b0 <= 0x7F) {
            // Short form
            return new int[]{b0, offset + 1};
        }

        int numBytes = b0 & 0x7F;
        if (numBytes == 0) {
            throw new TLVException("Indefinite length not supported at offset " + offset);
        }
        if (numBytes > 3) {
            throw new TLVException("Length too large (" + numBytes + " bytes) at offset " + offset);
        }
        if (offset + 1 + numBytes > data.length) {
            throw new TLVException("Truncated length at offset " + offset);
        }

        int length = 0;
        for (int i = 0; i < numBytes; i++) {
            length = (length << 8) | (data[offset + 1 + i] & 0xFF);
        }

        return new int[]{length, offset + 1 + numBytes};
    }
}
