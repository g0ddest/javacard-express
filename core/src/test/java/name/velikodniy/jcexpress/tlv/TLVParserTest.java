package name.velikodniy.jcexpress.tlv;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TLVParserTest {

    @Test
    void shouldParseSinglePrimitive() {
        // Tag 84, length 7, value A0000000031010
        TLVList list = TLVParser.parse("84 07 A0000000031010");
        assertThat(list.size()).isEqualTo(1);
        TLV tlv = list.get(0);
        assertThat(tlv.tag()).isEqualTo(0x84);
        assertThat(tlv.length()).isEqualTo(7);
        assertThat(tlv.valueHex()).isEqualTo("A0000000031010");
        assertThat(tlv.isConstructed()).isFalse();
    }

    @Test
    void shouldParseMultiplePrimitives() {
        // Two primitives: tag 84 (7 bytes) + tag 50 (4 bytes)
        TLVList list = TLVParser.parse("84 07 A0000000031010 50 04 56495341");
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).tag()).isEqualTo(0x84);
        assertThat(list.get(1).tag()).isEqualTo(0x50);
        assertThat(list.get(1).valueHex()).isEqualTo("56495341"); // "VISA"
    }

    @Test
    void shouldParseConstructedTLV() {
        // 6F (FCI Template) containing 84 (DF Name)
        TLVList list = TLVParser.parse("6F 09 84 07 A0000000031010");
        assertThat(list.size()).isEqualTo(1);
        TLV fci = list.get(0);
        assertThat(fci.tag()).isEqualTo(0x6F);
        assertThat(fci.isConstructed()).isTrue();
        assertThat(fci.children().size()).isEqualTo(1);
        assertThat(fci.children().get(0).tag()).isEqualTo(0x84);
    }

    @Test
    void shouldParseMultiByteTag() {
        // Tag 9F38 (PDOL), length 3, value
        TLVList list = TLVParser.parse("9F38 03 9F3501");
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).tag()).isEqualTo(0x9F38);
        assertThat(list.get(0).length()).isEqualTo(3);
    }

    @Test
    void shouldSkipPaddingBytes() {
        // Padding (00, FF) before and after tag
        TLVList list = TLVParser.parse("00 FF 84 02 AABB 00");
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).tag()).isEqualTo(0x84);
    }

    @Test
    void shouldParseEmptyValue() {
        TLVList list = TLVParser.parse("84 00");
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).length()).isZero();
    }

    @Test
    void shouldParseTwoByteLength() {
        // Tag 84, length 0x81 0x80 = 128 bytes
        byte[] data = new byte[2 + 2 + 128]; // tag + length + value
        data[0] = (byte) 0x84;
        data[1] = (byte) 0x81;
        data[2] = (byte) 0x80;
        // value = 128 zero bytes
        TLVList list = TLVParser.parse(data);
        assertThat(list.get(0).length()).isEqualTo(128);
    }

    @Test
    void shouldParseFromByteArray() {
        byte[] raw = {(byte) 0x84, 0x02, (byte) 0xAA, (byte) 0xBB};
        TLVList list = TLVParser.parse(raw);
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).valueHex()).isEqualTo("AABB");
    }

    @Test
    void shouldParseEmptyInput() {
        TLVList list = TLVParser.parse(new byte[0]);
        assertThat(list.size()).isZero();
    }

    @Test
    void shouldThrowOnTruncatedLength() {
        assertThatThrownBy(() -> TLVParser.parse("84"))
                .isInstanceOf(TLVException.class)
                .hasMessageContaining("Truncated");
    }

    @Test
    void shouldThrowOnTruncatedValue() {
        assertThatThrownBy(() -> TLVParser.parse("84 05 AABB"))
                .isInstanceOf(TLVException.class)
                .hasMessageContaining("Truncated");
    }
}
