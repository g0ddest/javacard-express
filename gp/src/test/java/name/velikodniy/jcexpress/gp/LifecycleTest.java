package name.velikodniy.jcexpress.gp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Lifecycle}.
 */
class LifecycleTest {

    @Test
    void describeSelectableShouldReturnSelectable() {
        String desc = Lifecycle.describe(0x07);
        assertThat(desc).contains("SELECTABLE");
        assertThat(desc).contains("07");
    }

    @Test
    void describeLockedShouldShowLockedBit() {
        // 0x8F = LOCKED | PERSONALIZED
        String desc = Lifecycle.describe(0x8F);
        assertThat(desc).contains("LOCKED");
        assertThat(desc).contains("PERSONALIZED");
        assertThat(desc).contains("8F");
    }

    @Test
    void describeTerminatedShouldReturnTerminated() {
        String desc = Lifecycle.describe(0xFF);
        assertThat(desc).contains("TERMINATED");
    }

    @Test
    void isLockedShouldDetectBit7() {
        assertThat(Lifecycle.isLocked(0x83)).isTrue();
        assertThat(Lifecycle.isLocked(0x87)).isTrue();
        assertThat(Lifecycle.isLocked(0x07)).isFalse();
        assertThat(Lifecycle.isLocked(0x0F)).isFalse();
    }

    @Test
    void cardStatesShouldHaveCorrectValues() {
        assertThat(Lifecycle.CARD_OP_READY).isEqualTo(0x01);
        assertThat(Lifecycle.CARD_INITIALIZED).isEqualTo(0x07);
        assertThat(Lifecycle.CARD_SECURED).isEqualTo(0x0F);
        assertThat(Lifecycle.CARD_LOCKED).isEqualTo(0x7F);
        assertThat(Lifecycle.CARD_TERMINATED).isEqualTo(0xFF);
    }

    @Test
    void scopeConstantsShouldMatchGPSpec() {
        assertThat(Lifecycle.SCOPE_ISD).isEqualTo(0x80);
        assertThat(Lifecycle.SCOPE_APPS).isEqualTo(0x40);
        assertThat(Lifecycle.SCOPE_LOAD_FILES).isEqualTo(0x20);
    }
}
