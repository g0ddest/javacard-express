package name.velikodniy.jcexpress.assertions;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.tlv.TLV;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions for a single {@link TLV} element.
 */
public class TLVAssert extends AbstractAssert<TLVAssert, TLV> {

    public TLVAssert(TLV actual) {
        super(actual, TLVAssert.class);
    }

    /**
     * Verifies that this TLV is constructed (contains children).
     *
     * @return this assertion for chaining
     */
    public TLVAssert isConstructed() {
        isNotNull();
        if (!actual.isConstructed()) {
            failWithMessage("Expected tag %s to be constructed but it is primitive",
                    formatTag(actual.tag()));
        }
        return this;
    }

    /**
     * Verifies that this TLV is primitive (no children).
     *
     * @return this assertion for chaining
     */
    public TLVAssert isPrimitive() {
        isNotNull();
        if (actual.isConstructed()) {
            failWithMessage("Expected tag %s to be primitive but it is constructed",
                    formatTag(actual.tag()));
        }
        return this;
    }

    /**
     * Verifies the value equals the expected hex string.
     *
     * @param expectedHex expected value as hex
     * @return this assertion for chaining
     */
    public TLVAssert hasValue(String expectedHex) {
        isNotNull();
        String actualHex = actual.valueHex();
        String normalized = expectedHex.replace(" ", "").toUpperCase();
        if (!actualHex.equals(normalized)) {
            failWithMessage("Expected tag %s value [%s] but was [%s]",
                    formatTag(actual.tag()), normalized, actualHex);
        }
        return this;
    }

    /**
     * Verifies the value equals the expected bytes.
     *
     * @param expectedBytes expected value
     * @return this assertion for chaining
     */
    public TLVAssert hasValue(byte[] expectedBytes) {
        return hasValue(Hex.encode(expectedBytes));
    }

    /**
     * Verifies the value length.
     *
     * @param expectedLength expected length in bytes
     * @return this assertion for chaining
     */
    public TLVAssert hasLength(int expectedLength) {
        isNotNull();
        if (actual.length() != expectedLength) {
            failWithMessage("Expected tag %s length %d but was %d",
                    formatTag(actual.tag()), expectedLength, actual.length());
        }
        return this;
    }

    /**
     * Navigates into a child tag of a constructed TLV.
     *
     * @param childTag the child tag to find
     * @return a TLVAssert on the child element
     */
    public TLVAssert tag(int childTag) {
        isNotNull();
        if (!actual.isConstructed()) {
            failWithMessage("Cannot navigate to child tag %s: tag %s is primitive",
                    formatTag(childTag), formatTag(actual.tag()));
        }
        TLV child = actual.find(childTag).orElse(null);
        if (child == null) {
            failWithMessage("Child tag %s not found in tag %s",
                    formatTag(childTag), formatTag(actual.tag()));
        }
        return new TLVAssert(child);
    }

    private static String formatTag(int tag) {
        return String.format(tag > 0xFF ? "0x%04X" : "0x%02X", tag);
    }
}
