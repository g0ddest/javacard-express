package name.velikodniy.jcexpress.sm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SMKeys}.
 */
class SMKeysTest {

    @Test
    void constructorShouldCopyKeys() {
        byte[] enc = {0x01, 0x02, 0x03};
        byte[] mac = {0x04, 0x05, 0x06};
        SMKeys keys = new SMKeys(enc, mac);

        // Modify originals — should not affect keys
        enc[0] = (byte) 0xFF;
        mac[0] = (byte) 0xFF;

        assertThat(keys.encKey()[0]).isEqualTo((byte) 0x01);
        assertThat(keys.macKey()[0]).isEqualTo((byte) 0x04);
    }

    @Test
    void encKeyShouldReturnCopy() {
        SMKeys keys = new SMKeys(new byte[]{0x01}, new byte[]{0x02});
        byte[] k1 = keys.encKey();
        byte[] k2 = keys.encKey();

        // Should be equal but not same reference
        assertThat(k1).isEqualTo(k2);
        k1[0] = (byte) 0xFF;
        assertThat(keys.encKey()[0]).isEqualTo((byte) 0x01);
    }

    @Test
    void macKeyShouldReturnCopy() {
        SMKeys keys = new SMKeys(new byte[]{0x01}, new byte[]{0x02});
        byte[] k1 = keys.macKey();
        byte[] k2 = keys.macKey();

        assertThat(k1).isEqualTo(k2);
        k1[0] = (byte) 0xFF;
        assertThat(keys.macKey()[0]).isEqualTo((byte) 0x02);
    }

    @Test
    void nullKeyShouldThrow() {
        assertThatThrownBy(() -> new SMKeys(null, new byte[1]))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SMKeys(new byte[1], null))
                .isInstanceOf(NullPointerException.class);
    }
}
