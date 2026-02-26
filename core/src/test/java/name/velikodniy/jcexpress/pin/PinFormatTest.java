package name.velikodniy.jcexpress.pin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PinFormatTest {

    @Test
    void asciiShouldEncodeDigits() {
        byte[] encoded = PinFormat.ASCII.encode("1234");
        assertThat(encoded).containsExactly(0x31, 0x32, 0x33, 0x34);
    }

    @Test
    void asciiShouldEncodeSingleDigit() {
        byte[] encoded = PinFormat.ASCII.encode("0");
        assertThat(encoded).containsExactly(0x30);
    }

    @Test
    void bcdShouldEncodeEvenLength() {
        byte[] encoded = PinFormat.BCD.encode("1234");
        assertThat(encoded).containsExactly(0x12, 0x34);
    }

    @Test
    void bcdShouldPadOddLength() {
        byte[] encoded = PinFormat.BCD.encode("123");
        assertThat(encoded).containsExactly(0x12, 0x3F);
    }

    @Test
    void bcdShouldEncodeSingleDigit() {
        byte[] encoded = PinFormat.BCD.encode("5");
        assertThat(encoded).containsExactly(0x5F);
    }

    @Test
    void iso9564ShouldEncode4DigitPin() {
        byte[] encoded = PinFormat.ISO_9564_FORMAT_2.encode("1234");
        // 0x24 = format 2, length 4
        // 0x12 0x34 = BCD digits
        // rest = 0xFF padding
        assertThat(encoded).hasSize(8);
        assertThat(encoded[0]).isEqualTo((byte) 0x24);
        assertThat(encoded[1]).isEqualTo((byte) 0x12);
        assertThat(encoded[2]).isEqualTo((byte) 0x34);
        for (int i = 3; i < 8; i++) {
            assertThat(encoded[i]).isEqualTo((byte) 0xFF);
        }
    }

    @Test
    void iso9564ShouldEncode6DigitPin() {
        byte[] encoded = PinFormat.ISO_9564_FORMAT_2.encode("123456");
        assertThat(encoded).hasSize(8);
        assertThat(encoded[0]).isEqualTo((byte) 0x26);
        assertThat(encoded[1]).isEqualTo((byte) 0x12);
        assertThat(encoded[2]).isEqualTo((byte) 0x34);
        assertThat(encoded[3]).isEqualTo((byte) 0x56);
        for (int i = 4; i < 8; i++) {
            assertThat(encoded[i]).isEqualTo((byte) 0xFF);
        }
    }

    @Test
    void iso9564ShouldRejectTooShortPin() {
        assertThatThrownBy(() -> PinFormat.ISO_9564_FORMAT_2.encode("123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4-12");
    }

    @Test
    void shouldRejectNonDigits() {
        assertThatThrownBy(() -> PinFormat.ASCII.encode("12AB"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digits");
    }

    @Test
    void shouldRejectEmptyPin() {
        assertThatThrownBy(() -> PinFormat.ASCII.encode(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullPin() {
        assertThatThrownBy(() -> PinFormat.ASCII.encode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
