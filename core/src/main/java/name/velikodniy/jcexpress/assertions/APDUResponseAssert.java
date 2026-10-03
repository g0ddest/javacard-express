package name.velikodniy.jcexpress.assertions;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SW;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractByteArrayAssert;
import org.assertj.core.api.AbstractIntegerAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * AssertJ assertions for {@link APDUResponse}.
 *
 * <p>Failure messages name status words with their meaning ({@link SW#describe(int)}). A mismatch of status word
 * or data carries the expected and the actual value (hex), so IDEs show a diff. Status words can be given as ints
 * ({@code 0x6982}), as {@link SW} constants or as the {@code short} constants of the Java Card API
 * ({@code ISO7816.SW_NO_ERROR}).</p>
 *
 * <pre>
 * assertThat(card.send(GET_BALANCE)).isSuccess().u16(0).isEqualTo(100);
 * assertThat(card.send(DEBIT)).hasStatusWord(SW.SECURITY_STATUS_NOT_SATISFIED).hasNoData();
 * assertThat(card.send(READ)).hasDataHex("01 02 03");
 * assertThat(card.send(READ)).data().hasSize(3).startsWith((byte) 0x01);
 * </pre>
 */
public class APDUResponseAssert extends AbstractAssert<APDUResponseAssert, APDUResponse> {

    /**
     * Creates a new assertion for the given response.
     *
     * @param actual the response to assert on
     */
    public APDUResponseAssert(APDUResponse actual) {
        super(actual, APDUResponseAssert.class);
    }

    /**
     * Verifies that the response status word is 0x9000 (success).
     *
     * @return this assertion for chaining
     */
    public APDUResponseAssert isSuccess() {
        isNotNull();
        if (!actual.isSuccess()) {
            failWithActualExpectedAndMessage(SW.format(actual.sw()), SW.format(SW.NO_ERROR),
                    "Expected success (SW=9000) but was SW=%04X (%s)", actual.sw(), SW.describe(actual.sw()));
        }
        return this;
    }

    /**
     * Verifies that the response status word is not 0x9000.
     *
     * @return this assertion for chaining
     */
    public APDUResponseAssert isNotSuccess() {
        isNotNull();
        if (actual.isSuccess()) {
            failWithMessage("Expected a status word other than %s but was SW=9000", SW.format(SW.NO_ERROR));
        }
        return this;
    }

    /**
     * Verifies that the response has the expected status word.
     *
     * <p>The status word can be given as an int ({@code 0x6982}) or as a {@code short} constant of the Java Card API
     * ({@code ISO7816.SW_NO_ERROR}, which reaches this method as {@code 0xFFFF9000}): SW1-SW2 are two bytes
     * (ISO/IEC 7816-4 5.1.3), so the value counts modulo {@code 0x10000}.</p>
     *
     * @param expectedSw the expected status word, {@code -32768} to {@code 0xFFFF}
     * @return this assertion for chaining
     * @throws IllegalArgumentException if {@code expectedSw} is not a two-byte value
     * @see #hasStatusWord(int)
     */
    public APDUResponseAssert statusWord(int expectedSw) {
        int expected = statusBytes("A status word", expectedSw, 0xFFFF);
        isNotNull();
        if (actual.sw() != expected) {
            failWithActualExpectedAndMessage(SW.format(actual.sw()), SW.format(expected),
                    "Expected SW=%04X (%s) but was SW=%04X (%s)",
                    expected, SW.describe(expected), actual.sw(), SW.describe(actual.sw()));
        }
        return this;
    }

    /**
     * Verifies that the response has the expected status word; the same as {@link #statusWord(int)}, under the
     * name AssertJ users look for.
     *
     * @param expectedSw the expected status word: an int ({@code 0x6982}), an {@link SW} constant or a
     *                   {@code short} constant of the Java Card API
     * @return this assertion for chaining
     * @throws IllegalArgumentException if {@code expectedSw} is not a two-byte value
     */
    public APDUResponseAssert hasStatusWord(int expectedSw) {
        return statusWord(expectedSw);
    }

    /**
     * Verifies that the response has one of the expected status words, e.g. {@code hasStatusWordIn(0x9000, 0x6310)}.
     *
     * @param expectedSws the accepted status words (ints, {@link SW} constants or Java Card {@code short} constants)
     * @return this assertion for chaining
     * @throws IllegalArgumentException if no status word is given or one is not a two-byte value
     */
    public APDUResponseAssert hasStatusWordIn(int... expectedSws) {
        if (expectedSws.length == 0) {
            throw new IllegalArgumentException("hasStatusWordIn needs at least one status word");
        }
        int[] expected = Arrays.stream(expectedSws).map(sw -> statusBytes("A status word", sw, 0xFFFF)).toArray();
        isNotNull();
        if (Arrays.stream(expected).noneMatch(sw -> sw == actual.sw())) {
            String accepted = Arrays.stream(expected).mapToObj(SW::format).collect(Collectors.joining(", "));
            failWithMessage("Expected SW in [%s] but was SW=%04X (%s)", accepted, actual.sw(),
                    SW.describe(actual.sw()));
        }
        return this;
    }

    /**
     * Verifies that SW1 has the expected value.
     *
     * @param expectedSw1 the expected SW1 byte, as an int ({@code 0x90}) or a {@code byte} ({@code (byte) 0x90}),
     *                    {@code -128} to {@code 0xFF}
     * @return this assertion for chaining
     * @throws IllegalArgumentException if {@code expectedSw1} is not a one-byte value
     */
    public APDUResponseAssert hasSw1(int expectedSw1) {
        int expected = statusBytes("SW1", expectedSw1, 0xFF);
        isNotNull();
        if (actual.sw1() != expected) {
            failWithMessage("Expected SW1=%02X but was SW1=%02X (full SW=%04X, %s)",
                    expected, actual.sw1(), actual.sw(), SW.describe(actual.sw()));
        }
        return this;
    }

    /**
     * Verifies that SW2 has the expected value, e.g. the counter of {@code 63CX}.
     *
     * @param expectedSw2 the expected SW2 byte, as an int ({@code 0xC2}) or a {@code byte}, {@code -128} to
     *                    {@code 0xFF}
     * @return this assertion for chaining
     * @throws IllegalArgumentException if {@code expectedSw2} is not a one-byte value
     */
    public APDUResponseAssert hasSw2(int expectedSw2) {
        int expected = statusBytes("SW2", expectedSw2, 0xFF);
        isNotNull();
        if (actual.sw2() != expected) {
            failWithMessage("Expected SW2=%02X but was SW2=%02X (full SW=%04X, %s)",
                    expected, actual.sw2(), actual.sw(), SW.describe(actual.sw()));
        }
        return this;
    }

    /**
     * Verifies that the response data has the expected length.
     *
     * @param expectedLength the expected data length in bytes
     * @return this assertion for chaining
     */
    public APDUResponseAssert hasDataLength(int expectedLength) {
        isNotNull();
        int actualLength = actual.data().length;
        if (actualLength != expectedLength) {
            failWithActualExpectedAndMessage(actualLength, expectedLength,
                    "Expected data length %d but was %d", expectedLength, actualLength);
        }
        return this;
    }

    /**
     * Verifies that the response data equals the expected bytes.
     *
     * @param expectedBytes the expected data bytes, as ints ({@code 0x80}) or bytes
     * @return this assertion for chaining
     */
    public APDUResponseAssert dataEquals(int... expectedBytes) {
        byte[] expected = new byte[expectedBytes.length];
        for (int i = 0; i < expectedBytes.length; i++) {
            expected[i] = (byte) expectedBytes[i];
        }
        return hasData(expected);
    }

    /**
     * Verifies that the response data equals the expected bytes.
     *
     * @param expected the expected data
     * @return this assertion for chaining
     */
    public APDUResponseAssert hasData(byte[] expected) {
        isNotNull();
        byte[] actualData = actual.data();
        if (!Arrays.equals(actualData, expected)) {
            failWithActualExpectedAndMessage(Hex.encode(actualData), Hex.encode(expected),
                    "Expected data [%s] but was [%s]", Hex.encode(expected), Hex.encode(actualData));
        }
        return this;
    }

    /**
     * Verifies that the response data equals the bytes written as hex.
     *
     * @param expectedHex the expected data as hex, spaces allowed ({@code "00 46"})
     * @return this assertion for chaining
     * @throws IllegalArgumentException if {@code expectedHex} is not hex
     */
    public APDUResponseAssert hasDataHex(String expectedHex) {
        return hasData(Hex.decode(expectedHex));
    }

    /**
     * Verifies that the response has no data (only a status word).
     *
     * @return this assertion for chaining
     */
    public APDUResponseAssert hasNoData() {
        isNotNull();
        byte[] actualData = actual.data();
        if (actualData.length != 0) {
            failWithActualExpectedAndMessage(Hex.encode(actualData), "", "Expected no data but was [%s] (%d bytes)",
                    Hex.encode(actualData), actualData.length);
        }
        return this;
    }

    /**
     * Returns an AssertJ assertion on the response data, for everything AssertJ checks on byte arrays.
     *
     * <pre>
     * assertThat(response).data().hasSize(4).startsWith((byte) 0x6F);
     * </pre>
     *
     * @return a byte array assertion on a copy of the data, described as the data of this response
     */
    public AbstractByteArrayAssert<?> data() {
        isNotNull();
        return Assertions.assertThat(actual.data()).as("data of %s", actual);
    }

    /**
     * Returns an assertion on an unsigned byte of the response data ({@link APDUResponse#u8(int)}).
     *
     * @param offset the offset in the data
     * @return an integer assertion on the byte, {@code 0} to {@code 255}
     */
    public AbstractIntegerAssert<?> u8(int offset) {
        requireData("u8", offset, 1);
        return Assertions.assertThat(actual.u8(offset)).as("u8 at offset %d of the data of %s", offset, actual);
    }

    /**
     * Returns an assertion on an unsigned big-endian 16-bit number of the response data
     * ({@link APDUResponse#u16(int)}): {@code assertThat(response).isSuccess().u16(0).isEqualTo(70)}.
     *
     * @param offset the offset of the high byte in the data
     * @return an integer assertion on the number, {@code 0} to {@code 65535}
     */
    public AbstractIntegerAssert<?> u16(int offset) {
        requireData("u16", offset, 2);
        return Assertions.assertThat(actual.u16(offset)).as("u16 at offset %d of the data of %s", offset, actual);
    }

    /**
     * Returns an assertion on a signed big-endian 16-bit number of the response data, the {@code short} an applet
     * wrote with {@code Util.setShort} ({@link APDUResponse#s16(int)}).
     *
     * @param offset the offset of the high byte in the data
     * @return an integer assertion on the number, {@code -32768} to {@code 32767}
     */
    public AbstractIntegerAssert<?> s16(int offset) {
        requireData("s16", offset, 2);
        return Assertions.assertThat(actual.s16(offset)).as("s16 at offset %d of the data of %s", offset, actual);
    }

    /**
     * Returns a string assertion on the response data interpreted as UTF-8.
     *
     * @return a string assertion for chaining
     */
    public AbstractStringAssert<?> dataAsString() {
        isNotNull();
        return Assertions.assertThat(actual.dataAsString());
    }

    /**
     * Parses the response data as BER-TLV and returns a TLV list assertion.
     *
     * @return a TLVListAssert for chaining
     */
    public TLVListAssert tlv() {
        isNotNull();
        return new TLVListAssert(actual.tlv());
    }

    /**
     * Returns a string assertion on the response data as a hex string.
     *
     * @return a string assertion for chaining
     */
    public AbstractStringAssert<?> dataAsHex() {
        isNotNull();
        return Assertions.assertThat(actual.dataAsHex());
    }

    /**
     * Verifies that the response data starts with the given bytes.
     *
     * @param prefix the expected prefix bytes
     * @return this assertion for chaining
     */
    public APDUResponseAssert dataStartsWith(int... prefix) {
        isNotNull();
        byte[] actualData = actual.data();
        if (actualData.length < prefix.length) {
            failWithMessage("Expected data to start with [%s] but data length is %d",
                    formatBytes(prefix), actualData.length);
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((actualData[i] & 0xFF) != (prefix[i] & 0xFF)) {
                failWithMessage("Expected data to start with [%s] but was [%s]",
                        formatBytes(prefix), Hex.encode(actualData));
                break;
            }
        }
        return this;
    }

    /**
     * Verifies that the response data ends with the given bytes.
     *
     * @param suffix the expected suffix bytes
     * @return this assertion for chaining
     */
    public APDUResponseAssert dataEndsWith(int... suffix) {
        isNotNull();
        byte[] actualData = actual.data();
        if (actualData.length < suffix.length) {
            failWithMessage("Expected data to end with [%s] but data length is %d",
                    formatBytes(suffix), actualData.length);
        }
        int offset = actualData.length - suffix.length;
        for (int i = 0; i < suffix.length; i++) {
            if ((actualData[offset + i] & 0xFF) != (suffix[i] & 0xFF)) {
                failWithMessage("Expected data to end with [%s] but was [%s]",
                        formatBytes(suffix), Hex.encode(actualData));
                break;
            }
        }
        return this;
    }

    /**
     * Parses the response data as BER-TLV and verifies that a tag is present.
     *
     * @param tag the expected TLV tag
     * @return this assertion for chaining
     */
    public APDUResponseAssert tlvContains(int tag) {
        isNotNull();
        var list = actual.tlv();
        if (!list.contains(tag)) {
            failWithMessage("Expected TLV data to contain tag %s but found tags: %s",
                    String.format(tag > 0xFF ? "%04X" : "%02X", tag), list);
        }
        return this;
    }

    /** Fails (as an assertion) when the data has no {@code width} bytes at {@code offset}, naming the status word. */
    private void requireData(String reader, int offset, int width) {
        isNotNull();
        int length = actual.data().length;
        if (offset < 0 || offset > length - width) {
            failWithMessage("Expected response data with %d byte%s at offset %d for %s, but the data has %d byte%s"
                            + " (SW=%04X, %s)", width, width == 1 ? "" : "s", offset, reader, length,
                    length == 1 ? "" : "s", actual.sw(), SW.describe(actual.sw()));
        }
    }

    private static String formatBytes(int... bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * A status word or byte given as an unsigned value or as a signed Java Card constant ({@code short} or
     * {@code byte}), as the unsigned value.
     */
    private static int statusBytes(String what, int value, int mask) {
        int signedMin = -(mask + 1) / 2;
        if (value < signedMin || value > mask) {
            throw new IllegalArgumentException(String.format("%s is %d byte%s (ISO/IEC 7816-4 5.1.3): 0x0 to 0x%X,"
                    + " or a signed Java Card constant from %d; got 0x%X", what, mask == 0xFF ? 1 : 2,
                    mask == 0xFF ? "" : "s", mask, signedMin, value));
        }
        return value & mask;
    }
}
