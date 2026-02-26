package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.crypto.CryptoException;

import name.velikodniy.jcexpress.crypto.CryptoUtil;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SCP03}.
 */
class SCP03Test {

    /**
     * Synthetic INITIALIZE UPDATE response for SCP03.
     *
     * Structure:
     *   [0..9]   Key diversification data
     *   [10..11] Key information (key version + SCP03 identifier)
     *   [12..19] Card challenge (8 bytes for SCP03 i=70)
     *   [20..27] Card cryptogram (8 bytes, derived via AES-CMAC KDF)
     */
    private static final String DIVERSIFICATION = "00010203040506070809";
    private static final String KEY_INFO = "0103"; // version 01, SCP03

    private SCPKeys keys;
    private byte[] hostChallenge;
    private byte[] cardChallenge;

    @BeforeEach
    void setUp() {
        keys = SCPKeys.defaultKeys();
        hostChallenge = Hex.decode("AABBCCDDEEFF0011");
        cardChallenge = Hex.decode("1122334455667788");
    }

    /**
     * Builds a valid INITIALIZE UPDATE response with correct card cryptogram.
     */
    private byte[] buildInitUpdateResponse() {
        // Context for derivation
        byte[] context = new byte[16];
        System.arraycopy(hostChallenge, 0, context, 0, 8);
        System.arraycopy(cardChallenge, 0, context, 8, 8);

        // Card cryptogram = first 8 bytes of KDF(staticMAC, context, CARD_CRYPTO_CONSTANT, 64)
        byte[] cardCryptoFull = CryptoUtil.deriveSCP03SessionKey(
                keys.mac(), context, GP.SCP03_DERIVE_CARD_CRYPTO, 64);
        byte[] cardCryptogram = Arrays.copyOf(cardCryptoFull, 8);

        String responseHex = DIVERSIFICATION + KEY_INFO
                + Hex.encode(cardChallenge)
                + Hex.encode(cardCryptogram);
        return Hex.decode(responseHex);
    }

    @Nested
    class SessionCreation {

        @Test
        void shouldCreateFromInitUpdateResponse() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response);
            assertThat(scp.securityLevel()).isEqualTo(GP.SECURITY_C_MAC);
        }

        @Test
        void shouldDeriveSessionKeys() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response);
            assertThat(scp.sessionMacKey()).hasSize(16);
            assertThat(scp.sessionEncKey()).hasSize(16);
            assertThat(scp.sessionMacKey()).isNotEqualTo(keys.mac());
        }

        @Test
        void shouldRejectShortResponse() {
            byte[] tooShort = new byte[20];
            assertThatThrownBy(() -> SCP03.from(keys, hostChallenge, tooShort))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("too short");
        }

        @Test
        void shouldRejectInvalidCardCryptogram() {
            byte[] response = buildInitUpdateResponse();
            // Corrupt the card cryptogram
            response[25] ^= 0xFF;
            assertThatThrownBy(() -> SCP03.from(keys, hostChallenge, response))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("verification failed");
        }
    }

    @Nested
    class HostCryptogram {

        @Test
        void shouldComputeHostCryptogram() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response);
            byte[] hostCrypto = scp.hostCryptogram();
            assertThat(hostCrypto).hasSize(8);
        }

        @Test
        void hostCryptogramShouldBeDeterministic() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response);
            byte[] crypto1 = scp.hostCryptogram();
            byte[] crypto2 = scp.hostCryptogram();
            assertThat(crypto1).isEqualTo(crypto2);
        }
    }

    @Nested
    class Wrapping {

        @Test
        void shouldWrapCase1Apdu() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response);

            byte[] apdu = Hex.decode("80F20000");
            byte[] wrapped = scp.wrap(apdu);

            assertThat(wrapped).hasSize(4 + 1 + 8); // header + Lc + MAC
            assertThat(wrapped[0] & 0xFF).isEqualTo(0x84); // secure messaging bit
            assertThat(wrapped[4] & 0xFF).isEqualTo(8); // Lc = MAC only
        }

        @Test
        void shouldWrapCase3Apdu() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response);

            byte[] apdu = Hex.decode("80E40000" + "07" + "4F05A000000003");
            byte[] wrapped = scp.wrap(apdu);

            assertThat(wrapped[0] & 0xFF).isEqualTo(0x84);
            // Lc = 7 data + 8 MAC = 15
            assertThat(wrapped[4] & 0xFF).isEqualTo(15);
        }

        @Test
        void shouldWrapWithEncryption() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response, GP.SECURITY_C_MAC_C_ENC);

            byte[] apdu = Hex.decode("80E40000" + "07" + "4F05A000000003");
            byte[] wrapped = scp.wrap(apdu);

            // Data padded to 16-byte boundary: 7 + pad80 = 16, then + 8 MAC = 24
            assertThat(wrapped[4] & 0xFF).isEqualTo(24);
        }

        @Test
        void consecutiveWrapsShouldChain() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response);

            byte[] apdu = Hex.decode("80F20000");
            byte[] wrapped1 = scp.wrap(apdu);
            byte[] wrapped2 = scp.wrap(apdu);

            // MACs should be different due to chaining
            byte[] mac1 = Arrays.copyOfRange(wrapped1, 5, 13);
            byte[] mac2 = Arrays.copyOfRange(wrapped2, 5, 13);
            assertThat(mac1).isNotEqualTo(mac2);
        }

        @Test
        void shouldPreserveLeAfterWrapping() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response);

            // Case 2: header + Le
            byte[] apdu = Hex.decode("80CA00CF00"); // GET DATA with Le=0
            byte[] wrapped = scp.wrap(apdu);

            // Last byte should be Le=0x00
            assertThat(wrapped[wrapped.length - 1]).isEqualTo((byte) 0x00);
        }

        @Test
        void shouldRejectTooShortApdu() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response);

            assertThatThrownBy(() -> scp.wrap(new byte[3]))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("too short");
        }
    }

    @Nested
    class ResponseMac {

        private SCP03 createRmacSession() {
            byte[] response = buildInitUpdateResponse();
            return SCP03.from(keys, hostChallenge, response, GP.SECURITY_C_MAC_C_ENC_R_MAC);
        }

        /** Computes a valid R-MAC for the given plain data and SW using the session's R-MAC key. */
        private byte[] computeRmac(SCP03 scp, byte[] plainData, int sw) {
            byte[] rmacCh = scp.rmacChaining();
            byte[] macInput = new byte[rmacCh.length + plainData.length + 2];
            System.arraycopy(rmacCh, 0, macInput, 0, rmacCh.length);
            System.arraycopy(plainData, 0, macInput, rmacCh.length, plainData.length);
            macInput[macInput.length - 2] = (byte) ((sw >> 8) & 0xFF);
            macInput[macInput.length - 1] = (byte) (sw & 0xFF);

            byte[] fullMac = CryptoUtil.aesCmac(scp.sessionRmacKey(), macInput);
            return Arrays.copyOf(fullMac, 8);
        }

        @Test
        void shouldDeriveRmacSessionKey() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response, GP.SECURITY_C_MAC_C_ENC_R_MAC);

            assertThat(scp.sessionRmacKey()).hasSize(16);
            // R-MAC key should differ from session MAC key
            assertThat(scp.sessionRmacKey()).isNotEqualTo(scp.sessionMacKey());
        }

        @Test
        void shouldUnwrapValidRmacWithSw() {
            SCP03 scp = createRmacSession();

            byte[] plainData = Hex.decode("0102030405");
            int sw = 0x9000;
            byte[] rmac = computeRmac(scp, plainData, sw);

            // Build response data: plainData || R-MAC
            byte[] responseData = new byte[plainData.length + 8];
            System.arraycopy(plainData, 0, responseData, 0, plainData.length);
            System.arraycopy(rmac, 0, responseData, plainData.length, 8);

            APDUResponse raw = new APDUResponse(responseData, sw);
            APDUResponse unwrapped = scp.unwrap(raw);

            assertThat(unwrapped.data()).isEqualTo(plainData);
            assertThat(unwrapped.sw()).isEqualTo(sw);
        }

        @Test
        void shouldRejectInvalidRmac() {
            SCP03 scp = createRmacSession();

            byte[] plainData = Hex.decode("0102030405");
            byte[] badMac = new byte[8];

            byte[] responseData = new byte[plainData.length + 8];
            System.arraycopy(plainData, 0, responseData, 0, plainData.length);
            System.arraycopy(badMac, 0, responseData, plainData.length, 8);

            APDUResponse raw = new APDUResponse(responseData, 0x9000);

            assertThatThrownBy(() -> scp.unwrap(raw))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("Response MAC verification failed");
        }

        @Test
        void shouldUpdateRmacChaining() {
            SCP03 scp = createRmacSession();

            byte[] initialChaining = scp.rmacChaining();
            assertThat(initialChaining).isEqualTo(new byte[16]);

            // First unwrap
            byte[] plainData1 = Hex.decode("0102030405");
            byte[] rmac1 = computeRmac(scp, plainData1, 0x9000);
            byte[] responseData1 = new byte[plainData1.length + 8];
            System.arraycopy(plainData1, 0, responseData1, 0, plainData1.length);
            System.arraycopy(rmac1, 0, responseData1, plainData1.length, 8);
            scp.unwrap(new APDUResponse(responseData1, 0x9000));

            byte[] afterFirst = scp.rmacChaining();
            assertThat(afterFirst).isNotEqualTo(new byte[16]);
            // SCP03 chaining = full 16-byte CMAC (not just the truncated 8 bytes)
            assertThat(afterFirst).hasSize(16);

            // Second unwrap — uses updated chaining
            byte[] plainData2 = Hex.decode("0A0B0C");
            byte[] rmac2 = computeRmac(scp, plainData2, 0x9000);
            byte[] responseData2 = new byte[plainData2.length + 8];
            System.arraycopy(plainData2, 0, responseData2, 0, plainData2.length);
            System.arraycopy(rmac2, 0, responseData2, plainData2.length, 8);
            scp.unwrap(new APDUResponse(responseData2, 0x9000));

            assertThat(scp.rmacChaining()).isNotEqualTo(afterFirst);
        }

        @Test
        void shouldPassThroughWithoutRmac() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response, GP.SECURITY_C_MAC);

            byte[] plainData = Hex.decode("0102030405");
            APDUResponse raw = new APDUResponse(plainData, 0x9000);
            APDUResponse result = scp.unwrap(raw);

            assertThat(result.data()).isEqualTo(plainData);
            assertThat(result.sw()).isEqualTo(0x9000);
        }
    }

    @Nested
    class ResponseEncryption {

        /** Creates an SCP03 session with R-MAC + R-ENC. */
        private SCP03 createRencSession() {
            byte[] response = buildInitUpdateResponse();
            return SCP03.from(keys, hostChallenge, response, GP.SECURITY_C_MAC_C_ENC_R_MAC_R_ENC);
        }

        /** Creates an SCP03 session with R-ENC only (no R-MAC). */
        private SCP03 createRencOnlySession() {
            byte[] response = buildInitUpdateResponse();
            return SCP03.from(keys, hostChallenge, response, 0x21); // C-MAC + R-ENC
        }

        /** Computes a valid R-MAC for the given data and SW. */
        private byte[] computeRmac(SCP03 scp, byte[] data, int sw) {
            byte[] rmacCh = scp.rmacChaining();
            byte[] macInput = new byte[rmacCh.length + data.length + 2];
            System.arraycopy(rmacCh, 0, macInput, 0, rmacCh.length);
            System.arraycopy(data, 0, macInput, rmacCh.length, data.length);
            macInput[macInput.length - 2] = (byte) ((sw >> 8) & 0xFF);
            macInput[macInput.length - 1] = (byte) (sw & 0xFF);

            byte[] fullMac = CryptoUtil.aesCmac(scp.sessionRmacKey(), macInput);
            return Arrays.copyOf(fullMac, 8);
        }

        @Test
        void shouldDecryptAndUnpadResponse() {
            SCP03 scp = createRencSession();
            int sw = 0x9000;

            byte[] plainData = Hex.decode("48656C6C6F"); // "Hello"
            // Encrypt: pad80 + AES-CBC(sessionEncKey, zero IV)
            byte[] padded = CryptoUtil.pad80(plainData, 16);
            byte[] encrypted = CryptoUtil.aesCbcEncrypt(scp.sessionEncKey(), padded);
            // R-MAC over encrypted data
            byte[] rmac = computeRmac(scp, encrypted, sw);

            // Wire: [encrypted] [R-MAC(8)]
            byte[] responseData = new byte[encrypted.length + 8];
            System.arraycopy(encrypted, 0, responseData, 0, encrypted.length);
            System.arraycopy(rmac, 0, responseData, encrypted.length, 8);

            APDUResponse result = scp.unwrap(new APDUResponse(responseData, sw));

            assertThat(result.data()).isEqualTo(plainData);
            assertThat(result.sw()).isEqualTo(sw);
        }

        @Test
        void shouldHandleRencWithoutRmac() {
            SCP03 scp = createRencOnlySession();

            byte[] plainData = Hex.decode("0102030405");
            byte[] padded = CryptoUtil.pad80(plainData, 16);
            byte[] encrypted = CryptoUtil.aesCbcEncrypt(scp.sessionEncKey(), padded);

            APDUResponse result = scp.unwrap(new APDUResponse(encrypted, 0x9000));

            assertThat(result.data()).isEqualTo(plainData);
            assertThat(result.sw()).isEqualTo(0x9000);
        }

        @Test
        void shouldPassThroughEmptyDataWithRenc() {
            SCP03 scp = createRencSession();

            // R-MAC over empty encrypted data
            byte[] emptyEncrypted = new byte[0];
            byte[] rmac = computeRmac(scp, emptyEncrypted, 0x9000);

            // Wire: just R-MAC (no encrypted data)
            APDUResponse result = scp.unwrap(new APDUResponse(rmac, 0x9000));

            assertThat(result.data()).isEmpty();
            assertThat(result.sw()).isEqualTo(0x9000);
        }

        @Test
        void shouldRejectBadPaddingAfterDecrypt() {
            SCP03 scp = createRencSession();

            // Encrypted data that decrypts to invalid padding (no 0x80 marker)
            // Use raw AES-CBC encrypt of 16 zero bytes — will decrypt to zeros, no padding marker
            byte[] badPlain = new byte[16]; // all zeros, no 0x80 padding marker
            byte[] encrypted = CryptoUtil.aesCbcEncrypt(scp.sessionEncKey(), badPlain);
            byte[] rmac = computeRmac(scp, encrypted, 0x9000);

            byte[] responseData = new byte[encrypted.length + 8];
            System.arraycopy(encrypted, 0, responseData, 0, encrypted.length);
            System.arraycopy(rmac, 0, responseData, encrypted.length, 8);

            APDUResponse raw = new APDUResponse(responseData, 0x9000);
            assertThatThrownBy(() -> scp.unwrap(raw))
                    .isInstanceOf(CryptoException.class)
                    .hasMessageContaining("padding");
        }

        @Test
        void shouldLeaveResponseUnchangedWithoutRenc() {
            byte[] response = buildInitUpdateResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response, GP.SECURITY_C_MAC);

            byte[] plainData = Hex.decode("0102030405");
            APDUResponse result = scp.unwrap(new APDUResponse(plainData, 0x9000));

            assertThat(result.data()).isEqualTo(plainData);
        }
    }

    @Nested
    class PseudoRandomChallenge {

        private static final String SEQ_COUNTER = "001234";
        private static final String CARD_CHALLENGE_6 = "AABB11223344";

        /**
         * Builds a 29-byte INIT UPDATE response for SCP03 pseudo-random (i=60).
         * Format: divData(10) || keyInfo(2) || seqCounter(3) || cardChallenge(6) || cryptogram(8)
         */
        private byte[] buildPseudoRandomResponse() {
            byte[] seqCounter = Hex.decode(SEQ_COUNTER);
            byte[] cardCh = Hex.decode(CARD_CHALLENGE_6);

            // Context = hostChallenge(8) || seqCounter(3) || cardChallenge(6) = 17 bytes
            byte[] context = new byte[17];
            System.arraycopy(hostChallenge, 0, context, 0, 8);
            System.arraycopy(seqCounter, 0, context, 8, 3);
            System.arraycopy(cardCh, 0, context, 11, 6);

            byte[] cardCryptoFull = CryptoUtil.deriveSCP03SessionKey(
                    keys.mac(), context, GP.SCP03_DERIVE_CARD_CRYPTO, 64);
            byte[] cardCryptogram = Arrays.copyOf(cardCryptoFull, 8);

            // Build 29-byte response
            String responseHex = DIVERSIFICATION + KEY_INFO
                    + SEQ_COUNTER
                    + CARD_CHALLENGE_6
                    + Hex.encode(cardCryptogram);
            return Hex.decode(responseHex);
        }

        @Test
        void shouldCreateFromPseudoRandomResponse() {
            byte[] response = buildPseudoRandomResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response, GP.SECURITY_C_MAC, true);
            assertThat(scp.securityLevel()).isEqualTo(GP.SECURITY_C_MAC);
        }

        @Test
        void shouldDeriveSessionKeysForPseudoRandom() {
            byte[] prResponse = buildPseudoRandomResponse();
            SCP03 prScp = SCP03.from(keys, hostChallenge, prResponse, GP.SECURITY_C_MAC, true);

            byte[] exResponse = buildInitUpdateResponse();
            SCP03 exScp = SCP03.from(keys, hostChallenge, exResponse, GP.SECURITY_C_MAC, false);

            // Different context → different session keys
            assertThat(prScp.sessionMacKey()).isNotEqualTo(exScp.sessionMacKey());
            assertThat(prScp.sessionEncKey()).isNotEqualTo(exScp.sessionEncKey());
        }

        @Test
        void shouldComputeHostCryptogramForPseudoRandom() {
            byte[] response = buildPseudoRandomResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response, GP.SECURITY_C_MAC, true);
            byte[] hostCrypto = scp.hostCryptogram();
            assertThat(hostCrypto).hasSize(8);
            // Should be deterministic
            assertThat(scp.hostCryptogram()).isEqualTo(hostCrypto);
        }

        @Test
        void shouldWrapCommandInPseudoRandomMode() {
            byte[] response = buildPseudoRandomResponse();
            SCP03 scp = SCP03.from(keys, hostChallenge, response, GP.SECURITY_C_MAC, true);

            byte[] apdu = Hex.decode("80F20000");
            byte[] wrapped = scp.wrap(apdu);

            assertThat(wrapped).hasSize(4 + 1 + 8); // header + Lc + MAC
            assertThat(wrapped[0] & 0xFF).isEqualTo(0x84); // secure messaging bit
            assertThat(wrapped[4] & 0xFF).isEqualTo(8); // Lc = MAC only
        }

        @Test
        void shouldRejectShortPseudoRandomResponse() {
            byte[] tooShort = new byte[28]; // 28 < 29
            assertThatThrownBy(() -> SCP03.from(keys, hostChallenge, tooShort, GP.SECURITY_C_MAC, true))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("pseudo-random");
        }
    }
}
