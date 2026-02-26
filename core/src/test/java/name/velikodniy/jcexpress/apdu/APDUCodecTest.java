package name.velikodniy.jcexpress.apdu;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link APDUCodec}.
 */
class APDUCodecTest {

    // ── Encode short format (backward compatibility) ──

    @Nested
    class EncodeShort {

        @Test
        void case1NoDataNoLe() {
            byte[] apdu = APDUCodec.encode(0x00, 0xA4, 0x04, 0x00, null, -1);

            assertThat(Hex.encode(apdu)).isEqualTo("00A40400");
        }

        @Test
        void case2WithLe() {
            byte[] apdu = APDUCodec.encode(0x00, 0xCA, 0x00, 0xCF, null, 0);

            // Le=0 → 256, encoded as 0x00
            assertThat(Hex.encode(apdu)).isEqualTo("00CA00CF00");
        }

        @Test
        void case2WithExplicitLe() {
            byte[] apdu = APDUCodec.encode(0x80, 0x50, 0x00, 0x00, null, 8);

            assertThat(Hex.encode(apdu)).isEqualTo("8050000008");
        }

        @Test
        void case3WithData() {
            byte[] data = Hex.decode("A0000000031010");
            byte[] apdu = APDUCodec.encode(0x00, 0xA4, 0x04, 0x00, data, -1);

            assertThat(Hex.encode(apdu)).isEqualTo("00A4040007A0000000031010");
        }

        @Test
        void case4WithDataAndLe() {
            byte[] data = Hex.decode("A0000000031010");
            byte[] apdu = APDUCodec.encode(0x00, 0xA4, 0x04, 0x00, data, 0);

            assertThat(Hex.encode(apdu)).isEqualTo("00A4040007A000000003101000");
        }

        @Test
        void le256EncodedAsZero() {
            byte[] apdu = APDUCodec.encode(0x00, 0xC0, 0x00, 0x00, null, 256);

            assertThat(Hex.encode(apdu)).isEqualTo("00C0000000");
        }

        @Test
        void emptyDataTreatedAsNoData() {
            byte[] apdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, new byte[0], -1);

            assertThat(Hex.encode(apdu)).isEqualTo("80010000");
        }
    }

    // ── Encode extended format ──

    @Nested
    class EncodeExtended {

        @Test
        void case2ExtendedWithLargeLe() {
            // Le=1000 → extended Case 2E: CLA INS P1 P2 0x00 Le_hi Le_lo
            byte[] apdu = APDUCodec.encode(0x00, 0xCA, 0x00, 0xCF, null, 1000);

            assertThat(apdu.length).isEqualTo(7);
            assertThat(apdu[4] & 0xFF).isEqualTo(0x00); // extended marker
            assertThat(((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF)).isEqualTo(1000);
        }

        @Test
        void case3ExtendedWithLargeData() {
            // 300 bytes data → extended Case 3E
            byte[] data = new byte[300];
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i & 0xFF);

            byte[] apdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, data, -1);

            // Header(4) + marker(1) + Lc(2) + data(300) = 307
            assertThat(apdu.length).isEqualTo(307);
            assertThat(apdu[4] & 0xFF).isEqualTo(0x00); // extended marker
            int lc = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
            assertThat(lc).isEqualTo(300);
        }

        @Test
        void case4ExtendedWithLargeDataAndLe() {
            byte[] data = new byte[300];
            byte[] apdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, data, 1000);

            // Header(4) + marker(1) + Lc(2) + data(300) + Le(2) = 309
            assertThat(apdu.length).isEqualTo(309);
            assertThat(apdu[4] & 0xFF).isEqualTo(0x00);
            int lc = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
            assertThat(lc).isEqualTo(300);
            int le = ((apdu[307] & 0xFF) << 8) | (apdu[308] & 0xFF);
            assertThat(le).isEqualTo(1000);
        }

        @Test
        void le65536EncodedAsZeroZero() {
            byte[] apdu = APDUCodec.encode(0x00, 0xC0, 0x00, 0x00, null, 65536);

            assertThat(apdu.length).isEqualTo(7);
            assertThat(apdu[5] & 0xFF).isEqualTo(0x00);
            assertThat(apdu[6] & 0xFF).isEqualTo(0x00);
        }

        @Test
        void data256ForcesExtendedFormat() {
            byte[] data = new byte[256];
            byte[] apdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, data, -1);

            // Header(4) + marker(1) + Lc(2) + data(256) = 263
            assertThat(apdu.length).isEqualTo(263);
            assertThat(apdu[4] & 0xFF).isEqualTo(0x00); // extended marker
            int lc = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
            assertThat(lc).isEqualTo(256);
        }

        @Test
        void le257ForcesExtendedFormat() {
            byte[] apdu = APDUCodec.encode(0x00, 0xCA, 0x00, 0xCF, null, 257);

            assertThat(apdu.length).isEqualTo(7); // extended Case 2E
            int le = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
            assertThat(le).isEqualTo(257);
        }

        @Test
        void smallDataWithLargeLeForcesExtended() {
            // 10 bytes data + Le=1000 → extended
            byte[] data = new byte[10];
            byte[] apdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, data, 1000);

            // Header(4) + marker(1) + Lc(2) + data(10) + Le(2) = 19
            assertThat(apdu.length).isEqualTo(19);
            assertThat(apdu[4] & 0xFF).isEqualTo(0x00);
        }
    }

    // ── isExtended ──

    @Nested
    class IsExtended {

        @Test
        void shortApduNotExtended() {
            assertThat(APDUCodec.isExtended(Hex.decode("00A40400"))).isFalse();
            assertThat(APDUCodec.isExtended(Hex.decode("00CA00CF00"))).isFalse();
        }

        @Test
        void extendedCase2Detected() {
            byte[] apdu = APDUCodec.encode(0x00, 0xCA, 0x00, 0xCF, null, 1000);
            assertThat(APDUCodec.isExtended(apdu)).isTrue();
        }

        @Test
        void extendedCase3Detected() {
            byte[] apdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, new byte[300], -1);
            assertThat(APDUCodec.isExtended(apdu)).isTrue();
        }

        @Test
        void nullAndShortArrayNotExtended() {
            assertThat(APDUCodec.isExtended(null)).isFalse();
            assertThat(APDUCodec.isExtended(new byte[4])).isFalse();
            assertThat(APDUCodec.isExtended(new byte[6])).isFalse();
        }
    }

    // ── correctLe ──

    @Nested
    class CorrectLe {

        @Test
        void correctLeShortCase1AppendLe() {
            byte[] apdu = Hex.decode("00A40400"); // Case 1
            byte[] corrected = APDUCodec.correctLe(apdu, 128);

            assertThat(Hex.encode(corrected)).isEqualTo("00A4040080");
        }

        @Test
        void correctLeShortCase2ReplaceLe() {
            byte[] apdu = Hex.decode("00CA00CF00"); // Case 2, Le=0 (256)
            byte[] corrected = APDUCodec.correctLe(apdu, 64);

            assertThat(Hex.encode(corrected)).isEqualTo("00CA00CF40");
        }

        @Test
        void correctLeShortCase4ReplaceLast() {
            byte[] apdu = Hex.decode("00A4040007A000000003101000"); // Case 4
            byte[] corrected = APDUCodec.correctLe(apdu, 32);

            assertThat(corrected[corrected.length - 1] & 0xFF).isEqualTo(32);
        }

        @Test
        void correctLeShortCase3AppendLe() {
            byte[] apdu = Hex.decode("00A4040007A0000000031010"); // Case 3
            byte[] corrected = APDUCodec.correctLe(apdu, 16);

            assertThat(corrected.length).isEqualTo(apdu.length + 1);
            assertThat(corrected[corrected.length - 1] & 0xFF).isEqualTo(16);
        }

        @Test
        void correctLeExtendedCase2Replace() {
            byte[] apdu = APDUCodec.encode(0x00, 0xCA, 0x00, 0xCF, null, 1000);
            byte[] corrected = APDUCodec.correctLe(apdu, 200);

            assertThat(corrected.length).isEqualTo(7);
            int le = ((corrected[5] & 0xFF) << 8) | (corrected[6] & 0xFF);
            assertThat(le).isEqualTo(200);
        }

        @Test
        void correctLeExtendedCase4Replace() {
            byte[] apdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, new byte[300], 1000);
            byte[] corrected = APDUCodec.correctLe(apdu, 128);

            int le = ((corrected[corrected.length - 2] & 0xFF) << 8)
                    | (corrected[corrected.length - 1] & 0xFF);
            assertThat(le).isEqualTo(128);
        }

        @Test
        void correctLeExtendedCase3AppendLe() {
            byte[] apdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, new byte[300], -1);
            byte[] corrected = APDUCodec.correctLe(apdu, 64);

            assertThat(corrected.length).isEqualTo(apdu.length + 2);
            int le = ((corrected[corrected.length - 2] & 0xFF) << 8)
                    | (corrected[corrected.length - 1] & 0xFF);
            assertThat(le).isEqualTo(64);
        }
    }
}
