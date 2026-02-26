package name.velikodniy.jcexpress.assertions;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;

import java.util.Map;

/**
 * AssertJ assertions for {@link APDUResponse}.
 *
 * <p>Provides fluent, descriptive assertions for APDU responses with
 * human-readable error messages including status word descriptions.</p>
 */
public class APDUResponseAssert extends AbstractAssert<APDUResponseAssert, APDUResponse> {

    private static final Map<Integer, String> SW_DESCRIPTIONS = Map.ofEntries(
            Map.entry(0x9000, "success"),
            Map.entry(0x6100, "more data available"),
            Map.entry(0x6283, "selected file invalidated"),
            Map.entry(0x6300, "verification failed"),
            Map.entry(0x6400, "execution error"),
            Map.entry(0x6581, "memory failure"),
            Map.entry(0x6700, "wrong length"),
            Map.entry(0x6881, "logical channel not supported"),
            Map.entry(0x6882, "secure messaging not supported"),
            Map.entry(0x6982, "security status not satisfied"),
            Map.entry(0x6983, "authentication method blocked"),
            Map.entry(0x6984, "reference data not usable"),
            Map.entry(0x6985, "conditions of use not satisfied"),
            Map.entry(0x6986, "command not allowed"),
            Map.entry(0x6999, "applet selection failed"),
            Map.entry(0x6A80, "incorrect data parameters"),
            Map.entry(0x6A81, "function not supported"),
            Map.entry(0x6A82, "file not found"),
            Map.entry(0x6A83, "record not found"),
            Map.entry(0x6A84, "not enough memory"),
            Map.entry(0x6A86, "incorrect P1/P2"),
            Map.entry(0x6A88, "referenced data not found"),
            Map.entry(0x6B00, "incorrect P1/P2"),
            Map.entry(0x6C00, "wrong Le"),
            Map.entry(0x6D00, "INS not supported"),
            Map.entry(0x6E00, "CLA not supported"),
            Map.entry(0x6F00, "unknown error")
    );

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
            failWithMessage("Expected success (SW=9000) but was SW=%04X (%s)",
                    actual.sw(), describeStatusWord(actual.sw()));
        }
        return this;
    }

    /**
     * Verifies that the response has the expected status word.
     *
     * @param expectedSw the expected status word
     * @return this assertion for chaining
     */
    public APDUResponseAssert statusWord(int expectedSw) {
        isNotNull();
        if (actual.sw() != expectedSw) {
            failWithMessage("Expected SW=%04X (%s) but was SW=%04X (%s)",
                    expectedSw, describeStatusWord(expectedSw),
                    actual.sw(), describeStatusWord(actual.sw()));
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
            failWithMessage("Expected data length %d but was %d", expectedLength, actualLength);
        }
        return this;
    }

    /**
     * Verifies that the response data equals the expected bytes.
     *
     * @param expectedBytes the expected data bytes
     * @return this assertion for chaining
     */
    public APDUResponseAssert dataEquals(int... expectedBytes) {
        isNotNull();
        byte[] expected = new byte[expectedBytes.length];
        for (int i = 0; i < expectedBytes.length; i++) {
            expected[i] = (byte) expectedBytes[i];
        }
        byte[] actualData = actual.data();
        if (!java.util.Arrays.equals(actualData, expected)) {
            failWithMessage("Expected data [%s] but was [%s]",
                    Hex.encode(expected), Hex.encode(actualData));
        }
        return this;
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
     * Verifies that SW1 has the expected value.
     *
     * @param expectedSw1 the expected SW1 byte
     * @return this assertion for chaining
     */
    public APDUResponseAssert hasSw1(int expectedSw1) {
        isNotNull();
        if (actual.sw1() != expectedSw1) {
            failWithMessage("Expected SW1=%02X but was SW1=%02X (full SW=%04X, %s)",
                    expectedSw1, actual.sw1(), actual.sw(), describeStatusWord(actual.sw()));
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

    private static String formatBytes(int... bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    private static String describeStatusWord(int sw) {
        String desc = SW_DESCRIPTIONS.get(sw);
        if (desc != null) {
            return desc;
        }
        // Check for SW1-only matches (e.g. 61XX, 6CXX)
        desc = SW_DESCRIPTIONS.get(sw & 0xFF00);
        return desc != null ? desc : "unknown";
    }
}
