package name.velikodniy.jcexpress.converter.cap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BinaryWriter} -- the low-level binary serialization utility
 * used by all CAP component generators.
 */
class BinaryWriterTest {

    @Test
    void newWriter_shouldHaveSizeZero() {
        var writer = new BinaryWriter();
        assertThat(writer.size()).isZero();
        assertThat(writer.toByteArray()).isEmpty();
    }

    // ── u1 ──

    @Test
    void u1_shouldWriteSingleByte() {
        var writer = new BinaryWriter();
        writer.u1(0x42);

        assertThat(writer.size()).isEqualTo(1);
        assertThat(writer.toByteArray()).containsExactly(0x42);
    }

    @Test
    void u1_shouldMaskToLow8Bits() {
        var writer = new BinaryWriter();
        writer.u1(0x1FF); // only low 8 bits = 0xFF

        assertThat(writer.toByteArray()).containsExactly((byte) 0xFF);
    }

    @Test
    void u1_zero() {
        var writer = new BinaryWriter();
        writer.u1(0);

        assertThat(writer.toByteArray()).containsExactly(0x00);
    }

    @Test
    void u1_maxValue() {
        var writer = new BinaryWriter();
        writer.u1(0xFF);

        assertThat(writer.toByteArray()).containsExactly((byte) 0xFF);
    }

    // ── u2 ──

    @Test
    void u2_shouldWriteBigEndian() {
        var writer = new BinaryWriter();
        writer.u2(0x1234);

        assertThat(writer.size()).isEqualTo(2);
        assertThat(writer.toByteArray()).containsExactly(0x12, 0x34);
    }

    @Test
    void u2_zero() {
        var writer = new BinaryWriter();
        writer.u2(0);

        assertThat(writer.toByteArray()).containsExactly(0x00, 0x00);
    }

    @Test
    void u2_maxValue() {
        var writer = new BinaryWriter();
        writer.u2(0xFFFF);

        assertThat(writer.toByteArray()).containsExactly((byte) 0xFF, (byte) 0xFF);
    }

    @Test
    void u2_shouldMaskToLow16Bits() {
        var writer = new BinaryWriter();
        writer.u2(0x10000 | 0xABCD);

        assertThat(writer.toByteArray()).containsExactly((byte) 0xAB, (byte) 0xCD);
    }

    // ── u4 ──

    @Test
    void u4_shouldWriteBigEndian() {
        var writer = new BinaryWriter();
        writer.u4(0xDECAFFED);

        assertThat(writer.size()).isEqualTo(4);
        byte[] result = writer.toByteArray();
        assertThat(result[0]).isEqualTo((byte) 0xDE);
        assertThat(result[1]).isEqualTo((byte) 0xCA);
        assertThat(result[2]).isEqualTo((byte) 0xFF);
        assertThat(result[3]).isEqualTo((byte) 0xED);
    }

    @Test
    void u4_zero() {
        var writer = new BinaryWriter();
        writer.u4(0);

        assertThat(writer.toByteArray()).containsExactly(0x00, 0x00, 0x00, 0x00);
    }

    @Test
    void u4_negativeValue_shouldWriteAllBits() {
        var writer = new BinaryWriter();
        writer.u4(-1); // 0xFFFFFFFF

        assertThat(writer.toByteArray()).containsExactly(
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF);
    }

    // ── bytes ──

    @Test
    void bytes_shouldWriteRawData() {
        var writer = new BinaryWriter();
        writer.bytes(new byte[]{0x01, 0x02, 0x03});

        assertThat(writer.size()).isEqualTo(3);
        assertThat(writer.toByteArray()).containsExactly(0x01, 0x02, 0x03);
    }

    @Test
    void bytes_emptyArray() {
        var writer = new BinaryWriter();
        writer.bytes(new byte[0]);

        assertThat(writer.size()).isZero();
    }

    @Test
    void bytes_withOffsetAndLength() {
        var writer = new BinaryWriter();
        byte[] data = {0x10, 0x20, 0x30, 0x40, 0x50};
        writer.bytes(data, 1, 3); // bytes at index 1,2,3

        assertThat(writer.size()).isEqualTo(3);
        assertThat(writer.toByteArray()).containsExactly(0x20, 0x30, 0x40);
    }

    @Test
    void bytes_withOffsetAndLength_zeroLength() {
        var writer = new BinaryWriter();
        writer.bytes(new byte[]{1, 2, 3}, 0, 0);

        assertThat(writer.size()).isZero();
    }

    // ── aidWithLength ──

    @Test
    void aidWithLength_shouldPrefixLengthByte() {
        var writer = new BinaryWriter();
        byte[] aid = {(byte) 0xA0, 0x00, 0x00, 0x00, 0x62};
        writer.aidWithLength(aid);

        assertThat(writer.size()).isEqualTo(6); // 1 length + 5 AID bytes
        byte[] result = writer.toByteArray();
        assertThat(result[0]).isEqualTo((byte) 5);    // length
        assertThat(result[1]).isEqualTo((byte) 0xA0);  // first AID byte
    }

    @Test
    void aidWithLength_emptyAid() {
        var writer = new BinaryWriter();
        writer.aidWithLength(new byte[0]);

        assertThat(writer.size()).isEqualTo(1); // just the length byte (0)
        assertThat(writer.toByteArray()).containsExactly(0x00);
    }

    // ── Chaining ──

    @Test
    void methodsShouldSupportChaining() {
        var writer = new BinaryWriter();
        BinaryWriter result = writer.u1(1).u2(2).u4(3).bytes(new byte[]{4});

        assertThat(result).isSameAs(writer);
        assertThat(writer.size()).isEqualTo(1 + 2 + 4 + 1);
    }

    @Test
    void bytesWithOffset_shouldSupportChaining() {
        var writer = new BinaryWriter();
        BinaryWriter result = writer.bytes(new byte[]{1, 2, 3}, 0, 2);

        assertThat(result).isSameAs(writer);
    }

    @Test
    void aidWithLength_shouldSupportChaining() {
        var writer = new BinaryWriter();
        BinaryWriter result = writer.aidWithLength(new byte[]{1, 2, 3});

        assertThat(result).isSameAs(writer);
    }

    // ── Composite / Integration ──

    @Test
    void multipleWrites_shouldConcatenate() {
        var writer = new BinaryWriter();
        writer.u1(0x01);    // 1 byte
        writer.u2(0x0203);  // 2 bytes
        writer.u4(0x04050607); // 4 bytes

        assertThat(writer.size()).isEqualTo(7);
        assertThat(writer.toByteArray()).containsExactly(
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07);
    }

    @Test
    void toByteArray_shouldReturnCopy() {
        var writer = new BinaryWriter();
        writer.u1(42);

        byte[] first = writer.toByteArray();
        byte[] second = writer.toByteArray();

        assertThat(first).isEqualTo(second);
        // Modifying one should not affect the other
        first[0] = 0;
        assertThat(second[0]).isEqualTo((byte) 42);
    }
}
