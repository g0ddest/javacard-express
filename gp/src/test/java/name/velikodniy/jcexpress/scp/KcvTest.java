package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Key check values of {@link KeyInfo}: 3DES = first 3 bytes of 3DES-ECB(key, 8 x '00'), AES = first 3 bytes of
 * AES-ECB(key, 16 x '01'). Expected values are the public GlobalPlatformPro KCVs of the well-known test key
 * 40..4F (static DEK of TestPlaintextKeys.testSessionKeys_SCP01 / _SCP03, data only).
 */
class KcvTest {

    private static final byte[] TEST_KEY = Hex.decode("404142434445464748494A4B4C4D4E4F");

    @Test
    void tripleDesKeyCheckValueOfTheTestKey() {
        assertThat(Hex.encode(KeyInfo.kcvDes3(TEST_KEY))).isEqualTo("8BAF47");
    }

    @Test
    void aesKeyCheckValueOfTheTestKey() {
        assertThat(Hex.encode(KeyInfo.kcvAes(TEST_KEY))).isEqualTo("504A77");
    }

    @Test
    void kcvDispatchesByKeyType() {
        assertThat(KeyInfo.kcv(TEST_KEY, KeyInfo.KeyType.DES3)).isEqualTo(KeyInfo.kcvDes3(TEST_KEY));
        assertThat(KeyInfo.kcv(TEST_KEY, KeyInfo.KeyType.AES)).isEqualTo(KeyInfo.kcvAes(TEST_KEY));
        assertThat(KeyInfo.KeyType.DES3.code()).isEqualTo(0x80);
        assertThat(KeyInfo.KeyType.AES.code()).isEqualTo(0x88);
    }
}
