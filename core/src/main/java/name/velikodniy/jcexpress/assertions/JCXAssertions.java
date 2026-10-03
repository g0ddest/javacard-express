package name.velikodniy.jcexpress.assertions;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.memory.MemoryInfo;
import name.velikodniy.jcexpress.tlv.TLV;
import name.velikodniy.jcexpress.tlv.TLVList;
import org.assertj.core.api.Assertions;

/**
 * Entry point for the assertions of JavaCard Express, and of AssertJ: the class extends AssertJ's
 * {@link Assertions}, so one static import gives {@code assertThat} for APDU responses, TLV data and memory
 * information as well as for everything AssertJ asserts on ({@code byte[]}, numbers, strings, collections, ...),
 * and {@code assertThatThrownBy}, {@code fail} and the other entry points of AssertJ.
 *
 * <p>Usage:</p>
 * <pre>
 * import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
 *
 * assertThat(response).isSuccess();
 * assertThat(response).dataAsString().isEqualTo("Hello");
 * assertThat(response).tlv().containsTag(0x6F);
 * assertThat(response.u16(0)).isEqualTo(70);        // AssertJ's assertThat(int), same import
 * assertThat(response.data()).hasSize(2);            // AssertJ's assertThat(byte[])
 * </pre>
 */
public final class JCXAssertions extends Assertions {

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
