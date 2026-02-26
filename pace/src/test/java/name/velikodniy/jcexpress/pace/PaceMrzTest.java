package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.sm.SMKeys;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PaceMrz}.
 */
class PaceMrzTest {

    @Test
    void checkDigitShouldComputeCorrectly() {
        // ICAO 9303 example: document number "L898902C<" → check digit 3
        assertThat(PaceMrz.checkDigit("L898902C<")).isEqualTo(3);
        // Date of birth "690806" → check digit 1
        assertThat(PaceMrz.checkDigit("690806")).isEqualTo(1);
        // Date of expiry "940623" → check digit 6
        assertThat(PaceMrz.checkDigit("940623")).isEqualTo(6);
    }

    @Test
    void checkDigitForAllDigitsShouldWork() {
        assertThat(PaceMrz.checkDigit("520727")).isEqualTo(3);
    }

    @Test
    void checkDigitForLettersShouldWork() {
        // A=10, B=11, ..., Z=35
        // 'A' * 7 = 70, mod 10 = 0
        assertThat(PaceMrz.checkDigit("A")).isZero();
        // 'B' * 7 = 77, mod 10 = 7
        assertThat(PaceMrz.checkDigit("B")).isEqualTo(7);
    }

    @Test
    void checkDigitForFillerShouldBeZero() {
        // '<' maps to 0
        assertThat(PaceMrz.checkDigit("<")).isZero();
        assertThat(PaceMrz.checkDigit("<<<")).isZero();
    }

    @Test
    void computeKSeedShouldProduceCorrectLength() {
        byte[] kSeed = PaceMrz.computeKSeed("L898902C<", "690806", "940623");
        // SHA-1 output is 20 bytes
        assertThat(kSeed).hasSize(20);
    }

    @Test
    void computeKSeedShouldBeDeterministic() {
        byte[] k1 = PaceMrz.computeKSeed("L898902C<", "690806", "940623");
        byte[] k2 = PaceMrz.computeKSeed("L898902C<", "690806", "940623");
        assertThat(k1).isEqualTo(k2);
    }

    @Test
    void kdfShouldDerive16ByteKey() {
        byte[] kSeed = PaceMrz.computeKSeed("L898902C<", "690806", "940623");
        byte[] key = PaceMrz.kdf(kSeed, 1, 16);
        assertThat(key).hasSize(16);
    }

    @Test
    void kdfDifferentCountersShouldProduceDifferentKeys() {
        byte[] kSeed = PaceMrz.computeKSeed("L898902C<", "690806", "940623");
        byte[] k1 = PaceMrz.kdf(kSeed, 1, 16);
        byte[] k2 = PaceMrz.kdf(kSeed, 2, 16);
        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    void deriveKeysShouldReturn128BitKeys() {
        byte[] kSeed = PaceMrz.computeKSeed("L898902C<", "690806", "940623");
        SMKeys keys = PaceMrz.deriveKeys(kSeed, 16);
        assertThat(keys.encKey()).hasSize(16);
        assertThat(keys.macKey()).hasSize(16);
        assertThat(keys.encKey()).isNotEqualTo(keys.macKey());
    }

    @Test
    void deriveKeys256ShouldUseSha256() {
        byte[] kSeed = PaceMrz.computeKSeed("L898902C<", "690806", "940623");
        SMKeys keys = PaceMrz.deriveKeys(kSeed, 32);
        assertThat(keys.encKey()).hasSize(32);
        assertThat(keys.macKey()).hasSize(32);
    }

    @Test
    void emptyDocNumberShouldThrow() {
        assertThatThrownBy(() -> PaceMrz.computeKSeed("", "690806", "940623"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullDocNumberShouldThrow() {
        assertThatThrownBy(() -> PaceMrz.computeKSeed(null, "690806", "940623"))
                .isInstanceOf(NullPointerException.class);
    }
}
