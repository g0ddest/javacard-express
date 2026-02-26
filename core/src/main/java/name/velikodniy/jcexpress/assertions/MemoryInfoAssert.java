package name.velikodniy.jcexpress.assertions;

import name.velikodniy.jcexpress.memory.MemoryInfo;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions for {@link MemoryInfo}.
 *
 * <p>Provides fluent assertions for checking memory consumption
 * of JavaCard applets via {@link name.velikodniy.jcexpress.memory.MemoryProbeApplet}.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * MemoryInfo info = MemoryInfo.from(response);
 * assertThat(info)
 *     .persistentBelow(16384)
 *     .transientDeselectBelow(512);
 * </pre>
 */
public class MemoryInfoAssert extends AbstractAssert<MemoryInfoAssert, MemoryInfo> {

    public MemoryInfoAssert(MemoryInfo actual) {
        super(actual, MemoryInfoAssert.class);
    }

    /**
     * Verifies that available persistent memory is below the given threshold.
     *
     * <p>Use this to assert that your applet has NOT consumed more EEPROM
     * than expected. A lower "available" value means more memory was used.</p>
     *
     * @param maxBytes the maximum available persistent memory in bytes
     * @return this assertion for chaining
     */
    public MemoryInfoAssert persistentBelow(int maxBytes) {
        isNotNull();
        if (actual.persistent() >= maxBytes) {
            failWithMessage("Expected persistent memory below %d bytes but was %d",
                    maxBytes, actual.persistent());
        }
        return this;
    }

    /**
     * Verifies that available persistent memory is at least the given amount.
     *
     * <p>Use this to ensure enough EEPROM remains for expected operations.</p>
     *
     * @param minBytes the minimum expected available persistent memory
     * @return this assertion for chaining
     */
    public MemoryInfoAssert persistentAtLeast(int minBytes) {
        isNotNull();
        if (actual.persistent() < minBytes) {
            failWithMessage("Expected at least %d bytes persistent memory but only %d available",
                    minBytes, actual.persistent());
        }
        return this;
    }

    /**
     * Verifies that available transient CLEAR_ON_DESELECT memory is below the threshold.
     *
     * @param maxBytes the maximum available transient deselect memory
     * @return this assertion for chaining
     */
    public MemoryInfoAssert transientDeselectBelow(int maxBytes) {
        isNotNull();
        if (actual.transientDeselect() >= maxBytes) {
            failWithMessage("Expected transient deselect memory below %d bytes but was %d",
                    maxBytes, actual.transientDeselect());
        }
        return this;
    }

    /**
     * Verifies that available transient CLEAR_ON_DESELECT memory is at least the given amount.
     *
     * @param minBytes the minimum expected available transient deselect memory
     * @return this assertion for chaining
     */
    public MemoryInfoAssert transientDeselectAtLeast(int minBytes) {
        isNotNull();
        if (actual.transientDeselect() < minBytes) {
            failWithMessage("Expected at least %d bytes transient deselect memory but only %d available",
                    minBytes, actual.transientDeselect());
        }
        return this;
    }

    /**
     * Verifies that available transient CLEAR_ON_RESET memory is below the threshold.
     *
     * @param maxBytes the maximum available transient reset memory
     * @return this assertion for chaining
     */
    public MemoryInfoAssert transientResetBelow(int maxBytes) {
        isNotNull();
        if (actual.transientReset() >= maxBytes) {
            failWithMessage("Expected transient reset memory below %d bytes but was %d",
                    maxBytes, actual.transientReset());
        }
        return this;
    }

    /**
     * Verifies that available transient CLEAR_ON_RESET memory is at least the given amount.
     *
     * @param minBytes the minimum expected available transient reset memory
     * @return this assertion for chaining
     */
    public MemoryInfoAssert transientResetAtLeast(int minBytes) {
        isNotNull();
        if (actual.transientReset() < minBytes) {
            failWithMessage("Expected at least %d bytes transient reset memory but only %d available",
                    minBytes, actual.transientReset());
        }
        return this;
    }
}
