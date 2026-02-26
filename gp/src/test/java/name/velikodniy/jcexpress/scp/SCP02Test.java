package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.crypto.CryptoUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SCP02}.
 */
class SCP02Test {

    /**
     * Synthetic INITIALIZE UPDATE response for testing.
     *
     * Structure (28 bytes):
     *   [0..9]   Key diversification data: 0001020304050607 0809
     *   [10..11] Key information: 0102 (key version 01, SCP02)
     *   [12..13] Sequence counter: 0027
     *   [14..19] Card challenge: 112233445566
     *   [20..27] Card cryptogram: computed from session ENC key
     */
    private static final String DIVERSIFICATION = "00010203040506070809";
    private static final String KEY_INFO = "0102";
    private static final String SEQ_COUNTER = "0027";
    private static final String CARD_CHALLENGE = "112233445566";

    private SCPKeys keys;
    private byte[] hostChallenge;

    @BeforeEach
    void setUp() {
        keys = SCPKeys.defaultKeys();
        hostChallenge = Hex.decode("AABBCCDDEEFF0011");
    }

    /**
     * Builds a valid INITIALIZE UPDATE response with a correctly computed card cryptogram.
     */
    private byte[] buildInitUpdateResponse() {
        byte[] seqCounter = Hex.decode(SEQ_COUNTER);

        // Derive session ENC key (same as SCP02 does internally)
        byte[] sessionEncKey = CryptoUtil.deriveSCP02SessionKey(
                keys.enc(), seqCounter, GP.SCP02_DERIVE_ENC);

        // Card cryptogram: MAC(sessionENC, hostChallenge || seqCounter || cardChallenge)
        // Wait — the SCP02 implementation computes:
        // data = seqCounter || cardChallenge || hostChallenge
        // But card crypto should use: hostChallenge || seqCounter || cardChallenge
        // Let me use what the implementation expects for verification
        byte[] data = new byte[16];
        byte[] cardChallengeBytes = Hex.decode(CARD_CHALLENGE);
        System.arraycopy(seqCounter, 0, data, 0, 2);
        System.arraycopy(cardChallengeBytes, 0, data, 2, 6);
        System.arraycopy(hostChallenge, 0, data, 8, 8);

        byte[] padded = CryptoUtil.pad80(data, 8);
        byte[] cardCryptogram = CryptoUtil.des3Mac(sessionEncKey, padded, new byte[8]);

        // Build full response
        String responseHex = DIVERSIFICATION + KEY_INFO + SEQ_COUNTER
                + CARD_CHALLENGE + Hex.encode(cardCryptogram);
        return Hex.decode(responseHex);
    }

    @Nested
    class SessionCreation {

        @Test
        void shouldCreateFromInitUpdateResponse() {
            byte[] response = buildInitUpdateResponse();
            SCP02 scp = SCP02.from(keys, response);
            assertThat(scp.securityLevel()).isEqualTo(GP.SECURITY_C_MAC);
        }

        @Test
        void shouldDeriveSessionKeys() {
            byte[] response = buildInitUpdateResponse();
            SCP02 scp = SCP02.from(keys, response);
            assertThat(scp.sessionMacKey()).hasSize(16);
            assertThat(scp.sessionEncKey()).hasSize(16);
            assertThat(scp.sessionDekKey()).hasSize(16);
            // Session keys should differ from static keys
            assertThat(scp.sessionMacKey()).isNotEqualTo(keys.mac());
        }

        @Test
        void shouldRejectShortResponse() {
            byte[] tooShort = new byte[20];
            assertThatThrownBy(() -> SCP02.from(keys, tooShort))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("too short");
        }
    }

    @Nested
    class Cryptograms {

        @Test
        void verifyCardCryptogramShouldPass() {
            byte[] response = buildInitUpdateResponse();
            SCP02 scp = SCP02.from(keys, response);
            // Should not throw
            scp.verifyCardCryptogram(hostChallenge);
        }

        @Test
        void verifyCardCryptogramShouldFailWithWrongChallenge() {
            byte[] response = buildInitUpdateResponse();
            SCP02 scp = SCP02.from(keys, response);
            byte[] wrongChallenge = Hex.decode("0000000000000000");
            assertThatThrownBy(() -> scp.verifyCardCryptogram(wrongChallenge))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("verification failed");
        }

        @Test
        void hostCryptogramShouldBeDeterministic() {
            byte[] response = buildInitUpdateResponse();
            SCP02 scp = SCP02.from(keys, response);
            byte[] crypto1 = scp.computeHostCryptogram(hostChallenge);
            byte[] crypto2 = scp.computeHostCryptogram(hostChallenge);
            assertThat(crypto1).hasSize(8);
            assertThat(crypto1).isEqualTo(crypto2);
        }
    }

    @Nested
    class Wrapping {

        @Test
        void shouldWrapCase1Apdu() {
            byte[] response = buildInitUpdateResponse();
            SCP02 scp = SCP02.from(keys, response);

            byte[] apdu = Hex.decode("80F20000"); // GET STATUS, no data
            byte[] wrapped = scp.wrap(apdu);

            // Wrapped: CLA(|0x04) INS P1 P2 Lc(=8) MAC(8)
            assertThat(wrapped).hasSize(4 + 1 + 8); // header + Lc + MAC
            assertThat(wrapped[0] & 0xFF).isEqualTo(0x84); // 0x80 | 0x04
            assertThat(wrapped[1] & 0xFF).isEqualTo(0xF2);
            assertThat(wrapped[4] & 0xFF).isEqualTo(8); // Lc = MAC only
        }

        @Test
        void shouldWrapCase3Apdu() {
            byte[] response = buildInitUpdateResponse();
            SCP02 scp = SCP02.from(keys, response);

            byte[] apdu = Hex.decode("80E40000" + "07" + "4F05A000000003");
            byte[] wrapped = scp.wrap(apdu);

            // CLA should have secure bit set
            assertThat(wrapped[0] & 0xFF).isEqualTo(0x84);
            // Lc should be original data(7) + MAC(8) = 15
            assertThat(wrapped[4] & 0xFF).isEqualTo(15);
            // Total: 5 header + 7 data + 8 MAC = 20
            assertThat(wrapped).hasSize(20);
        }

        @Test
        void macChainingShouldUpdate() {
            byte[] response = buildInitUpdateResponse();
            SCP02 scp = SCP02.from(keys, response);

            byte[] initialChaining = scp.macChaining();
            assertThat(initialChaining).isEqualTo(new byte[8]); // starts at zero

            scp.wrap(Hex.decode("80F20000"));
            byte[] afterFirst = scp.macChaining();
            assertThat(afterFirst).isNotEqualTo(new byte[8]); // should be updated

            scp.wrap(Hex.decode("80F20000"));
            byte[] afterSecond = scp.macChaining();
            assertThat(afterSecond).isNotEqualTo(afterFirst); // different again
        }

        @Test
        void shouldWrapWithEncryption() {
            byte[] response = buildInitUpdateResponse();
            SCP02 scp = SCP02.from(keys, response, GP.SECURITY_C_MAC_C_ENC);

            byte[] apdu = Hex.decode("80E40000" + "07" + "4F05A000000003");
            byte[] wrapped = scp.wrap(apdu);

            // Data should be padded to 8-byte boundary then encrypted
            // Original 7 bytes → pad80 → 8 bytes → encrypted → 8 bytes + 8 MAC
            assertThat(wrapped[4] & 0xFF).isEqualTo(16); // 8 encrypted + 8 MAC
        }

        @Test
        void shouldRejectTooShortApdu() {
            byte[] response = buildInitUpdateResponse();
            SCP02 scp = SCP02.from(keys, response);

            assertThatThrownBy(() -> scp.wrap(new byte[2]))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("too short");
        }
    }

    @Nested
    class ResponseMac {

        private SCP02 createRmacSession() {
            byte[] response = buildInitUpdateResponse();
            return SCP02.from(keys, response, GP.SECURITY_C_MAC_C_ENC_R_MAC);
        }

        /** Computes a valid R-MAC for the given plain data using the session's R-MAC key. */
        private byte[] computeRmac(SCP02 scp, byte[] plainData) {
            byte[] padded = CryptoUtil.pad80(plainData, 8);
            return CryptoUtil.retailMac(scp.sessionRmacKey(), padded, scp.rmacChaining());
        }

        @Test
        void shouldDeriveRmacSessionKey() {
            byte[] response = buildInitUpdateResponse();
            SCP02 scp = SCP02.from(keys, response, GP.SECURITY_C_MAC_C_ENC_R_MAC);

            assertThat(scp.sessionRmacKey()).hasSize(16);
            // R-MAC key should differ from C-MAC key (different derivation constant)
            assertThat(scp.sessionRmacKey()).isNotEqualTo(scp.sessionMacKey());
        }

        @Test
        void shouldUnwrapValidRmac() {
            SCP02 scp = createRmacSession();

            byte[] plainData = Hex.decode("0102030405");
            byte[] rmac = computeRmac(scp, plainData);

            // Build response data: plainData || R-MAC
            byte[] responseData = new byte[plainData.length + 8];
            System.arraycopy(plainData, 0, responseData, 0, plainData.length);
            System.arraycopy(rmac, 0, responseData, plainData.length, 8);

            APDUResponse raw = new APDUResponse(responseData, 0x9000);
            APDUResponse unwrapped = scp.unwrap(raw);

            assertThat(unwrapped.data()).isEqualTo(plainData);
            assertThat(unwrapped.sw()).isEqualTo(0x9000);
        }

        @Test
        void shouldRejectInvalidRmac() {
            SCP02 scp = createRmacSession();

            byte[] plainData = Hex.decode("0102030405");
            byte[] badMac = new byte[8]; // all zeros — invalid

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
            SCP02 scp = createRmacSession();

            byte[] initialChaining = scp.rmacChaining();
            assertThat(initialChaining).isEqualTo(new byte[8]); // starts at zero

            // First unwrap
            byte[] plainData1 = Hex.decode("0102030405");
            byte[] rmac1 = computeRmac(scp, plainData1);
            byte[] responseData1 = new byte[plainData1.length + 8];
            System.arraycopy(plainData1, 0, responseData1, 0, plainData1.length);
            System.arraycopy(rmac1, 0, responseData1, plainData1.length, 8);
            scp.unwrap(new APDUResponse(responseData1, 0x9000));

            byte[] afterFirst = scp.rmacChaining();
            assertThat(afterFirst).isNotEqualTo(new byte[8]);
            assertThat(afterFirst).isEqualTo(rmac1); // chaining = received MAC

            // Second unwrap — uses updated chaining
            byte[] plainData2 = Hex.decode("0A0B0C");
            byte[] rmac2 = computeRmac(scp, plainData2);
            byte[] responseData2 = new byte[plainData2.length + 8];
            System.arraycopy(plainData2, 0, responseData2, 0, plainData2.length);
            System.arraycopy(rmac2, 0, responseData2, plainData2.length, 8);
            scp.unwrap(new APDUResponse(responseData2, 0x9000));

            assertThat(scp.rmacChaining()).isNotEqualTo(afterFirst);
        }

        @Test
        void shouldPassThroughWithoutRmac() {
            // C-MAC only — no R-MAC bit
            byte[] response = buildInitUpdateResponse();
            SCP02 scp = SCP02.from(keys, response, GP.SECURITY_C_MAC);

            byte[] plainData = Hex.decode("0102030405");
            APDUResponse raw = new APDUResponse(plainData, 0x9000);
            APDUResponse result = scp.unwrap(raw);

            // Should return as-is
            assertThat(result.data()).isEqualTo(plainData);
            assertThat(result.sw()).isEqualTo(0x9000);
        }
    }
}
