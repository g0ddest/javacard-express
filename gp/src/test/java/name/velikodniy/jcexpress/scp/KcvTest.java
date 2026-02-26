package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Key Check Value computation in {@link KeyInfo}.
 */
class KcvTest {

    @Test
    void shouldComputeKcvDes3() {
        byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
        byte[] kcv = KeyInfo.kcvDes3(key);
        assertThat(kcv).hasSize(3);
        assertThat(KeyInfo.kcvDes3(key)).isEqualTo(kcv);
    }

    @Test
    void shouldComputeKcvAes() {
        byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
        byte[] kcv = KeyInfo.kcvAes(key);
        assertThat(kcv).hasSize(3);
        assertThat(KeyInfo.kcvAes(key)).isEqualTo(kcv);
    }

    @Test
    void kcvDes3AndAesShouldDiffer() {
        byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
        assertThat(KeyInfo.kcvDes3(key)).isNotEqualTo(KeyInfo.kcvAes(key));
    }

    @Test
    void kcvShouldDispatchByType() {
        byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
        assertThat(KeyInfo.kcv(key, KeyInfo.KeyType.DES3)).isEqualTo(KeyInfo.kcvDes3(key));
        assertThat(KeyInfo.kcv(key, KeyInfo.KeyType.AES)).isEqualTo(KeyInfo.kcvAes(key));
    }
}
