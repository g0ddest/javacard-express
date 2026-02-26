package name.velikodniy.jcexpress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HexTest {

    @Test
    void shouldEncodeBytes() {
        assertThat(Hex.encode(new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}))
                .isEqualTo("48656C6C6F");
    }

    @Test
    void shouldEncodeEmptyArray() {
        assertThat(Hex.encode(new byte[0])).isEmpty();
    }

    @Test
    void shouldDecodeHexString() {
        assertThat(Hex.decode("48656C6C6F"))
                .containsExactly(0x48, 0x65, 0x6C, 0x6C, 0x6F);
    }

    @Test
    void shouldDecodeLowerCase() {
        assertThat(Hex.decode("abcdef"))
                .containsExactly(0xAB, 0xCD, 0xEF);
    }

    @Test
    void shouldIgnoreSpaces() {
        assertThat(Hex.decode("48 65 6C 6C 6F"))
                .containsExactly(0x48, 0x65, 0x6C, 0x6C, 0x6F);
    }

    @Test
    void shouldRejectOddLength() {
        assertThatThrownBy(() -> Hex.decode("ABC"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNull() {
        assertThatThrownBy(() -> Hex.encode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Hex.decode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
