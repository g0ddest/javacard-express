package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SMCodec}.
 */
class SMCodecTest {

    private static final byte[] DES3_ENC_KEY = Hex.decode("979EC13B1CBFE9DCD01AB0FED307EAE5");
    private static final byte[] DES3_MAC_KEY = Hex.decode("F1CB1F1FB5ADF208806B89DC579DC1F8");
    private static final byte[] DES3_INITIAL_SSC = Hex.decode("887022120C06C227");

    private SMContext des3Context() {
        return new SMContext(SMAlgorithm.DES3,
                new SMKeys(DES3_ENC_KEY, DES3_MAC_KEY),
                DES3_INITIAL_SSC.clone());
    }

    private static final byte[] AES_ENC_KEY = Hex.decode("404142434445464748494A4B4C4D4E4F");
    private static final byte[] AES_MAC_KEY = Hex.decode("505152535455565758595A5B5C5D5E5F");
    private static final byte[] AES_INITIAL_SSC = new byte[16];

    private SMContext aesContext() {
        return new SMContext(SMAlgorithm.AES,
                new SMKeys(AES_ENC_KEY, AES_MAC_KEY),
                AES_INITIAL_SSC.clone());
    }

    // ── DES3 Command Wrapping ──

    @Nested
    class Des3Wrapping {

        @Test
        void wrapCommandWithDataShouldProduceDO87AndDO8E() {
            SMContext ctx = des3Context();
            // SELECT command: 00 A4 04 00 07 A0000002471001
            byte[] apdu = Hex.decode("00A4040007A0000002471001");
            byte[] wrapped = SMCodec.wrapCommand(ctx, apdu);

            // Wrapped APDU should start with CLA=0C
            assertThat(wrapped[0]).isEqualTo((byte) 0x0C);
            assertThat(wrapped[1]).isEqualTo((byte) 0xA4); // INS preserved
            assertThat(wrapped[2]).isEqualTo((byte) 0x04); // P1 preserved
            assertThat(wrapped[3]).isEqualTo((byte) 0x00); // P2 preserved

            // Body should contain DO87 and DO8E
            byte[] body = Arrays.copyOfRange(wrapped, 5, 5 + (wrapped[4] & 0xFF));
            Map<Integer, byte[]> dos = SMCodec.parseSMDataObjects(body);
            assertThat(dos).containsKey(0x87);
            assertThat(dos).containsKey(0x8E);
            assertThat(dos.get(0x8E)).hasSize(8); // 8-byte MAC

            // DO87 value should start with 0x01 (padding indicator)
            assertThat(dos.get(0x87)[0]).isEqualTo((byte) 0x01);
        }

        @Test
        void wrapCommandWithLeShouldIncludeDO97() {
            SMContext ctx = des3Context();
            // READ BINARY with Le: 00 B0 00 00 04
            byte[] apdu = Hex.decode("00B0000004");
            byte[] wrapped = SMCodec.wrapCommand(ctx, apdu);

            assertThat(wrapped[0]).isEqualTo((byte) 0x0C);

            byte[] body = Arrays.copyOfRange(wrapped, 5, 5 + (wrapped[4] & 0xFF));
            Map<Integer, byte[]> dos = SMCodec.parseSMDataObjects(body);
            assertThat(dos).containsKey(0x97);
            assertThat(dos).containsKey(0x8E);
            assertThat(dos.get(0x97)).isEqualTo(new byte[]{0x04});
        }

        @Test
        void wrapCommandWithDataAndLeShouldIncludeAllDOs() {
            SMContext ctx = des3Context();
            // Case 4: CLA INS P1 P2 Lc Data Le
            byte[] apdu = Hex.decode("00A40400 07 A0000002471001 00");
            byte[] wrapped = SMCodec.wrapCommand(ctx, apdu);

            byte[] body = Arrays.copyOfRange(wrapped, 5, 5 + (wrapped[4] & 0xFF));
            Map<Integer, byte[]> dos = SMCodec.parseSMDataObjects(body);
            assertThat(dos).containsKey(0x87);
            assertThat(dos).containsKey(0x97);
            assertThat(dos).containsKey(0x8E);
        }

        @Test
        void wrapCase1CommandShouldOnlyHaveDO8E() {
            SMContext ctx = des3Context();
            // Case 1: CLA INS P1 P2 only
            byte[] apdu = Hex.decode("00700000");
            byte[] wrapped = SMCodec.wrapCommand(ctx, apdu);

            byte[] body = Arrays.copyOfRange(wrapped, 5, 5 + (wrapped[4] & 0xFF));
            Map<Integer, byte[]> dos = SMCodec.parseSMDataObjects(body);
            assertThat(dos).doesNotContainKey(0x87);
            assertThat(dos).doesNotContainKey(0x97);
            assertThat(dos).containsKey(0x8E);
        }
    }

    // ── DES3 Response Unwrapping ──

    @Nested
    class Des3Unwrapping {

        @Test
        void unwrapResponseWithDO87ShouldDecryptData() {
            // We wrap a command, then construct a matching SM response
            SMContext wrapCtx = des3Context();
            SMContext unwrapCtx = des3Context();

            // Encrypt some data for the response
            byte[] plainData = Hex.decode("01020304");
            byte[] padded = SMAlgorithm.DES3.pad(plainData);
            byte[] encrypted = SMAlgorithm.DES3.encrypt(
                    unwrapCtx.encKey(), padded, new byte[8]);

            // Build DO87
            byte[] do87value = new byte[1 + encrypted.length];
            do87value[0] = 0x01;
            System.arraycopy(encrypted, 0, do87value, 1, encrypted.length);

            // Build DO99
            byte[] do99 = {(byte) 0x99, 0x02, (byte) 0x90, 0x00};

            // Build DO87 TLV
            ByteArrayOutputStream do87tlv = new ByteArrayOutputStream();
            do87tlv.write(0x87);
            do87tlv.write(do87value.length);
            do87tlv.write(do87value, 0, do87value.length);

            // Increment SSC to match unwrap
            unwrapCtx.incrementSsc();

            // Compute MAC over SSC || pad(DO87 || DO99)
            ByteArrayOutputStream macInput = new ByteArrayOutputStream();
            macInput.write(unwrapCtx.ssc(), 0, unwrapCtx.ssc().length);
            macInput.write(do87tlv.toByteArray(), 0, do87tlv.size());
            macInput.write(do99, 0, do99.length);
            byte[] macData = SMAlgorithm.DES3.pad(macInput.toByteArray());
            byte[] mac = SMAlgorithm.DES3.mac(unwrapCtx.macKey(), macData);

            // Build DO8E
            byte[] do8e = new byte[2 + mac.length];
            do8e[0] = (byte) 0x8E;
            do8e[1] = (byte) mac.length;
            System.arraycopy(mac, 0, do8e, 2, mac.length);

            // Assemble response: DO87 || DO99 || DO8E || 9000
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            response.write(do87tlv.toByteArray(), 0, do87tlv.size());
            response.write(do99, 0, do99.length);
            response.write(do8e, 0, do8e.length);
            response.write(0x90);
            response.write(0x00);

            // Now unwrap using a fresh context
            SMContext ctx = des3Context();
            APDUResponse result = SMCodec.unwrapResponse(ctx, response.toByteArray());

            assertThat(result.sw()).isEqualTo(0x9000);
            assertThat(result.data()).isEqualTo(plainData);
        }

        @Test
        void unwrapResponseWithoutDO87ShouldReturnEmpty() {
            SMContext ctx = des3Context();

            // Build response with only DO99 + DO8E (no data)
            byte[] do99 = {(byte) 0x99, 0x02, (byte) 0x90, 0x00};

            // Compute MAC
            ctx.incrementSsc();
            ByteArrayOutputStream macInput = new ByteArrayOutputStream();
            macInput.write(ctx.ssc(), 0, ctx.ssc().length);
            macInput.write(do99, 0, do99.length);
            byte[] macData = SMAlgorithm.DES3.pad(macInput.toByteArray());
            byte[] mac = SMAlgorithm.DES3.mac(ctx.macKey(), macData);

            byte[] do8e = new byte[2 + mac.length];
            do8e[0] = (byte) 0x8E;
            do8e[1] = (byte) mac.length;
            System.arraycopy(mac, 0, do8e, 2, mac.length);

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            response.write(do99, 0, do99.length);
            response.write(do8e, 0, do8e.length);
            response.write(0x90);
            response.write(0x00);

            SMContext freshCtx = des3Context();
            APDUResponse result = SMCodec.unwrapResponse(freshCtx, response.toByteArray());
            assertThat(result.sw()).isEqualTo(0x9000);
            assertThat(result.data()).isEmpty();
        }

        @Test
        void unwrapResponseWithBadMacShouldThrow() {
            byte[] do99 = {(byte) 0x99, 0x02, (byte) 0x90, 0x00};
            byte[] do8e = {(byte) 0x8E, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            response.write(do99, 0, do99.length);
            response.write(do8e, 0, do8e.length);
            response.write(0x90);
            response.write(0x00);

            SMContext ctx = des3Context();
            assertThatThrownBy(() -> SMCodec.unwrapResponse(ctx, response.toByteArray()))
                    .isInstanceOf(SMException.class)
                    .hasMessageContaining("MAC verification failed");
        }

        @Test
        void nonSuccessOuterSwShouldPassThrough() {
            SMContext ctx = des3Context();
            // Card error: 6A82 (file not found)
            byte[] response = {(byte) 0x6A, (byte) 0x82};
            APDUResponse result = SMCodec.unwrapResponse(ctx, response);
            assertThat(result.sw()).isEqualTo(0x6A82);
            assertThat(result.data()).isEmpty();
        }
    }

    // ── SSC ──

    @Nested
    class SscManagement {

        @Test
        void sscShouldIncrementAfterWrap() {
            SMContext ctx = des3Context();
            byte[] sscBefore = ctx.ssc();

            SMCodec.wrapCommand(ctx, Hex.decode("00700000"));

            byte[] sscAfter = ctx.ssc();
            assertThat(sscAfter).isNotEqualTo(sscBefore);
        }

        @Test
        void sscIncrementShouldCarry() {
            byte[] ssc = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF};
            SMContext ctx = new SMContext(SMAlgorithm.DES3,
                    new SMKeys(DES3_ENC_KEY, DES3_MAC_KEY), ssc);

            ctx.incrementSsc();

            byte[] expected = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00};
            assertThat(ctx.ssc()).isEqualTo(expected);
        }

        @Test
        void sscIncrementShouldCarryMultipleBytes() {
            byte[] ssc = {0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
            SMContext ctx = new SMContext(SMAlgorithm.DES3,
                    new SMKeys(DES3_ENC_KEY, DES3_MAC_KEY), ssc);

            ctx.incrementSsc();

            byte[] expected = {0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00};
            assertThat(ctx.ssc()).isEqualTo(expected);
        }
    }

    // ── DO builders ──

    @Nested
    class DoBuilders {

        @Test
        void buildDO87ShouldEncryptAndPad() {
            byte[] data = Hex.decode("01020304");
            byte[] do87 = SMCodec.buildDO87(SMAlgorithm.DES3, DES3_ENC_KEY, data);

            // Should start with tag 0x87
            assertThat(do87[0]).isEqualTo((byte) 0x87);

            // Parse the DO and check padding indicator
            Map<Integer, byte[]> parsed = SMCodec.parseSMDataObjects(do87);
            byte[] value = parsed.get(0x87);
            assertThat(value[0]).isEqualTo((byte) 0x01); // padding indicator

            // Encrypted data should be block-aligned (multiple of 8 for DES3)
            assertThat((value.length - 1) % 8).isZero();
        }

        @Test
        void buildDO97ShouldEncodeLe() {
            byte[] do97 = SMCodec.buildDO97(4);
            assertThat(do97).isEqualTo(new byte[]{(byte) 0x97, 0x01, 0x04});
        }

        @Test
        void buildDO97ShouldEncodeLeZeroAs256() {
            byte[] do97 = SMCodec.buildDO97(256);
            assertThat(do97).isEqualTo(new byte[]{(byte) 0x97, 0x01, 0x00});
        }

        @Test
        void buildDO8EShouldWrapMac() {
            byte[] mac = Hex.decode("0102030405060708");
            byte[] do8e = SMCodec.buildDO8E(mac);
            assertThat(do8e[0]).isEqualTo((byte) 0x8E);
            assertThat(do8e[1]).isEqualTo((byte) 0x08);
            assertThat(Arrays.copyOfRange(do8e, 2, 10)).isEqualTo(mac);
        }
    }

    // ── SM Data Object Parser ──

    @Nested
    class Parser {

        @Test
        void parseSMDataObjectsShouldExtractAll() {
            // Construct: DO97(01 04) || DO99(02 90 00) || DO8E(08 01..08)
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.write(0x97);
            buf.write(0x01);
            buf.write(0x04);
            buf.write(0x99);
            buf.write(0x02);
            buf.write(0x90);
            buf.write(0x00);
            byte[] mac = {0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08};
            buf.write(0x8E);
            buf.write(0x08);
            buf.write(mac, 0, mac.length);

            Map<Integer, byte[]> dos = SMCodec.parseSMDataObjects(buf.toByteArray());
            assertThat(dos).containsKey(0x97);
            assertThat(dos).containsKey(0x99);
            assertThat(dos).containsKey(0x8E);
            assertThat(dos.get(0x97)).isEqualTo(new byte[]{0x04});
            assertThat(dos.get(0x99)).isEqualTo(new byte[]{(byte) 0x90, 0x00});
            assertThat(dos.get(0x8E)).isEqualTo(mac);
        }

        @Test
        void parseShouldHandleLongFormLength() {
            // Tag 0x87 with length > 127
            byte[] value = new byte[130];
            Arrays.fill(value, (byte) 0xAB);
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            buf.write(0x87);
            buf.write(0x81); // long form: 1 byte follows
            buf.write(130);
            buf.write(value, 0, value.length);

            Map<Integer, byte[]> dos = SMCodec.parseSMDataObjects(buf.toByteArray());
            assertThat(dos.get(0x87)).hasSize(130);
        }
    }

    // ── AES suite ──

    @Nested
    class AesWrapping {

        @Test
        void aesWrapCommandShouldUseCmac() {
            SMContext ctx = aesContext();
            byte[] apdu = Hex.decode("00B0000004"); // READ BINARY Le=4
            byte[] wrapped = SMCodec.wrapCommand(ctx, apdu);

            assertThat(wrapped[0]).isEqualTo((byte) 0x0C);

            byte[] body = Arrays.copyOfRange(wrapped, 5, 5 + (wrapped[4] & 0xFF));
            Map<Integer, byte[]> dos = SMCodec.parseSMDataObjects(body);
            assertThat(dos).containsKey(0x97);
            assertThat(dos).containsKey(0x8E);
            assertThat(dos.get(0x8E)).hasSize(8); // AES-CMAC truncated to 8
        }

        @Test
        void aesWrapUnwrapRoundTripShouldWork() {
            // Use two identical contexts
            SMContext wrapCtx = aesContext();
            SMContext verifyCtx = aesContext();

            byte[] plainData = Hex.decode("DEADBEEF");

            // Build a valid SM response manually using the verify context
            byte[] padded = SMAlgorithm.AES.pad(plainData);
            byte[] encrypted = SMAlgorithm.AES.encrypt(
                    verifyCtx.encKey(), padded, new byte[16]);

            // DO87
            byte[] do87value = new byte[1 + encrypted.length];
            do87value[0] = 0x01;
            System.arraycopy(encrypted, 0, do87value, 1, encrypted.length);

            ByteArrayOutputStream do87tlv = new ByteArrayOutputStream();
            do87tlv.write(0x87);
            do87tlv.write(do87value.length);
            do87tlv.write(do87value, 0, do87value.length);

            // DO99
            byte[] do99 = {(byte) 0x99, 0x02, (byte) 0x90, 0x00};

            // Compute MAC
            verifyCtx.incrementSsc();
            ByteArrayOutputStream macInput = new ByteArrayOutputStream();
            macInput.write(verifyCtx.ssc(), 0, verifyCtx.ssc().length);
            macInput.write(do87tlv.toByteArray(), 0, do87tlv.size());
            macInput.write(do99, 0, do99.length);
            byte[] macDataPadded = SMAlgorithm.AES.pad(macInput.toByteArray());
            byte[] mac = SMAlgorithm.AES.mac(verifyCtx.macKey(), macDataPadded);

            // DO8E
            byte[] do8e = new byte[2 + mac.length];
            do8e[0] = (byte) 0x8E;
            do8e[1] = (byte) mac.length;
            System.arraycopy(mac, 0, do8e, 2, mac.length);

            // Response
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            response.write(do87tlv.toByteArray(), 0, do87tlv.size());
            response.write(do99, 0, do99.length);
            response.write(do8e, 0, do8e.length);
            response.write(0x90);
            response.write(0x00);

            // Unwrap with fresh context
            SMContext freshCtx = aesContext();
            APDUResponse result = SMCodec.unwrapResponse(freshCtx, response.toByteArray());
            assertThat(result.sw()).isEqualTo(0x9000);
            assertThat(result.data()).isEqualTo(plainData);
        }
    }
}
