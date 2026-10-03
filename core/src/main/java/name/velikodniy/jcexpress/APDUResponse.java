package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.tlv.TLVList;
import name.velikodniy.jcexpress.tlv.TLVParser;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Represents a response to an APDU command, containing data and a status word.
 *
 * <p>Responses are values: two responses with the same data and status word are {@link #equals(Object) equal}.
 * {@link #fromHex(String)} is the only static factory that takes a {@code String}, so JUnit converts a
 * {@code @CsvSource} or {@code @ValueSource} argument to an {@code APDUResponse} without an explicit converter.</p>
 *
 * <p>A response returned by a session also remembers the command it answers ({@link #inReplyTo(byte[])}), which
 * {@link #requireSuccess()} shows when it fails; the command takes no part in equality.</p>
 */
public final class APDUResponse {

    private final byte[] data;
    private final int sw;
    /** The command this response answers, if the session supplied it; not part of the value. */
    private final byte[] command;

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
        this.command = null;
    }

    /**
     * Creates an APDUResponse from separate data and status word.
     *
     * @param data the response data (without SW)
     * @param sw   the status word, as an int ({@code 0x9000}) or as a {@code short} constant of the Java Card API
     *             ({@code ISO7816.SW_NO_ERROR}): SW1-SW2 are two bytes (ISO/IEC 7816-4 5.1.3), so the value counts
     *             modulo {@code 0x10000}
     * @throws IllegalArgumentException if {@code sw} is outside {@code -32768} to {@code 0xFFFF}
     */
    public APDUResponse(byte[] data, int sw) {
        this.sw = SW.unsigned(sw);
        this.data = data != null ? data.clone() : new byte[0];
        this.command = null;
    }

    private APDUResponse(APDUResponse response, byte[] command) {
        this.data = response.data;
        this.sw = response.sw;
        this.command = command;
    }

    /**
     * Parses a response written as hex: the response data followed by the status word, e.g.
     * {@code "48656C6C6F9000"} or {@code "6A82"}. Spaces are ignored.
     *
     * <p>This is the only static factory of the class that takes a {@code String}, so JUnit's implicit argument
     * conversion uses it: {@code @CsvSource("80010000, 48656C6C6F9000") void t(String command, APDUResponse
     * expected)}.</p>
     *
     * @param hex the response bytes (data and SW1 SW2) as hex
     * @return the response
     * @throws IllegalArgumentException if the text is not hex or has fewer than two bytes
     */
    public static APDUResponse fromHex(String hex) {
        return new APDUResponse(Hex.decode(hex));
    }

    /**
     * Returns this response together with the command it answers. {@link #requireSuccess()} shows the command
     * when it fails; data, status word and equality are unchanged. The sessions of JavaCard Express return
     * responses that know their command.
     *
     * @param command the command APDU this response answers
     * @return a response with the same data and status word that remembers a copy of {@code command}
     * @throws IllegalArgumentException if {@code command} is null
     */
    public APDUResponse inReplyTo(byte[] command) {
        if (command == null) {
            throw new IllegalArgumentException("command must not be null");
        }
        return new APDUResponse(this, command.clone());
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
     * Returns a range of the response data.
     *
     * @param from the first offset (inclusive)
     * @param to   the end offset (exclusive), at most the data length
     * @return a copy of the bytes {@code from} to {@code to}
     * @throws IndexOutOfBoundsException if the range is not within the data
     */
    public byte[] data(int from, int to) {
        if (from < 0 || to > data.length || from > to) {
            throw new IndexOutOfBoundsException(String.format("data %d to %d is outside the response data of %s",
                    from, to, describeData()));
        }
        return Arrays.copyOfRange(data, from, to);
    }

    /**
     * Returns an unsigned byte of the response data.
     *
     * @param offset the offset in the data
     * @return the byte, {@code 0} to {@code 255}
     * @throws IndexOutOfBoundsException if the data has no byte at {@code offset}
     */
    public int u8(int offset) {
        check("u8", offset, 1);
        return data[offset] & 0xFF;
    }

    /**
     * Returns an unsigned big-endian 16-bit number of the response data, as an applet writes a {@code short} with
     * {@code Util.setShort}. The result is an {@code int}, so {@code assertThat(response.u16(0)).isEqualTo(70)}
     * compares numbers (with a {@code short}, AssertJ would compare a {@code Short} with an {@code Integer}).
     *
     * @param offset the offset of the first (high) byte in the data
     * @return the number, {@code 0} to {@code 65535}
     * @throws IndexOutOfBoundsException if the data has no two bytes at {@code offset}
     */
    public int u16(int offset) {
        return bigEndian16("u16", offset);
    }

    /**
     * Returns a signed big-endian 16-bit number of the response data: the value of a {@code short} that an applet
     * wrote with {@code Util.setShort} (and {@code Util.getShort} would read), as an {@code int}.
     *
     * @param offset the offset of the first (high) byte in the data
     * @return the number, {@code -32768} to {@code 32767}
     * @throws IndexOutOfBoundsException if the data has no two bytes at {@code offset}
     */
    public int s16(int offset) {
        return (short) bigEndian16("s16", offset);
    }

    private int bigEndian16(String reader, int offset) {
        check(reader, offset, 2);
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private void check(String reader, int offset, int width) {
        if (offset < 0 || offset > data.length - width) {
            throw new IndexOutOfBoundsException(String.format("%s at offset %d needs %d byte%s, but the response"
                    + " data has %s", reader, offset, width, width == 1 ? "" : "s", describeData()));
        }
    }

    private String describeData() {
        String count = data.length + (data.length == 1 ? " byte" : " bytes");
        return data.length == 0 ? count : count + ": " + Hex.encode(data);
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
     * Returns this response if successful, or fails like an assertion.
     *
     * <p>Useful for chaining: {@code card.send(0x80, 0x01).requireSuccess().data()}. The error is an
     * {@link AssertionError}, so a test that calls this counts as failed (not as an error); its message shows the
     * exchange in the transcript format: {@code C:} the command (when the response {@link #inReplyTo(byte[])
     * knows it}), {@code R:} the response.</p>
     *
     * @return this response (for chaining)
     * @throws UnexpectedStatusWordError if SW != 0x9000
     */
    public APDUResponse requireSuccess() {
        if (!isSuccess()) {
            throw new UnexpectedStatusWordError(this);
        }
        return this;
    }

    /**
     * Returns this response if its status word is one of the expected ones, or fails like an assertion. For
     * commands whose success has more than one status word, e.g. {@code requireSw(SW.NO_ERROR, 0x6310)}.
     *
     * @param expected the accepted status words, as ints ({@code 0x9000}) or {@code short} constants of the Java Card
     *                 API ({@code ISO7816.SW_NO_ERROR})
     * @return this response (for chaining)
     * @throws UnexpectedStatusWordError if the status word is none of {@code expected}; the message lists them with
     *                                   their meaning and shows the exchange
     * @throws IllegalArgumentException  if {@code expected} is empty or holds a value that is not two bytes
     */
    public APDUResponse requireSw(int... expected) {
        if (expected.length == 0) {
            throw new IllegalArgumentException("requireSw needs at least one status word");
        }
        int[] accepted = new int[expected.length];
        for (int i = 0; i < expected.length; i++) {
            accepted[i] = SW.unsigned(expected[i]);
            if (accepted[i] == sw) {
                return this;
            }
        }
        throw new UnexpectedStatusWordError(this, accepted);
    }

    /** The command this response answers, or null (for {@link UnexpectedStatusWordError}). */
    byte[] command() {
        return command == null ? null : command.clone();
    }

    /** The response as sent by the card: data followed by SW1 SW2. */
    byte[] toBytes() {
        byte[] bytes = Arrays.copyOf(data, data.length + 2);
        bytes[data.length] = (byte) (sw >> 8);
        bytes[data.length + 1] = (byte) sw;
        return bytes;
    }

    /**
     * Compares data and status word; the command a response answers is not compared.
     *
     * @param other the object to compare with
     * @return true if {@code other} is a response with the same data and status word
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof APDUResponse that && sw == that.sw && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(data) + sw;
    }

    @Override
    public String toString() {
        return "APDUResponse[data=" + dataAsHex() + ", SW=" + String.format("%04X", sw) + "]";
    }
}
