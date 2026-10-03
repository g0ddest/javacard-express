package name.velikodniy.jcexpress.memory;

import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link name.velikodniy.jcexpress.assertions.MemoryInfoAssert}.
 */
class MemoryInfoAssertTest {

    private final MemoryInfo info = new MemoryInfo(10000, 500, 300);

    @Test
    void persistentBelowShouldPassWhenBelow() {
        assertThat(info).persistentBelow(20000);
    }

    @Test
    void persistentBelowShouldFailWhenAbove() {
        assertThatThrownBy(() -> assertThat(info).persistentBelow(5000))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("10000");
    }

    @Test
    void persistentAtLeastShouldPassWhenEnough() {
        assertThat(info).persistentAtLeast(5000);
    }

    @Test
    void persistentAtLeastShouldFailWhenNotEnough() {
        assertThatThrownBy(() -> assertThat(info).persistentAtLeast(20000))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("20000");
    }

    @Test
    void transientDeselectBelowShouldPass() {
        assertThat(info).transientDeselectBelow(1000);
    }

    @Test
    void transientDeselectAtLeastShouldPass() {
        assertThat(info).transientDeselectAtLeast(200);
    }

    @Test
    void transientResetBelowShouldPass() {
        assertThat(info).transientResetBelow(1000);
    }

    @Test
    void transientResetAtLeastShouldPass() {
        assertThat(info).transientResetAtLeast(100);
    }

    /**
     * Consumption is the difference between two measurements: memory available before minus memory
     * available after (an applet that consumed a lot leaves little available, so "available below" is not
     * a consumption check).
     */
    @Test
    void consumedAtMostComparesTwoMeasurements() {
        MemoryInfo before = new MemoryInfo(50_000, 1000, 800);
        MemoryInfo after = new MemoryInfo(46_000, 900, 800);

        assertThat(after)
                .persistentConsumedAtMost(before, 4000)
                .transientDeselectConsumedAtMost(before, 100)
                .transientResetConsumedAtMost(before, 0);
        assertThatThrownBy(() -> assertThat(after).persistentConsumedAtMost(before, 3999))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("4000");
        assertThatThrownBy(() -> assertThat(after).transientDeselectConsumedAtMost(before, 99))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void consumedAtMostNeedsTheEarlierMeasurement() {
        assertThatThrownBy(() -> assertThat(info).persistentConsumedAtMost(null, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chainingAssertionsShouldWork() {
        assertThat(info)
                .persistentAtLeast(5000)
                .persistentBelow(20000)
                .transientDeselectAtLeast(100)
                .transientResetAtLeast(100);
    }
}
