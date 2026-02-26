package name.velikodniy.jcexpress.assertions;

import name.velikodniy.jcexpress.tlv.TLVList;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions for {@link TLVList}.
 */
public class TLVListAssert extends AbstractAssert<TLVListAssert, TLVList> {

    /**
     * Creates a new assertion for the given TLV list.
     *
     * @param actual the TLV list to assert on
     */
    public TLVListAssert(TLVList actual) {
        super(actual, TLVListAssert.class);
    }

    /**
     * Verifies that the list has the expected number of elements.
     *
     * @param expected the expected size
     * @return this assertion for chaining
     */
    public TLVListAssert hasSize(int expected) {
        isNotNull();
        if (actual.size() != expected) {
            failWithMessage("Expected TLV list size %d but was %d", expected, actual.size());
        }
        return this;
    }

    /**
     * Verifies that the list contains an element with the given tag.
     *
     * @param tag the tag to look for
     * @return this assertion for chaining
     */
    public TLVListAssert containsTag(int tag) {
        isNotNull();
        if (!actual.contains(tag)) {
            failWithMessage("Expected TLV list to contain tag %s but it was not found",
                    formatTag(tag));
        }
        return this;
    }

    /**
     * Verifies that the list does not contain an element with the given tag.
     *
     * @param tag the tag that should be absent
     * @return this assertion for chaining
     */
    public TLVListAssert doesNotContainTag(int tag) {
        isNotNull();
        if (actual.contains(tag)) {
            failWithMessage("Expected TLV list not to contain tag %s but it was found",
                    formatTag(tag));
        }
        return this;
    }

    /**
     * Navigates to a specific tag for further assertions.
     *
     * @param tag the tag to navigate to
     * @return a TLVAssert on the found element
     */
    public TLVAssert tag(int tag) {
        isNotNull();
        return new TLVAssert(actual.find(tag).orElseThrow(() ->
                new AssertionError("Tag " + formatTag(tag) + " not found in TLV list")));
    }

    private static String formatTag(int tag) {
        return String.format(tag > 0xFF ? "0x%04X" : "0x%02X", tag);
    }
}
