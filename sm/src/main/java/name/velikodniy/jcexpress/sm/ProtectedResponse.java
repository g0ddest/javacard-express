package name.velikodniy.jcexpress.sm;

import java.util.Arrays;
import java.util.List;

/**
 * The Secure Messaging data objects of a protected response APDU.
 *
 * <p>ICAO Doc 9303-11, 9.8.4 fixes the structure and order of a protected response:
 * {@code [DO'85' or DO'87'] DO'99' DO'8E'} followed by SW1-SW2 (Figure 6). DO'87' carries the
 * padding-content indicator {@code '01'} followed by the cryptogram; DO'85' (answers to an odd INS)
 * carries the cryptogram only. DO'99' holds the authenticated status word and DO'8E' the 8-byte
 * cryptographic checksum, computed over the SSC and all preceding data objects in their received
 * encoding (9.8.6.2, 9.8.7.2). ICAO 9303-11, 9.3.3 requires the sender to keep this order, so any other
 * layout, an unknown data object or a missing DO'99'/DO'8E' is rejected.</p>
 *
 * @param cryptogram the encrypted response data without the padding-content indicator, or {@code null}
 * @param statusWord the status word from DO'99'
 * @param macInput   the received bytes of all data objects before DO'8E'
 * @param mac        the value of DO'8E'
 */
record ProtectedResponse(byte[] cryptogram, int statusWord, byte[] macInput, byte[] mac) {

    private static final int TAG_CRYPTOGRAM_BER = 0x85;
    private static final int TAG_CRYPTOGRAM = 0x87;
    private static final int TAG_STATUS = 0x99;
    private static final int TAG_MAC = 0x8E;
    private static final int MAC_LENGTH = 8;

    /**
     * Parses and validates the data field of a protected response.
     *
     * @param response the complete response APDU (data field followed by SW1-SW2)
     * @return the validated structure
     * @throws SMException if the data field is not {@code [DO'85'|DO'87'] DO'99' DO'8E'}
     */
    static ProtectedResponse parse(byte[] response) {
        int dataLength = response.length - 2;
        List<SmDataObject> objects = SmDataObject.parseAll(response, dataLength);
        int index = 0;
        byte[] cryptogram = null;
        if (index < objects.size() && isCryptogram(objects.get(index).tag())) {
            cryptogram = cryptogram(objects.get(index++));
        }
        SmDataObject status = expect(objects, index++, TAG_STATUS, "DO'99' (processing status)");
        SmDataObject mac = expect(objects, index++, TAG_MAC, "DO'8E' (cryptographic checksum)");
        if (index != objects.size()) {
            throw new SMException(String.format("Unexpected SM data object 0x%02X after DO'8E'",
                    objects.get(index).tag()));
        }
        return new ProtectedResponse(cryptogram, statusWord(status), Arrays.copyOf(response, mac.start()),
                macValue(mac));
    }

    private static boolean isCryptogram(int tag) {
        return tag == TAG_CRYPTOGRAM || tag == TAG_CRYPTOGRAM_BER;
    }

    private static byte[] cryptogram(SmDataObject object) {
        byte[] value = object.value();
        if (object.tag() == TAG_CRYPTOGRAM_BER) {
            return value;
        }
        if (value.length < 1 || value[0] != 0x01) {
            throw new SMException("DO'87' must start with padding-content indicator 01"
                    + (value.length > 0 ? String.format(", got %02X", value[0] & 0xFF) : ", got empty value"));
        }
        return Arrays.copyOfRange(value, 1, value.length);
    }

    private static SmDataObject expect(List<SmDataObject> objects, int index, int tag, String name) {
        if (index >= objects.size()) {
            throw new SMException("Missing " + name + " in SM response");
        }
        SmDataObject object = objects.get(index);
        if (object.tag() != tag) {
            throw new SMException(String.format("Expected %s in SM response, found data object 0x%02X",
                    name, object.tag()));
        }
        return object;
    }

    private static int statusWord(SmDataObject status) {
        byte[] value = status.value();
        if (value.length != 2) {
            throw new SMException("DO'99' must contain exactly 2 bytes (SW1 SW2), got " + value.length);
        }
        return ((value[0] & 0xFF) << 8) | (value[1] & 0xFF);
    }

    private static byte[] macValue(SmDataObject mac) {
        if (mac.value().length != MAC_LENGTH) {
            throw new SMException("DO'8E' must contain an 8-byte MAC, got " + mac.value().length + " bytes");
        }
        return mac.value();
    }
}
