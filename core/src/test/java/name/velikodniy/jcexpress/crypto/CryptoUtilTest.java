package name.velikodniy.jcexpress.crypto;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CryptoUtil}.
 */
class CryptoUtilTest {

    // ── Padding ──

    @Nested
    class Pad80 {

        @Test
        void shouldPadEmptyDataTo8Bytes() {
            byte[] padded = CryptoUtil.pad80(new byte[0], 8);
            assertThat(Hex.encode(padded)).isEqualTo("8000000000000000");
        }

        @Test
        void shouldPad7ByteDataTo8Bytes() {
            byte[] data = Hex.decode("01020304050607");
            byte[] padded = CryptoUtil.pad80(data, 8);
            assertThat(Hex.encode(padded)).isEqualTo("0102030405060780");
        }

        @Test
        void shouldPad8ByteDataTo16Bytes() {
            byte[] data = Hex.decode("0102030405060708");
            byte[] padded = CryptoUtil.pad80(data, 8);
            assertThat(Hex.encode(padded)).isEqualTo("01020304050607088000000000000000");
        }

        @Test
        void shouldPadTo16ByteBlockForAES() {
            byte[] data = Hex.decode("0102030405");
            byte[] padded = CryptoUtil.pad80(data, 16);
            assertThat(padded).hasSize(16);
            assertThat(Hex.encode(padded)).isEqualTo("01020304058000000000000000000000");
        }

        @Test
        void shouldPad16ByteDataTo32Bytes() {
            byte[] data = new byte[16];
            byte[] padded = CryptoUtil.pad80(data, 16);
            assertThat(padded).hasSize(32);
            assertThat(padded[16]).isEqualTo((byte) 0x80);
        }
    }

    // ── 3DES ──

    @Nested
    class DES3 {

        @Test
        void shouldEncryptWithZeroIV() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] data = new byte[8]; // 8 zero bytes
            byte[] encrypted = CryptoUtil.des3CbcEncrypt(key, data);
            assertThat(encrypted).hasSize(8);
            // Result should be deterministic
            byte[] encrypted2 = CryptoUtil.des3CbcEncrypt(key, data);
            assertThat(encrypted).isEqualTo(encrypted2);
        }

        @Test
        void shouldEncryptWithCustomIV() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] data = new byte[8];
            byte[] iv = Hex.decode("0102030405060708");
            byte[] encrypted = CryptoUtil.des3CbcEncrypt(key, data, iv);
            assertThat(encrypted).hasSize(8);
            // Should differ from zero IV
            byte[] encryptedZeroIv = CryptoUtil.des3CbcEncrypt(key, data);
            assertThat(encrypted).isNotEqualTo(encryptedZeroIv);
        }

        @Test
        void des3MacShouldReturnLast8Bytes() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] data = new byte[16]; // two blocks
            byte[] mac = CryptoUtil.des3Mac(key, data, new byte[8]);
            assertThat(mac).hasSize(8);
        }

        @Test
        void retailMacShouldWork() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] data = CryptoUtil.pad80(new byte[10], 8);
            byte[] mac = CryptoUtil.retailMac(key, data, new byte[8]);
            assertThat(mac).hasSize(8);
        }

        @Test
        void retailMacSingleBlockShouldWork() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] data = new byte[8]; // exactly one block
            byte[] mac = CryptoUtil.retailMac(key, data, new byte[8]);
            assertThat(mac).hasSize(8);
        }
    }

    // ── 3DES CBC Decrypt ──

    @Nested
    class Des3CbcDecrypt {

        @Test
        void shouldReverseEncrypt() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] original = Hex.decode("0102030405060708");
            byte[] encrypted = CryptoUtil.des3CbcEncrypt(key, original);
            byte[] decrypted = CryptoUtil.des3CbcDecrypt(key, encrypted);
            assertThat(decrypted).isEqualTo(original);
        }

        @Test
        void shouldReverseEncryptWithIv() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] iv = Hex.decode("AABBCCDD11223344");
            byte[] original = Hex.decode("01020304050607080102030405060708");
            byte[] encrypted = CryptoUtil.des3CbcEncrypt(key, original, iv);
            byte[] decrypted = CryptoUtil.des3CbcDecrypt(key, encrypted, iv);
            assertThat(decrypted).isEqualTo(original);
        }
    }

    // ── 3DES ECB ──

    @Nested
    class DES3Ecb {

        @Test
        void shouldEncryptEcb() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] data = new byte[8]; // 8 zero bytes
            byte[] encrypted = CryptoUtil.des3EcbEncrypt(key, data);
            assertThat(encrypted).hasSize(8);
            // Should be deterministic
            assertThat(CryptoUtil.des3EcbEncrypt(key, data)).isEqualTo(encrypted);
        }

        @Test
        void shouldDifferFromCbc() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] data = new byte[16]; // 16 zero bytes
            byte[] ecb = CryptoUtil.des3EcbEncrypt(key, data);
            byte[] cbc = CryptoUtil.des3CbcEncrypt(key, data);
            // CBC with zero IV and zero data: first block should be same, second differs
            // (because CBC chains, ECB doesn't)
            // Both blocks of ECB should be identical (same input, no chaining)
            assertThat(ecb).hasSize(16);
            byte[] ecbBlock1 = java.util.Arrays.copyOfRange(ecb, 0, 8);
            byte[] ecbBlock2 = java.util.Arrays.copyOfRange(ecb, 8, 16);
            assertThat(ecbBlock1).isEqualTo(ecbBlock2); // ECB: same plaintext → same ciphertext
        }
    }

    // ── AES ──

    @Nested
    class AES {

        @Test
        void shouldEncryptWithZeroIV() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] data = new byte[16];
            byte[] encrypted = CryptoUtil.aesCbcEncrypt(key, data);
            assertThat(encrypted).hasSize(16);
        }

        @Test
        void shouldEncryptWithCustomIV() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] data = new byte[16];
            byte[] iv = new byte[16];
            iv[0] = 0x01;
            byte[] encrypted = CryptoUtil.aesCbcEncrypt(key, data, iv);
            byte[] encryptedZero = CryptoUtil.aesCbcEncrypt(key, data);
            assertThat(encrypted).isNotEqualTo(encryptedZero);
        }

        @Test
        void aesCmacShouldProduceDeterministicOutput() {
            byte[] key = Hex.decode("2B7E151628AED2A6ABF7158809CF4F3C");
            byte[] data = new byte[0];
            byte[] cmac = CryptoUtil.aesCmac(key, data);
            assertThat(cmac).hasSize(16);
            // Should be deterministic
            byte[] cmac2 = CryptoUtil.aesCmac(key, data);
            assertThat(cmac).isEqualTo(cmac2);
        }

        @Test
        void aesCmacShouldHandleVariousDataLengths() {
            byte[] key = Hex.decode("2B7E151628AED2A6ABF7158809CF4F3C");

            // Empty
            byte[] mac0 = CryptoUtil.aesCmac(key, new byte[0]);
            assertThat(mac0).hasSize(16);

            // Partial block
            byte[] mac5 = CryptoUtil.aesCmac(key, new byte[5]);
            assertThat(mac5).hasSize(16);

            // Full block
            byte[] mac16 = CryptoUtil.aesCmac(key, new byte[16]);
            assertThat(mac16).hasSize(16);

            // Multiple blocks
            byte[] mac32 = CryptoUtil.aesCmac(key, new byte[32]);
            assertThat(mac32).hasSize(16);

            // All should be different
            assertThat(mac0).isNotEqualTo(mac5);
            assertThat(mac5).isNotEqualTo(mac16);
            assertThat(mac16).isNotEqualTo(mac32);
        }

        /**
         * RFC 4493 test vector: AES-CMAC with empty message.
         * Key:    2B7E1516 28AED2A6 ABF71588 09CF4F3C
         * M:      (empty)
         * CMAC:   BB1D6929 E9593728 7FA37D12 9B756746
         */
        @Test
        void aesCmacShouldMatchRfc4493EmptyMessage() {
            byte[] key = Hex.decode("2B7E151628AED2A6ABF7158809CF4F3C");
            byte[] cmac = CryptoUtil.aesCmac(key, new byte[0]);
            assertThat(Hex.encode(cmac)).isEqualTo("BB1D6929E95937287FA37D129B756746");
        }

        /**
         * RFC 4493 test vector: AES-CMAC with 16-byte message.
         * Key:    2B7E1516 28AED2A6 ABF71588 09CF4F3C
         * M:      6BC1BEE2 2E409F96 E93D7E11 7393172A
         * CMAC:   070A16B4 6B4D4144 F79BDD9D D04A287C
         */
        @Test
        void aesCmacShouldMatchRfc4493FullBlock() {
            byte[] key = Hex.decode("2B7E151628AED2A6ABF7158809CF4F3C");
            byte[] msg = Hex.decode("6BC1BEE22E409F96E93D7E117393172A");
            byte[] cmac = CryptoUtil.aesCmac(key, msg);
            assertThat(Hex.encode(cmac)).isEqualTo("070A16B46B4D4144F79BDD9DD04A287C");
        }

        /**
         * RFC 4493 test vector: AES-CMAC with 64-byte message.
         * Key:    2B7E1516 28AED2A6 ABF71588 09CF4F3C
         * M:      6BC1BEE2 2E409F96 E93D7E11 7393172A
         *         AE2D8A57 1E03AC9C 9EB76FAC 45AF8E51
         *         30C81C46 A35CE411 E5FBC119 1A0A52EF
         *         F69F2445 DF4F9B17 AD2B417B E66C3710
         * CMAC:   51F0BEBF 7E3B9D92 FC497417 79363CFE
         */
        @Test
        void aesCmacShouldMatchRfc449364Bytes() {
            byte[] key = Hex.decode("2B7E151628AED2A6ABF7158809CF4F3C");
            byte[] msg = Hex.decode(
                    "6BC1BEE22E409F96E93D7E117393172A"
                            + "AE2D8A571E03AC9C9EB76FAC45AF8E51"
                            + "30C81C46A35CE411E5FBC1191A0A52EF"
                            + "F69F2445DF4F9B17AD2B417BE66C3710");
            byte[] cmac = CryptoUtil.aesCmac(key, msg);
            assertThat(Hex.encode(cmac)).isEqualTo("51F0BEBF7E3B9D92FC49741779363CFE");
        }
    }

    // ── AES CBC Decrypt ──

    @Nested
    class AesCbcDecrypt {

        @Test
        void shouldReverseEncrypt() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] original = Hex.decode("0102030405060708090A0B0C0D0E0F10");
            byte[] encrypted = CryptoUtil.aesCbcEncrypt(key, original);
            byte[] decrypted = CryptoUtil.aesCbcDecrypt(key, encrypted);
            assertThat(decrypted).isEqualTo(original);
        }

        @Test
        void shouldReverseEncryptWithIv() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] iv = Hex.decode("AABBCCDD11223344AABBCCDD11223344");
            byte[] original = Hex.decode("0102030405060708090A0B0C0D0E0F10");
            byte[] encrypted = CryptoUtil.aesCbcEncrypt(key, original, iv);
            byte[] decrypted = CryptoUtil.aesCbcDecrypt(key, encrypted, iv);
            assertThat(decrypted).isEqualTo(original);
        }
    }

    // ── Unpad80 ──

    @Nested
    class Unpad80 {

        @Test
        void shouldStripPadding() {
            byte[] original = Hex.decode("0102030405");
            byte[] padded = CryptoUtil.pad80(original, 16);
            byte[] unpadded = CryptoUtil.unpad80(padded);
            assertThat(unpadded).isEqualTo(original);
        }

        @Test
        void shouldStripPaddingFromBlockAlignedData() {
            byte[] original = new byte[16]; // full block
            byte[] padded = CryptoUtil.pad80(original, 16);
            assertThat(padded).hasSize(32); // padded to next block
            byte[] unpadded = CryptoUtil.unpad80(padded);
            assertThat(unpadded).isEqualTo(original);
        }

        @Test
        void shouldHandleEmptyOriginal() {
            byte[] original = new byte[0];
            byte[] padded = CryptoUtil.pad80(original, 16);
            byte[] unpadded = CryptoUtil.unpad80(padded);
            assertThat(unpadded).isEmpty();
        }

        @Test
        void shouldRejectInvalidPadding() {
            byte[] noPadMarker = Hex.decode("01020304050607080900000000000000");
            assertThatThrownBy(() -> CryptoUtil.unpad80(noPadMarker))
                    .isInstanceOf(CryptoException.class)
                    .hasMessageContaining("padding");
        }
    }

    // ── AES ECB ──

    @Nested
    class AesEcb {

        @Test
        void shouldEncryptEcb() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] data = new byte[16];
            byte[] encrypted = CryptoUtil.aesEcbEncrypt(key, data);
            assertThat(encrypted).hasSize(16);
            assertThat(CryptoUtil.aesEcbEncrypt(key, data)).isEqualTo(encrypted);
        }

        @Test
        void shouldDifferFromCbc() {
            byte[] key = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] data = new byte[32]; // 32 zero bytes
            byte[] ecb = CryptoUtil.aesEcbEncrypt(key, data);
            byte[] cbc = CryptoUtil.aesCbcEncrypt(key, data);
            // ECB: same plaintext blocks → same ciphertext blocks
            byte[] ecbBlock1 = java.util.Arrays.copyOfRange(ecb, 0, 16);
            byte[] ecbBlock2 = java.util.Arrays.copyOfRange(ecb, 16, 32);
            assertThat(ecbBlock1).isEqualTo(ecbBlock2);
            // CBC should produce different blocks
            byte[] cbcBlock1 = java.util.Arrays.copyOfRange(cbc, 0, 16);
            byte[] cbcBlock2 = java.util.Arrays.copyOfRange(cbc, 16, 32);
            assertThat(cbcBlock1).isNotEqualTo(cbcBlock2);
        }
    }

    // ── Key derivation ──

    @Nested
    class KeyDerivation {

        // GP constants inlined to avoid dependency on GP module
        private static final byte[] SCP02_DERIVE_C_MAC = {0x01, 0x01};
        private static final byte[] SCP02_DERIVE_ENC = {0x01, (byte) 0x82};
        private static final byte SCP03_DERIVE_C_MAC = 0x06;

        @Test
        void scp02SessionKeyShouldBeDeterministic() {
            byte[] staticKey = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] seqCounter = Hex.decode("0027");
            byte[] key1 = CryptoUtil.deriveSCP02SessionKey(staticKey, seqCounter, SCP02_DERIVE_C_MAC);
            byte[] key2 = CryptoUtil.deriveSCP02SessionKey(staticKey, seqCounter, SCP02_DERIVE_C_MAC);
            assertThat(key1).isEqualTo(key2);
            assertThat(key1).hasSize(16);
        }

        @Test
        void scp02DifferentConstantsShouldProduceDifferentKeys() {
            byte[] staticKey = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] seqCounter = Hex.decode("0027");
            byte[] macKey = CryptoUtil.deriveSCP02SessionKey(staticKey, seqCounter, SCP02_DERIVE_C_MAC);
            byte[] encKey = CryptoUtil.deriveSCP02SessionKey(staticKey, seqCounter, SCP02_DERIVE_ENC);
            assertThat(macKey).isNotEqualTo(encKey);
        }

        @Test
        void scp03SessionKeyShouldBeDeterministic() {
            byte[] staticKey = Hex.decode("404142434445464748494A4B4C4D4E4F");
            byte[] context = Hex.decode("0102030405060708090A0B0C0D0E0F10");
            byte[] key1 = CryptoUtil.deriveSCP03SessionKey(staticKey, context, SCP03_DERIVE_C_MAC, 128);
            byte[] key2 = CryptoUtil.deriveSCP03SessionKey(staticKey, context, SCP03_DERIVE_C_MAC, 128);
            assertThat(key1).isEqualTo(key2);
            assertThat(key1).hasSize(16);
        }

        @Test
        void scp03ShouldDerive256BitKeys() {
            byte[] staticKey = Hex.decode("404142434445464748494A4B4C4D4E4F404142434445464748494A4B4C4D4E4F");
            byte[] context = Hex.decode("0102030405060708090A0B0C0D0E0F10");
            byte[] key = CryptoUtil.deriveSCP03SessionKey(staticKey, context, SCP03_DERIVE_C_MAC, 256);
            assertThat(key).hasSize(32);
        }
    }

    // ── Hashing ──

    @Nested
    class Hashing {

        @Test
        void sha1ShouldComputeCorrectHash() {
            // NIST test vector: SHA-1("abc") = A9993E364706816ABA3E25717850C26C9CD0D89D
            byte[] hash = CryptoUtil.sha1("abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            assertThat(Hex.encode(hash)).isEqualTo("A9993E364706816ABA3E25717850C26C9CD0D89D");
        }

        @Test
        void sha256ShouldComputeCorrectHash() {
            // NIST test vector: SHA-256("abc") = BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD
            byte[] hash = CryptoUtil.sha256("abc".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            assertThat(Hex.encode(hash)).isEqualTo("BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD");
        }
    }

    // ── Helpers ──

    @Test
    void expand3DESKeyShouldRepeatFirst8Bytes() {
        byte[] key16 = Hex.decode("404142434445464748494A4B4C4D4E4F");
        byte[] key24 = CryptoUtil.expand3DESKey(key16);
        assertThat(key24).hasSize(24);
        // First 16 bytes should be original
        assertThat(Hex.encode(key24).substring(0, 32)).isEqualTo("404142434445464748494A4B4C4D4E4F");
        // Last 8 bytes should repeat first 8
        assertThat(Hex.encode(key24).substring(32)).isEqualTo("4041424344454647");
    }

    @Test
    void xorInPlaceShouldWork() {
        byte[] a = {0x0F, 0x0F};
        byte[] b = {(byte) 0xF0, (byte) 0xFF};
        CryptoUtil.xorInPlace(a, b);
        assertThat(a[0]).isEqualTo((byte) 0xFF);
        assertThat(a[1]).isEqualTo((byte) 0xF0);
    }
}
