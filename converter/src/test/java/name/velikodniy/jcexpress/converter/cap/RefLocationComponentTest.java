package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.resolve.CpReference;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RefLocationComponent} (CAP component tag 9).
 * Validates delta encoding and the binary format of the Reference Location component.
 */
class RefLocationComponentTest {

    @Test
    void tagShouldBe9() {
        assertThat(9).isEqualTo(RefLocationComponent.TAG);
    }

    @Test
    void emptyReferences_shouldProduceMinimalComponent() {
        byte[] result = RefLocationComponent.generate(List.of());

        // tag(1) + size(2) + byte_index_count(2) + byte2_index_count(2) = 7
        assertThat(result).isNotEmpty();
        assertThat(result[0]).isEqualTo((byte) 9); // tag
        // byte_index_count = 0, byte2_index_count = 0
        // After header (tag=1, size=2), payload starts at index 3
        int byteIndexCount = ((result[3] & 0xFF) << 8) | (result[4] & 0xFF);
        int byte2IndexCount = ((result[5] & 0xFF) << 8) | (result[6] & 0xFF);
        assertThat(byteIndexCount).isZero();
        assertThat(byte2IndexCount).isZero();
    }

    @Test
    void singleByteRef_shouldProduceSingleDelta() {
        var ref = new CpReference(10, 5, 1); // 1-byte index at offset 10
        byte[] result = RefLocationComponent.generate(List.of(ref));

        assertThat(result[0]).isEqualTo((byte) 9); // tag
        // byte_index_count should be 1
        int byteIndexCount = ((result[3] & 0xFF) << 8) | (result[4] & 0xFF);
        assertThat(byteIndexCount).isEqualTo(1);
        // The delta should be 10 (first ref offset)
        assertThat(result[5] & 0xFF).isEqualTo(10);
    }

    @Test
    void singleShortRef_shouldProduceSingleDelta() {
        var ref = new CpReference(20, 3, 2); // 2-byte index at offset 20
        byte[] result = RefLocationComponent.generate(List.of(ref));

        // byte_index_count = 0
        int byteIndexCount = ((result[3] & 0xFF) << 8) | (result[4] & 0xFF);
        assertThat(byteIndexCount).isZero();

        // byte2_index_count = 1 (at offset 5-6)
        int byte2IndexCount = ((result[5] & 0xFF) << 8) | (result[6] & 0xFF);
        assertThat(byte2IndexCount).isEqualTo(1);
        // The delta should be 20
        assertThat(result[7] & 0xFF).isEqualTo(20);
    }

    @Test
    void multipleShortRefs_shouldDeltaEncode() {
        var refs = List.of(
                new CpReference(5, 1, 2),
                new CpReference(15, 2, 2),
                new CpReference(20, 3, 2)
        );
        byte[] result = RefLocationComponent.generate(refs);

        // byte_index_count = 0
        int byteIndexCount = ((result[3] & 0xFF) << 8) | (result[4] & 0xFF);
        assertThat(byteIndexCount).isZero();

        // byte2_index_count = 3
        int byte2IndexCount = ((result[5] & 0xFF) << 8) | (result[6] & 0xFF);
        assertThat(byte2IndexCount).isEqualTo(3);

        // Deltas: 5, 10 (15-5), 5 (20-15)
        assertThat(result[7] & 0xFF).isEqualTo(5);
        assertThat(result[8] & 0xFF).isEqualTo(10);
        assertThat(result[9] & 0xFF).isEqualTo(5);
    }

    @Test
    void mixedRefs_shouldSplitIntoByteAndShortCategories() {
        var refs = List.of(
                new CpReference(10, 1, 1),  // 1-byte
                new CpReference(30, 2, 2),  // 2-byte
                new CpReference(50, 3, 1),  // 1-byte
                new CpReference(70, 4, 2)   // 2-byte
        );
        byte[] result = RefLocationComponent.generate(refs);

        // 2 byte refs, 2 short refs
        int byteIndexCount = ((result[3] & 0xFF) << 8) | (result[4] & 0xFF);
        int offset = 5 + byteIndexCount;
        int byte2IndexCount = ((result[offset] & 0xFF) << 8) | (result[offset + 1] & 0xFF);

        assertThat(byteIndexCount).isEqualTo(2);
        assertThat(byte2IndexCount).isEqualTo(2);
    }

    @Test
    void unsortedRefs_shouldBeSortedInOutput() {
        // Provide refs out of order
        var refs = List.of(
                new CpReference(30, 2, 2),
                new CpReference(10, 1, 2),
                new CpReference(20, 3, 2)
        );
        byte[] result = RefLocationComponent.generate(refs);

        int byte2IndexCount = ((result[5] & 0xFF) << 8) | (result[6] & 0xFF);
        assertThat(byte2IndexCount).isEqualTo(3);

        // Deltas after sorting: 10, 10 (20-10), 10 (30-20)
        assertThat(result[7] & 0xFF).isEqualTo(10);
        assertThat(result[8] & 0xFF).isEqualTo(10);
        assertThat(result[9] & 0xFF).isEqualTo(10);
    }

    // ── deltaEncode tests (package-private method) ──

    @Test
    void deltaEncode_emptyList_shouldReturnEmptyArray() {
        byte[] result = RefLocationComponent.deltaEncode(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void deltaEncode_singleRef_shouldReturnOffset() {
        var ref = new CpReference(42, 1, 2);
        byte[] result = RefLocationComponent.deltaEncode(List.of(ref));

        assertThat(result).hasSize(1);
        assertThat(result[0] & 0xFF).isEqualTo(42);
    }

    @Test
    void deltaEncode_largeGap_shouldUseEscapeBytes() {
        // Gap of 300: should emit 0xFF (255), then 45 (300-255)
        var ref = new CpReference(300, 1, 2);
        byte[] result = RefLocationComponent.deltaEncode(List.of(ref));

        assertThat(result).hasSize(2);
        assertThat(result[0] & 0xFF).isEqualTo(255);
        assertThat(result[1] & 0xFF).isEqualTo(45);
    }

    @Test
    void deltaEncode_veryLargeGap_shouldUseMultipleEscapeBytes() {
        // Gap of 600: should emit 0xFF, 0xFF, 90 (600 - 255 - 255)
        var ref = new CpReference(600, 1, 2);
        byte[] result = RefLocationComponent.deltaEncode(List.of(ref));

        assertThat(result).hasSize(3);
        assertThat(result[0] & 0xFF).isEqualTo(255);
        assertThat(result[1] & 0xFF).isEqualTo(255);
        assertThat(result[2] & 0xFF).isEqualTo(90);
    }

    @Test
    void deltaEncode_exactlyOnEscapeBoundary() {
        // Gap of exactly 255: should emit 0xFF, 0
        var ref = new CpReference(255, 1, 2);
        byte[] result = RefLocationComponent.deltaEncode(List.of(ref));

        assertThat(result).hasSize(2);
        assertThat(result[0] & 0xFF).isEqualTo(255);
        assertThat(result[1] & 0xFF).isZero();
    }

    @Test
    void deltaEncode_254_shouldFitInSingleByte() {
        var ref = new CpReference(254, 1, 2);
        byte[] result = RefLocationComponent.deltaEncode(List.of(ref));

        assertThat(result).hasSize(1);
        assertThat(result[0] & 0xFF).isEqualTo(254);
    }

    @Test
    void deltaEncode_consecutiveRefs_shouldHaveZeroDeltas() {
        var refs = List.of(
                new CpReference(10, 1, 2),
                new CpReference(10, 2, 2)
        );
        byte[] result = RefLocationComponent.deltaEncode(refs);

        assertThat(result).hasSize(2);
        assertThat(result[0] & 0xFF).isEqualTo(10);
        assertThat(result[1] & 0xFF).isZero();
    }

    @Test
    void componentSize_shouldExcludeTagAndSizeHeader() {
        byte[] result = RefLocationComponent.generate(List.of());

        // Parse size from bytes[1..2]
        int size = ((result[1] & 0xFF) << 8) | (result[2] & 0xFF);
        // size should equal remaining bytes after tag(1) + size(2)
        assertThat(size).isEqualTo(result.length - 3);
    }
}
