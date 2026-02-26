package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.tlv.TLVList;
import name.velikodniy.jcexpress.tlv.TLVParser;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Represents a response to an APDU command, containing data and a status word.
 */
public final class APDUResponse {

    private final byte[] data;
    private final int sw;

    /**
     * Creates an APDUResponse from raw response bytes (data + 2-byte SW).
     *
     * @param responseBytes full response including trailing SW1 SW2
     */
    public APDUResponse(byte[] responseBytes) {
        if (responseBytes == null || responseBytes.length < 2) {
            throw new IllegalArgumentException("Response must be at least 2 bytes (SW1 SW2)");
        }
        int len = responseBytes.length;
        this.sw = ((responseBytes[len - 2] & 0xFF) << 8) | (responseBytes[len - 1] & 0xFF);
        this.data = Arrays.copyOfRange(responseBytes, 0, len - 2);
    }

    /**
     * Creates an APDUResponse from separate data and status word.
     *
     * @param data the response data (without SW)
     * @param sw   the status word
     */
    public APDUResponse(byte[] data, int sw) {
        this.data = data != null ? data.clone() : new byte[0];
        this.sw = sw;
    }

    /**
     * Returns the full status word (2 bytes).
     *
     * @return the status word (e.g., 0x9000)
     */
    public int sw() {
        return sw;
    }

    /**
     * Returns the first byte of the status word.
     *
     * @return SW1
     */
    public int sw1() {
        return (sw >> 8) & 0xFF;
    }

    /**
     * Returns the second byte of the status word.
     *
     * @return SW2
     */
    public int sw2() {
        return sw & 0xFF;
    }

    /**
     * Returns the response data (without status word).
     *
     * @return a copy of the response data
     */
    public byte[] data() {
        return data.clone();
    }

    /**
     * Returns the response data as a hex string.
     *
     * @return hex-encoded response data
     */
    public String dataAsHex() {
        return Hex.encode(data);
    }

    /**
     * Returns the response data as a UTF-8 string.
     *
     * @return response data decoded as UTF-8
     */
    public String dataAsString() {
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Parses the response data as BER-TLV.
     *
     * @return parsed TLV list
     * @throws name.velikodniy.jcexpress.tlv.TLVException if the data is malformed
     */
    public TLVList tlv() {
        return TLVParser.parse(data);
    }

    /**
     * Returns whether the status word indicates success (0x9000).
     *
     * @return true if SW == 0x9000
     */
    public boolean isSuccess() {
        return sw == 0x9000;
    }

    /**
     * Returns this response if successful, or throws an exception.
     *
     * <p>Useful for chaining: {@code card.send(0x80, 0x01).requireSuccess().data()}</p>
     *
     * @return this response (for chaining)
     * @throws IllegalStateException if SW != 0x9000
     */
    public APDUResponse requireSuccess() {
        if (!isSuccess()) {
            throw new IllegalStateException(
                    String.format("Expected SW 9000 but was %04X", sw));
        }
        return this;
    }

    @Override
    public String toString() {
        return "APDUResponse[data=" + dataAsHex() + ", SW=" + String.format("%04X", sw) + "]";
    }
}
