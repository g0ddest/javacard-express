package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SCPKeys}.
 */
class SCPKeysTest {

    @Test
    void defaultKeysShouldUseGPTestKey() {
        SCPKeys keys = SCPKeys.defaultKeys();
        assertThat(Hex.encode(keys.enc())).isEqualTo("404142434445464748494A4B4C4D4E4F");
        assertThat(Hex.encode(keys.mac())).isEqualTo("404142434445464748494A4B4C4D4E4F");
        assertThat(Hex.encode(keys.dek())).isEqualTo("404142434445464748494A4B4C4D4E4F");
    }

    @Test
    void keyLengthShouldReturn16For128BitKeys() {
        SCPKeys keys = SCPKeys.defaultKeys();
        assertThat(keys.keyLength()).isEqualTo(16);
    }

    @Test
    void fromMasterKeyShouldSetAllThreeKeys() {
        byte[] master = Hex.decode("00112233445566778899AABBCCDDEEFF");
        SCPKeys keys = SCPKeys.fromMasterKey(master);
        assertThat(keys.enc()).isEqualTo(master);
        assertThat(keys.mac()).isEqualTo(master);
        assertThat(keys.dek()).isEqualTo(master);
    }

    @Test
    void ofShouldSetSeparateKeys() {
        byte[] enc = Hex.decode("00112233445566778899AABBCCDDEEFF");
        byte[] mac = Hex.decode("FFEEDDCCBBAA99887766554433221100");
        byte[] dek = Hex.decode("AABBCCDDEEFF00112233445566778899");
        SCPKeys keys = SCPKeys.of(enc, mac, dek);
        assertThat(keys.enc()).isEqualTo(enc);
        assertThat(keys.mac()).isEqualTo(mac);
        assertThat(keys.dek()).isEqualTo(dek);
    }

    @Test
    void shouldAccept32ByteKeys() {
        byte[] key32 = Hex.decode("00112233445566778899AABBCCDDEEFF00112233445566778899AABBCCDDEEFF");
        SCPKeys keys = SCPKeys.fromMasterKey(key32);
        assertThat(keys.keyLength()).isEqualTo(32);
    }

    @Test
    void shouldRejectInvalidKeyLength() {
        byte[] badKey = new byte[10];
        assertThatThrownBy(() -> SCPKeys.fromMasterKey(badKey))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullKey() {
        assertThatThrownBy(() -> SCPKeys.of(null, new byte[16], new byte[16]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ENC");
    }

    @Test
    void keysShouldBeDefensivelyCopied() {
        byte[] original = Hex.decode("00112233445566778899AABBCCDDEEFF");
        SCPKeys keys = SCPKeys.fromMasterKey(original);
        byte[] returned = keys.enc();
        returned[0] = (byte) 0xFF; // mutate returned copy
        assertThat(keys.enc()[0]).isEqualTo((byte) 0x00); // original unaffected
    }

    @Test
    void toStringShouldNotLeakKeys() {
        SCPKeys keys = SCPKeys.defaultKeys();
        String s = keys.toString();
        assertThat(s).contains("length=16");
        assertThat(s).doesNotContain("4041");
    }
}
