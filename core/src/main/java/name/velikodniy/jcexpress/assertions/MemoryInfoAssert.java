package name.velikodniy.jcexpress.assertions;

import name.velikodniy.jcexpress.memory.MemoryInfo;
import org.assertj.core.api.AbstractAssert;

import java.util.function.ToIntFunction;

/**
 * AssertJ assertions for {@link MemoryInfo}, the memory available on a card as measured by
 * {@link name.velikodniy.jcexpress.memory.MemoryProbeApplet}.
 *
 * <p>The {@code *Below} and {@code *AtLeast} assertions check one measurement of <em>available</em> memory.
 * To check how much memory an applet <em>consumed</em>, measure before and after installing or exercising
 * it and use the {@code *ConsumedAtMost} assertions:</p>
 * <pre>
 * MemoryInfo before = MemoryInfo.from(card.send(0x80, 0x01));
 * card.install(MyApplet.class);
 * card.select(MemoryProbeApplet.class);
 * MemoryInfo after = MemoryInfo.from(card.send(0x80, 0x01));
 * assertThat(after)
 *     .persistentConsumedAtMost(before, 4096)
 *     .transientDeselectConsumedAtMost(before, 256)
 *     .persistentAtLeast(16384);   // enough left for the next applet
 * </pre>
 */
public class MemoryInfoAssert extends AbstractAssert<MemoryInfoAssert, MemoryInfo> {

    /**
     * Creates an assertion for a measurement.
     *
     * @param actual the measurement
     */
    public MemoryInfoAssert(MemoryInfo actual) {
        super(actual, MemoryInfoAssert.class);
    }

    /**
     * Verifies that the available persistent memory is below the given threshold.
     *
     * @param maxBytes the exclusive upper bound of available persistent memory, in bytes
     * @return this assertion for chaining
     */
    public MemoryInfoAssert persistentBelow(int maxBytes) {
        return below("persistent memory", MemoryInfo::persistent, maxBytes);
    }

    /**
     * Verifies that at least the given amount of persistent memory is available.
     *
     * @param minBytes the minimum available persistent memory, in bytes
     * @return this assertion for chaining
     */
    public MemoryInfoAssert persistentAtLeast(int minBytes) {
        return atLeast("persistent memory", MemoryInfo::persistent, minBytes);
    }

    /**
     * Verifies that at most the given amount of persistent memory was consumed since an earlier
     * measurement, i.e. {@code before.persistent() - actual.persistent() <= maxBytes}.
     *
     * @param before   the earlier measurement
     * @param maxBytes the maximum consumption, in bytes
     * @return this assertion for chaining
     */
    public MemoryInfoAssert persistentConsumedAtMost(MemoryInfo before, int maxBytes) {
        return consumedAtMost("persistent memory", MemoryInfo::persistent, before, maxBytes);
    }

    /**
     * Verifies that the available transient CLEAR_ON_DESELECT memory is below the given threshold.
     *
     * @param maxBytes the exclusive upper bound of available memory, in bytes
     * @return this assertion for chaining
     */
    public MemoryInfoAssert transientDeselectBelow(int maxBytes) {
        return below("transient deselect memory", MemoryInfo::transientDeselect, maxBytes);
    }

    /**
     * Verifies that at least the given amount of transient CLEAR_ON_DESELECT memory is available.
     *
     * @param minBytes the minimum available memory, in bytes
     * @return this assertion for chaining
     */
    public MemoryInfoAssert transientDeselectAtLeast(int minBytes) {
        return atLeast("transient deselect memory", MemoryInfo::transientDeselect, minBytes);
    }

    /**
     * Verifies that at most the given amount of transient CLEAR_ON_DESELECT memory was consumed since an
     * earlier measurement.
     *
     * @param before   the earlier measurement
     * @param maxBytes the maximum consumption, in bytes
     * @return this assertion for chaining
     */
    public MemoryInfoAssert transientDeselectConsumedAtMost(MemoryInfo before, int maxBytes) {
        return consumedAtMost("transient deselect memory", MemoryInfo::transientDeselect, before, maxBytes);
    }

    /**
     * Verifies that the available transient CLEAR_ON_RESET memory is below the given threshold.
     *
     * @param maxBytes the exclusive upper bound of available memory, in bytes
     * @return this assertion for chaining
     */
    public MemoryInfoAssert transientResetBelow(int maxBytes) {
        return below("transient reset memory", MemoryInfo::transientReset, maxBytes);
    }

    /**
     * Verifies that at least the given amount of transient CLEAR_ON_RESET memory is available.
     *
     * @param minBytes the minimum available memory, in bytes
     * @return this assertion for chaining
     */
    public MemoryInfoAssert transientResetAtLeast(int minBytes) {
        return atLeast("transient reset memory", MemoryInfo::transientReset, minBytes);
    }

    /**
     * Verifies that at most the given amount of transient CLEAR_ON_RESET memory was consumed since an
     * earlier measurement.
     *
     * @param before   the earlier measurement
     * @param maxBytes the maximum consumption, in bytes
     * @return this assertion for chaining
     */
    public MemoryInfoAssert transientResetConsumedAtMost(MemoryInfo before, int maxBytes) {
        return consumedAtMost("transient reset memory", MemoryInfo::transientReset, before, maxBytes);
    }

    private MemoryInfoAssert below(String type, ToIntFunction<MemoryInfo> amount, int maxBytes) {
        isNotNull();
        int available = amount.applyAsInt(actual);
        if (available >= maxBytes) {
            failWithMessage("Expected available %s below %d bytes but was %d", type, maxBytes, available);
        }
        return this;
    }

    private MemoryInfoAssert atLeast(String type, ToIntFunction<MemoryInfo> amount, int minBytes) {
        isNotNull();
        int available = amount.applyAsInt(actual);
        if (available < minBytes) {
            failWithMessage("Expected at least %d bytes of available %s but only %d available", minBytes, type,
                    available);
        }
        return this;
    }

    private MemoryInfoAssert consumedAtMost(String type, ToIntFunction<MemoryInfo> amount, MemoryInfo before,
                                            int maxBytes) {
        if (before == null) {
            throw new IllegalArgumentException("The earlier measurement must not be null");
        }
        isNotNull();
        int consumed = amount.applyAsInt(before) - amount.applyAsInt(actual);
        if (consumed > maxBytes) {
            failWithMessage("Expected at most %d bytes of %s to be consumed but %d were (%d available before, %d"
                    + " after)", maxBytes, type, consumed, amount.applyAsInt(before), amount.applyAsInt(actual));
        }
        return this;
    }
}
