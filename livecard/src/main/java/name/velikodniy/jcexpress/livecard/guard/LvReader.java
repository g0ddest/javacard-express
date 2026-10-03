package name.velikodniy.jcexpress.livecard.guard;

import java.util.Arrays;

/**
 * Reads the length-value fields of GlobalPlatform command data: one-byte lengths for AIDs, privileges and
 * hashes, ASN.1 BER lengths ('00'-'7F', '81 xx', '82 xx xx') for parameter and token fields (GlobalPlatform
 * Card Specification v2.3.1 11.1.5, Tables 11-42 to 11-44).
 */
final class LvReader {

    private final byte[] data;
    private int offset;

    LvReader(byte[] data) {
        this.data = data.clone();
    }

    /**
     * Reads a field with a one-byte length.
     *
     * @return the value
     * @throws IllegalArgumentException if the field runs past the end of the data
     */
    byte[] shortField() {
        int length = next();
        return value(length);
    }

    /**
     * Reads a field with a BER length.
     *
     * @return the value
     * @throws IllegalArgumentException if the length coding is invalid or the field runs past the end
     */
    byte[] berField() {
        int first = next();
        int length;
        if (first <= 0x7F) {
            length = first;
        } else if (first == 0x81) {
            length = next();
        } else if (first == 0x82) {
            length = (next() << 8) | next();
        } else {
            throw new IllegalArgumentException(String.format("invalid BER length byte %02X", first));
        }
        return value(length);
    }

    /**
     * Requires that all data has been read.
     *
     * @throws IllegalArgumentException if bytes are left
     */
    void requireEnd() {
        if (offset != data.length) {
            throw new IllegalArgumentException((data.length - offset) + " unexpected trailing byte(s)");
        }
    }

    boolean atEnd() {
        return offset == data.length;
    }

    private int next() {
        if (offset >= data.length) {
            throw new IllegalArgumentException("data ends inside a length field");
        }
        return data[offset++] & 0xFF;
    }

    private byte[] value(int length) {
        if (offset + length > data.length) {
            throw new IllegalArgumentException("field of " + length + " bytes runs past the end of the data");
        }
        byte[] value = Arrays.copyOfRange(data, offset, offset + length);
        offset += length;
        return value;
    }
}
