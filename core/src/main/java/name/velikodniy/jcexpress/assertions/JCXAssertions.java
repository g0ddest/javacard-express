package name.velikodniy.jcexpress.assertions;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.memory.MemoryInfo;
import name.velikodniy.jcexpress.tlv.TLV;
import name.velikodniy.jcexpress.tlv.TLVList;

/**
 * Entry point for JavaCard Express custom assertions.
 *
 * <p>Usage:</p>
 * <pre>
 * import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
 *
 * assertThat(response).isSuccess();
 * assertThat(response).dataAsString().isEqualTo("Hello");
 * assertThat(response).tlv().containsTag(0x6F);
 * </pre>
 */
public final class JCXAssertions {

    private JCXAssertions() {
    }

    /**
     * Creates a new assertion for an APDU response.
     *
     * @param actual the response to assert on
     * @return a new {@link APDUResponseAssert}
     */
    public static APDUResponseAssert assertThat(APDUResponse actual) {
        return new APDUResponseAssert(actual);
    }

    /**
     * Creates a new assertion for a TLV list.
     *
     * @param actual the TLV list to assert on
     * @return a new {@link TLVListAssert}
     */
    public static TLVListAssert assertThat(TLVList actual) {
        return new TLVListAssert(actual);
    }

    /**
     * Creates a new assertion for a single TLV element.
     *
     * @param actual the TLV to assert on
     * @return a new {@link TLVAssert}
     */
    public static TLVAssert assertThat(TLV actual) {
        return new TLVAssert(actual);
    }

    /**
     * Creates a new assertion for memory usage information.
     *
     * @param actual the memory info to assert on
     * @return a new {@link MemoryInfoAssert}
     * @see name.velikodniy.jcexpress.memory.MemoryProbeApplet
     */
    public static MemoryInfoAssert assertThat(MemoryInfo actual) {
        return new MemoryInfoAssert(actual);
    }
}
