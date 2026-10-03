package name.velikodniy.jcexpress.tlv;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * BER-TLV data objects as ISO/IEC 7816-4:2005 5.2.2 defines them: tag fields of one to three bytes
 * (5.2.2.1, Table 7), length fields of one to five bytes (5.2.2.2, Table 8), '00'/'FF' padding between data
 * objects.
 */
class BerTlvConformanceTest {

    @Nested
    class TagFields {

        /** "ISO/IEC 7816 supports tag fields of one, two and three bytes; longer tag fields are reserved". */
        @Test
        void fourByteTagIsRejectedInsteadOfMisparsed() {
            assertThatThrownBy(() -> TLVParser.parse("DF 81 82 03 01 02 03"))
                    .isInstanceOf(TLVException.class)
                    .hasMessageContaining("three bytes");
        }

        /** "Bits 7 to 1 of the first subsequent byte shall not be all set to 0". */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"9F 00 01 AA", "DF 80 01 01 AA"})
        void firstSubsequentByteWithoutTagNumberBitsIsRejected(String hex) {
            assertThatThrownBy(() -> TLVParser.parse(hex)).isInstanceOf(TLVException.class);
        }

        /** EMV uses two-byte tags with tag numbers below 31 (e.g. '9F02', '9F12'); they are read as written. */
        @Test
        void twoByteTagsWithSmallNumbersAreAccepted() {
            TLVList list = TLVParser.parse("9F 02 01 11 9F 12 01 22");

            assertThat(list.get(0).tag()).isEqualTo(0x9F02);
            assertThat(list.get(1).tag()).isEqualTo(0x9F12);
        }

        @Test
        void threeByteTagIsParsed() {
            TLV tlv = TLVParser.parse("DF 81 02 01 AA").get(0);

            assertThat(tlv.tag()).isEqualTo(0xDF8102);
            assertThat(tlv.value()).containsExactly(0xAA);
        }
    }

    @Nested
    class LengthFields {

        /** Table 8: '84' followed by four bytes is a valid length field (0 to 4 294 967 295). */
        @Test
        void fiveByteLengthFieldIsAccepted() {
            TLVList list = TLVParser.parse("C1 84 00 00 00 02 AA BB");

            assertThat(list.get(0).value()).containsExactly(0xAA, 0xBB);
        }

        @Test
        void fiveByteLengthBeyondTheDataIsATruncation() {
            assertThatThrownBy(() -> TLVParser.parse("C1 84 FF FF FF FF AA"))
                    .isInstanceOf(TLVException.class)
                    .hasMessageContaining("Truncated");
        }

        /** "the values '80' and '85' to 'FF' are invalid for the first byte of length fields". */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"C1 80 00 00", "C1 85 00 00 00 00 01 AA", "C1 FF 01"})
        void invalidFirstLengthBytesAreRejected(String hex) {
            assertThatThrownBy(() -> TLVParser.parse(hex)).isInstanceOf(TLVException.class);
        }
    }

    @Nested
    class Malformed {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"84", "84 05 01 02", "5F", "5F 2D", "DF 81", "84 81", "84 82 01", "6F 80 84 00 00 00",
                "6F 03 84 05 01"})
        void truncatedOrInvalidDataIsRejected(String hex) {
            assertThatThrownBy(() -> TLVParser.parse(hex)).isInstanceOf(TLVException.class);
        }

        @Test
        void zeroLengthAndPaddingAreAccepted() {
            TLVList list = TLVParser.parse("00 FF 84 00 FF 6F 00 00");

            assertThat(list.size()).isEqualTo(2);
            assertThat(list.get(0).length()).isZero();
            assertThat(list.get(1).isConstructed()).isTrue();
        }

        /** A card (or a hostile simulator) can return deeply nested templates in one extended response. */
        @Test
        void deepNestingIsATlvExceptionNotAStackOverflow() {
            byte[] data = nested(65_535);

            Throwable thrown = catchThrowable(() -> TLVParser.parse(data));

            assertThat(thrown).isInstanceOf(TLVException.class).hasMessageContaining("nest");
        }

        @Test
        void realisticNestingDepthIsParsed() {
            byte[] data = TLVBuilder.create().addConstructed(0x6F, a -> a
                    .addConstructed(0xA5, b -> b
                            .addConstructed(0xBF0C, c -> c
                                    .addConstructed(0x61, d -> d.add(0x4F, "A0000000031010"))))).build();

            assertThat(TLVParser.parse(data).findRecursive(0x4F)).isPresent();
        }

        private byte[] nested(int maxSize) {
            byte[] inner = new byte[0];
            while (true) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                out.write(0xE1);
                int len = inner.length;
                if (len < 0x80) {
                    out.write(len);
                } else if (len < 0x100) {
                    out.write(0x81);
                    out.write(len);
                } else {
                    out.write(0x82);
                    out.write(len >> 8);
                    out.write(len);
                }
                out.writeBytes(inner);
                if (out.size() > maxSize) {
                    return inner;
                }
                inner = out.toByteArray();
            }
        }
    }

    @Nested
    class Builder {

        /** Table 7: '00' is invalid; '0102' is not a BER tag (first byte '01' is a complete tag). */
        @ParameterizedTest(name = "tag {0}")
        @ValueSource(ints = {0x00, 0x1F, 0xFF, 0x0102, 0x9F00, 0x9F80, 0xDF80_01, 0xDF81_81, 0xFF21, 0x0100_0000, -1})
        void invalidTagsAreRejected(int tag) {
            assertThatThrownBy(() -> TLVBuilder.create().add(tag, new byte[]{1}))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> TLVBuilder.create().addConstructed(tag, b -> { }))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void validTagsOfOneTwoAndThreeBytesAreWritten() {
            byte[] data = TLVBuilder.create()
                    .add(0x84, "01")
                    .add(0x9F02, "02")
                    .add(0x5F2D, "03")
                    .add(0xDF8102, "04")
                    .build();

            assertThat(Hex.encodeSpaced(data)).isEqualTo("84 01 01 9F 02 01 02 5F 2D 01 03 DF 81 02 01 04");
        }

        /** Property: random valid structures round-trip through TLVBuilder and TLVParser. */
        @Test
        void randomStructuresRoundTrip() {
            Random random = new Random(78164);
            for (int iteration = 0; iteration < 2000; iteration++) {
                List<Node> top = new ArrayList<>();
                int count = 1 + random.nextInt(4);
                for (int i = 0; i < count; i++) {
                    top.add(Node.random(random, 0));
                }
                TLVBuilder builder = TLVBuilder.create();
                top.forEach(node -> node.build(builder));
                TLVList parsed = TLVParser.parse(builder.build());
                assertThat(parsed.size()).isEqualTo(top.size());
                for (int i = 0; i < top.size(); i++) {
                    top.get(i).check(parsed.get(i));
                }
            }
        }
    }

    /** Random BER-TLV tree used by the round-trip property. */
    private record Node(int tag, byte[] value, List<Node> children) {

        static Node random(Random random, int depth) {
            boolean constructed = depth < 4 && random.nextInt(3) == 0;
            int tag = randomTag(random, constructed);
            if (!constructed) {
                int length = switch (random.nextInt(4)) {
                    case 0 -> 0;
                    case 1 -> random.nextInt(128);
                    case 2 -> 128 + random.nextInt(128);
                    default -> 256 + random.nextInt(400);
                };
                byte[] value = new byte[length];
                random.nextBytes(value);
                return new Node(tag, value, List.of());
            }
            List<Node> children = new ArrayList<>();
            int count = random.nextInt(4);
            for (int i = 0; i < count; i++) {
                children.add(random(random, depth + 1));
            }
            return new Node(tag, null, children);
        }

        /** Tag numbers 0-30 (one byte), 31-127 (two bytes), 128-16383 (three bytes), any class. */
        static int randomTag(Random random, boolean constructed) {
            int first = (random.nextInt(4) << 6) | (constructed ? 0x20 : 0);
            return switch (random.nextInt(3)) {
                case 0 -> {
                    int tag = first | random.nextInt(31);
                    yield tag == 0x00 ? 0x01 : tag;
                }
                case 1 -> (longFirstByte(first) << 8) | (0x1F + random.nextInt(0x61));
                default -> (longFirstByte(first) << 16) | ((0x81 + random.nextInt(0x7F)) << 8) | random.nextInt(0x80);
            };
        }

        static int longFirstByte(int first) {
            int b0 = first | 0x1F;
            return b0 == 0xFF ? 0xBF : b0;
        }

        void build(TLVBuilder builder) {
            if (value != null) {
                builder.add(tag, value);
            } else {
                builder.addConstructed(tag, inner -> children.forEach(child -> child.build(inner)));
            }
        }

        void check(TLV tlv) {
            assertThat(tlv.tag()).isEqualTo(tag);
            if (value != null) {
                assertThat(tlv.isConstructed()).isFalse();
                assertThat(tlv.value()).isEqualTo(value);
                return;
            }
            assertThat(tlv.isConstructed()).isTrue();
            assertThat(tlv.children().size()).isEqualTo(children.size());
            for (int i = 0; i < children.size(); i++) {
                children.get(i).check(tlv.children().get(i));
            }
        }
    }
}
