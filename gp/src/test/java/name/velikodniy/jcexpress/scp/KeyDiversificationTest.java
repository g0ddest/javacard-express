package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link KeyDiversification}.
 */
class KeyDiversificationTest {

    private static final byte[] DEFAULT_KEY = Hex.decode("404142434445464748494A4B4C4D4E4F");
    private static final byte[] DIV_DATA = Hex.decode("00010203040506070809");

    // ── VISA2 ──

    @Test
    void visa2ShouldDeriveThreeDistinctKeys() {
        SCPKeys master = SCPKeys.fromMasterKey(DEFAULT_KEY);
        SCPKeys diversified = KeyDiversification.visa2(master, DIV_DATA);

        assertThat(diversified.enc()).hasSize(16);
        assertThat(diversified.mac()).hasSize(16);
        assertThat(diversified.dek()).hasSize(16);

        // All three should be different (different key index)
        assertThat(diversified.enc()).isNotEqualTo(diversified.mac());
        assertThat(diversified.mac()).isNotEqualTo(diversified.dek());
        assertThat(diversified.enc()).isNotEqualTo(diversified.dek());
    }

    @Test
    void visa2ShouldBeDeterministic() {
        SCPKeys master = SCPKeys.fromMasterKey(DEFAULT_KEY);
        SCPKeys d1 = KeyDiversification.visa2(master, DIV_DATA);
        SCPKeys d2 = KeyDiversification.visa2(master, DIV_DATA);

        assertThat(d1.enc()).isEqualTo(d2.enc());
        assertThat(d1.mac()).isEqualTo(d2.mac());
        assertThat(d1.dek()).isEqualTo(d2.dek());
    }

    @Test
    void visa2ShouldProduceDifferentKeysForDifferentDivData() {
        SCPKeys master = SCPKeys.fromMasterKey(DEFAULT_KEY);
        SCPKeys d1 = KeyDiversification.visa2(master, Hex.decode("00010203040506070809"));
        SCPKeys d2 = KeyDiversification.visa2(master, Hex.decode("09080706050403020100"));

        assertThat(d1.enc()).isNotEqualTo(d2.enc());
    }

    // ── EMV CPS 1.1 ──

    @Test
    void emvCps11ShouldDeriveThreeDistinctKeys() {
        SCPKeys master = SCPKeys.fromMasterKey(DEFAULT_KEY);
        SCPKeys diversified = KeyDiversification.emvCps11(master, DIV_DATA);

        assertThat(diversified.enc()).hasSize(16);
        assertThat(diversified.mac()).hasSize(16);
        assertThat(diversified.dek()).hasSize(16);

        assertThat(diversified.enc()).isNotEqualTo(diversified.mac());
        assertThat(diversified.mac()).isNotEqualTo(diversified.dek());
    }

    @Test
    void emvCps11ShouldDifferFromVisa2() {
        SCPKeys master = SCPKeys.fromMasterKey(DEFAULT_KEY);
        SCPKeys visa2 = KeyDiversification.visa2(master, DIV_DATA);
        SCPKeys emv = KeyDiversification.emvCps11(master, DIV_DATA);

        // Different byte positions used → different results
        assertThat(visa2.enc()).isNotEqualTo(emv.enc());
    }

    // ── KDF3 ──

    @Test
    void kdf3ShouldDeriveThreeDistinctKeys() {
        SCPKeys master = SCPKeys.fromMasterKey(DEFAULT_KEY);
        SCPKeys diversified = KeyDiversification.kdf3(master, DIV_DATA);

        assertThat(diversified.enc()).hasSize(16);
        assertThat(diversified.mac()).hasSize(16);
        assertThat(diversified.dek()).hasSize(16);

        assertThat(diversified.enc()).isNotEqualTo(diversified.mac());
        assertThat(diversified.mac()).isNotEqualTo(diversified.dek());
    }

    @Test
    void kdf3ShouldBeDeterministic() {
        SCPKeys master = SCPKeys.fromMasterKey(DEFAULT_KEY);
        SCPKeys d1 = KeyDiversification.kdf3(master, DIV_DATA);
        SCPKeys d2 = KeyDiversification.kdf3(master, DIV_DATA);

        assertThat(d1.enc()).isEqualTo(d2.enc());
        assertThat(d1.mac()).isEqualTo(d2.mac());
        assertThat(d1.dek()).isEqualTo(d2.dek());
    }

    // ── Validation ──

    @Test
    void shouldRejectNullDiversificationData() {
        SCPKeys master = SCPKeys.fromMasterKey(DEFAULT_KEY);

        assertThatThrownBy(() -> KeyDiversification.visa2(master, null))
                .isInstanceOf(SCPException.class);
        assertThatThrownBy(() -> KeyDiversification.emvCps11(master, null))
                .isInstanceOf(SCPException.class);
        assertThatThrownBy(() -> KeyDiversification.kdf3(master, null))
                .isInstanceOf(SCPException.class);
    }

    @Test
    void shouldRejectShortDiversificationData() {
        SCPKeys master = SCPKeys.fromMasterKey(DEFAULT_KEY);
        byte[] shortData = new byte[5]; // too short, need at least 10

        assertThatThrownBy(() -> KeyDiversification.visa2(master, shortData))
                .isInstanceOf(SCPException.class)
                .hasMessageContaining("10");
    }
}
