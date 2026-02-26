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

    @Test
    void chainingAssertionsShouldWork() {
        assertThat(info)
                .persistentAtLeast(5000)
                .persistentBelow(20000)
                .transientDeselectAtLeast(100)
                .transientResetAtLeast(100);
    }
}
