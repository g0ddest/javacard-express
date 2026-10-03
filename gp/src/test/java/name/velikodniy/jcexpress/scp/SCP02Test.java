package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.gp.ScpTranscript;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SCP02} (GlobalPlatform Card Specification v2.3.1 Appendix E).
 *
 * <p>All expected values come from outside javacard-express: the public real-card session
 * {@code SCP02_real_card_i15_session1} (GlobalPlatformPro log, keys 40..4F), the public GlobalPlatformPro
 * key check values for sequence counter '0000', and spec-derived vectors of the independent reference
 * implementation (see {@link ScpTranscript}).</p>
 */
class SCP02Test {

    private static final SCPKeys TEST_KEYS = SCPKeys.defaultKeys();
    private static final ScpTranscript REAL = ScpTranscript.named("SCP02_real_card_i15_session1");

    /** INITIALIZE UPDATE response with sequence counter '0000' (cryptogram irrelevant for key derivation). */
    private static final byte[] SEQUENCE_0000 = Hex.decode("00010203040506070809" + "0002" + "0000"
            + "112233445566" + "0000000000000000");

    private static SCP02 realSession(int level) {
        return SCP02.from(TEST_KEYS, REAL.initUpdate(), level, 0x15);
    }

    @Nested
    class SessionKeys {

        /** GlobalPlatformPro TestPlaintextKeys.testSessionKeys_SCP02 KCVs (public data, keys 40..4F, seq '0000'). */
        @Test
        void e_4_1_sessionKeysMatchPublicKeyCheckValues() {
            SCP02 scp = SCP02.from(TEST_KEYS, SEQUENCE_0000);

            assertThat(Hex.encode(KeyInfo.kcvDes3(scp.sessionEncKey()))).isEqualTo("F2DCDD");
            assertThat(Hex.encode(KeyInfo.kcvDes3(scp.sessionMacKey()))).isEqualTo("5FCC69");
            assertThat(Hex.encode(KeyInfo.kcvDes3(scp.sessionDekKey()))).isEqualTo("85272E");
            assertThat(Hex.encode(KeyInfo.kcvDes3(scp.sessionRmacKey()))).isEqualTo("9F749A");
            assertThat(scp.dek()).isEqualTo(scp.sessionDekKey());
        }

        @Test
        void e_4_1_sessionKeysOfTheRealCardSession() {
            SCP02 scp = realSession(GP.SECURITY_C_MAC);

            assertThat(Hex.encode(scp.sessionEncKey())).isEqualTo("1BA6EBAD460F5396C70E2605726C2CD1");
            assertThat(Hex.encode(scp.sessionMacKey())).isEqualTo("61BAA9D6003C14E7C65A8FF2A4A275A8");
            assertThat(Hex.encode(scp.sessionRmacKey())).isEqualTo("9375AB9D4BDDD16045C4BE271C40F271");
            assertThat(Hex.encode(scp.sessionDekKey())).isEqualTo("1AB1FA470FB34528804854E0EE9323ED");
        }
    }

    @Nested
    class Cryptograms {

        @Test
        void e_4_2_1_cardCryptogramCoversHostChallengeSequenceCounterAndCardChallenge() {
            SCP02 scp = realSession(GP.SECURITY_C_MAC);

            scp.verifyCardCryptogram(REAL.hostChallenge());   // the real card's cryptogram 6273F9DBDB60709F
        }

        @Test
        void e_4_2_1_cryptogramOverTheHostCryptogramOrderIsRejected() {
            // the value computed over sequence counter || card challenge || host challenge (E.4.2.2 order)
            byte[] response = REAL.initUpdate().clone();
            System.arraycopy(Hex.decode("AEFFEAFBE60A4665"), 0, response, 20, 8);
            SCP02 scp = SCP02.from(TEST_KEYS, response, GP.SECURITY_C_MAC, 0x15);

            assertThatThrownBy(() -> scp.verifyCardCryptogram(REAL.hostChallenge()))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("Card cryptogram verification failed");
        }

        @Test
        void e_4_2_1_wrongKeysAreDetectedBeforeExternalAuthenticate() {
            SCPKeys wrong = SCPKeys.fromMasterKey(Hex.decode("404142434445464748494A4B4C4D4E40"));
            SCP02 scp = SCP02.from(wrong, REAL.initUpdate(), GP.SECURITY_C_MAC, 0x15);

            assertThatThrownBy(() -> scp.verifyCardCryptogram(REAL.hostChallenge()))
                    .isInstanceOf(SCPException.class);
            assertThatThrownBy(scp::externalAuthenticate)
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("not verified");
        }

        @Test
        void e_4_2_2_hostCryptogramMatchesTheRealCardSession() {
            assertThat(Hex.encode(realSession(GP.SECURITY_C_MAC).computeHostCryptogram(REAL.hostChallenge())))
                    .isEqualTo("AEFFEAFBE60A4665");
        }
    }

    @Nested
    class ExternalAuthenticate {

        @Test
        void e_5_2_externalAuthenticateMatchesTheRealCardSession() {
            SCP02 scp = realSession(GP.SECURITY_C_MAC);
            scp.verifyCardCryptogram(REAL.hostChallenge());

            assertThat(Hex.encode(scp.externalAuthenticate())).isEqualTo("8482010010AEFFEAFBE60A466523A5B57236491CAC");
        }

        @Test
        void e_5_2_3_externalAuthenticateIsNeverEncryptedEvenWhenCommandsAre() {
            ScpTranscript t = ScpTranscript.named("SCP02_i15_level03_CENC");
            SCP02 scp = SCP02.from(t.keys(), t.initUpdate(), GP.SECURITY_C_MAC_C_ENC, 0x15);
            scp.verifyCardCryptogram(t.hostChallenge());

            byte[] extAuth = scp.externalAuthenticate();

            assertThat(Hex.encode(extAuth)).isEqualTo(Hex.encode(t.extAuth()));
            assertThat(extAuth[4]).as("Lc (Table E-10)").isEqualTo((byte) 0x10);
            assertThat(Hex.encode(extAuth)).startsWith("84820300" + "10"
                    + Hex.encode(scp.computeHostCryptogram(t.hostChallenge())));
        }

        @Test
        void externalAuthenticateCanBeProducedOnlyOnce() {
            SCP02 scp = realSession(GP.SECURITY_C_MAC);
            scp.verifyCardCryptogram(REAL.hostChallenge());
            scp.externalAuthenticate();

            assertThatThrownBy(scp::externalAuthenticate).isInstanceOf(SCPException.class);
        }

        @Test
        void commandsCannotBeWrappedBeforeExternalAuthenticate() {
            SCP02 scp = realSession(GP.SECURITY_C_MAC);
            scp.verifyCardCryptogram(REAL.hostChallenge());

            assertThatThrownBy(() -> scp.wrap(Hex.decode("80F28002024F0000")))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("EXTERNAL AUTHENTICATE must be the first command");
        }

        @Test
        void legacyWrapOfTheExternalAuthenticateCommandGivesTheSameResult() {
            SCP02 scp = realSession(GP.SECURITY_C_MAC);
            scp.verifyCardCryptogram(REAL.hostChallenge());
            byte[] plain = Hex.decode("8482010008" + "AEFFEAFBE60A4665");

            assertThat(Hex.encode(scp.wrap(plain))).isEqualTo(Hex.encode(REAL.extAuth()));
        }
    }

    @Nested
    class CommandMac {

        @Test
        void e_3_4_icvOfTheNextCommandIsEncryptedForOptionI15() {
            SCP02 scp = realSession(GP.SECURITY_C_MAC);
            scp.verifyCardCryptogram(REAL.hostChallenge());
            scp.externalAuthenticate();

            assertThat(Hex.encode(scp.wrap(REAL.commands().getFirst().plain())))
                    .isEqualTo(Hex.encode(REAL.commands().getFirst().wrapped()));
        }

        @Test
        void e_3_4_withoutIcvEncryptionTheRealCardWouldRejectTheCommand() {
            SCP02 scp = SCP02.from(TEST_KEYS, REAL.initUpdate(), GP.SECURITY_C_MAC, 0x05);
            scp.verifyCardCryptogram(REAL.hostChallenge());
            scp.externalAuthenticate();

            assertThat(Hex.encode(scp.wrap(REAL.commands().getFirst().plain())))
                    .isNotEqualTo(Hex.encode(REAL.commands().getFirst().wrapped()));
        }

        @Test
        void e_1_5_levelNoneSendsCommandsWithoutSecureMessaging() {
            SCP02 scp = realSession(GP.SECURITY_NONE);
            scp.verifyCardCryptogram(REAL.hostChallenge());
            assertThat(Hex.encode(scp.externalAuthenticate())).startsWith("84820000");

            assertThat(Hex.encode(scp.wrap(Hex.decode("80CA006600")))).isEqualTo("80CA006600");
        }
    }

    @Nested
    class ResponseMac {

        @Test
        void e_4_5_errorResponsesCarryAnRmacOverLiZero() {
            ScpTranscript t = ScpTranscript.named("SCP02_i75_level11_RMAC");
            SecureChannel scp = ScpChannelKnownAnswerTest.authenticate(t);
            scp.externalAuthenticate();
            ScpTranscript.Command delete = t.commands().stream()
                    .filter(c -> c.sw() == 0x6A88).findFirst().orElseThrow();
            t.commands().stream().takeWhile(c -> c != delete).forEach(c -> {
                scp.wrap(c.plain());
                scp.unwrap(new APDUResponse(c.cardResponse()));
            });

            scp.wrap(delete.plain());
            APDUResponse response = scp.unwrap(new APDUResponse(delete.cardResponse()));

            assertThat(delete.cardResponse()).hasSize(10);   // R-MAC || '6A88'
            assertThat(response.sw()).isEqualTo(0x6A88);
            assertThat(response.data()).isEmpty();
        }

        @Test
        void e_4_5_tamperedResponseIsRejected() {
            ScpTranscript t = ScpTranscript.named("SCP02_i75_level11_RMAC");
            SecureChannel scp = ScpChannelKnownAnswerTest.authenticate(t);
            scp.externalAuthenticate();
            ScpTranscript.Command first = t.commands().getFirst();
            byte[] tampered = first.cardResponse().clone();
            tampered[0] ^= 0x01;

            scp.wrap(first.plain());
            assertThatThrownBy(() -> scp.unwrap(new APDUResponse(tampered)))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("Response MAC verification failed");
        }

        /**
         * The card generates the R-MAC of an error over the stripped command, '00' and the status word and keeps
         * it as the ICV of the next R-MAC "regardless of whether the APDU command completed successfully or not"
         * (E.4.5); ISO/IEC 7816-4:2005 5.1.3 lets it answer only the status word. The host computes the same
         * R-MAC (value of the reviewer's reference: E9767F27FFBB2F92), so the next response verifies.
         */
        @Test
        void e_4_5_bareErrorStatusWordMovesTheRmacChainLikeTheCard() {
            ScpTranscript t = ScpTranscript.named("SCP02_i75_level11_reviewBareError6A88");
            SCP02 scp = (SCP02) ScpChannelKnownAnswerTest.authenticate(t);
            scp.externalAuthenticate();
            ScpTranscript.Command delete = t.commands().get(0);
            ScpTranscript.Command getData = t.commands().get(1);

            scp.wrap(delete.plain());
            APDUResponse error = scp.unwrap(new APDUResponse(Hex.decode("6A88")));
            assertThat(error.sw()).isEqualTo(0x6A88);
            assertThat(error.data()).isEmpty();
            assertThat(Hex.encode(scp.rmacChaining())).isEqualTo("E9767F27FFBB2F92");

            scp.wrap(getData.plain());
            APDUResponse next = scp.unwrap(new APDUResponse(getData.cardResponse()));
            assertThat(Hex.encode(next.data())).isEqualTo("6600");
            assertThat(next.sw()).isEqualTo(0x9000);
        }

        /** An error has no response data (E.4.5: '00'; ISO/IEC 7816-4 5.1.3): only the 8-byte R-MAC may precede it. */
        @ParameterizedTest
        @ValueSource(ints = {1, 7, 9, 16})
        void e_4_5_errorStatusWordWithResponseDataBesidesTheRmacIsRejected(int length) {
            ScpTranscript t = ScpTranscript.named("SCP02_i75_level11_reviewBareError6A88");
            SecureChannel scp = ScpChannelKnownAnswerTest.authenticate(t);
            scp.externalAuthenticate();
            byte[] response = new byte[length + 2];
            response[length] = 0x6A;
            response[length + 1] = (byte) 0x88;

            scp.wrap(t.commands().getFirst().plain());
            assertThatThrownBy(() -> scp.unwrap(new APDUResponse(response)))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("6A88");
        }

        @Test
        void e_4_5_successWithoutRmacIsRejected() {
            ScpTranscript t = ScpTranscript.named("SCP02_i75_level11_reviewBareError6A88");
            SecureChannel scp = ScpChannelKnownAnswerTest.authenticate(t);
            scp.externalAuthenticate();

            scp.wrap(t.commands().get(1).plain());
            assertThatThrownBy(() -> scp.unwrap(new APDUResponse(Hex.decode("9000"))))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("R-MAC");
        }
    }

    @Nested
    class Validation {

        @ParameterizedTest(name = "level {0}")
        @ValueSource(ints = {0x33, 0x31, 0x30, 0x10, 0x02, 0x20})
        void table_e_11_rejectsRfuAndUnsupportedSecurityLevels(int level) {
            assertThatThrownBy(() -> SCP02.from(TEST_KEYS, REAL.initUpdate(), level, 0x75))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("security level");
        }

        @Test
        void table_e_1_rmacLevelsNeedAnOptionWithRmacSupport() {
            assertThatThrownBy(() -> SCP02.from(TEST_KEYS, REAL.initUpdate(), GP.SECURITY_C_MAC_R_MAC, 0x15))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("R-MAC");
        }

        @ParameterizedTest(name = "i={0}")
        @ValueSource(ints = {0x1A, 0x0A, 0x17, 0x95})
        void table_e_1_onlyExplicitInitiationWithCmacOnModifiedApduIsSupported(int option) {
            assertThatThrownBy(() -> SCP02.from(TEST_KEYS, REAL.initUpdate(), GP.SECURITY_C_MAC, option))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("not supported");
        }

        @Test
        void table_e_1_singleBaseKeyOptionNeedsOneKey() {
            SCPKeys three = SCPKeys.of(Hex.decode("404142434445464748494A4B4C4D4E4F"),
                    Hex.decode("505152535455565758595A5B5C5D5E5F"), Hex.decode("606162636465666768696A6B6C6D6E6F"));

            assertThatThrownBy(() -> SCP02.from(three, REAL.initUpdate(), GP.SECURITY_C_MAC, 0x14))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("base key");
        }

        @Test
        void table_e_3_keysMustBeDoubleLengthDes() {
            SCPKeys aes256 = SCPKeys.fromMasterKey(new byte[32]);
            SCPKeys aes128 = SCPKeys.aes(new byte[16], new byte[16], new byte[16]);

            assertThatThrownBy(() -> SCP02.from(aes256, REAL.initUpdate()))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("16-byte");
            assertThatThrownBy(() -> SCP02.from(aes128, REAL.initUpdate()))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("AES");
        }

        @ParameterizedTest(name = "{0} bytes")
        @ValueSource(ints = {20, 27, 29, 32})
        void table_e_8_responseMustBe28Bytes(int length) {
            assertThatThrownBy(() -> SCP02.from(TEST_KEYS, new byte[length]))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("28 bytes");
        }

        @Test
        void e_5_1_5_hostChallengeMustBe8Bytes() {
            SCP02 scp = realSession(GP.SECURITY_C_MAC);

            assertThatThrownBy(() -> scp.verifyCardCryptogram(new byte[16])).isInstanceOf(SCPException.class);
        }
    }

    @Nested
    class Lengths {

        @Test
        void gpcs_11_1_5_cmacLeavesRoomFor247DataBytes() {
            SCP02 scp = openedRealSession(GP.SECURITY_C_MAC);

            assertThat(scp.maxCommandDataLength()).isEqualTo(247);
            assertThat(scp.wrap(apdu(247))).hasSize(5 + 255);
            assertThatThrownBy(() -> scp.wrap(apdu(248)))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("247");
        }

        @Test
        void e_4_6_encryptionPaddingLeavesRoomFor239DataBytes() {
            ScpTranscript t = ScpTranscript.named("SCP02_i15_level03_CENC");
            SecureChannel scp = ScpChannelKnownAnswerTest.authenticate(t);
            scp.externalAuthenticate();

            assertThat(scp.maxCommandDataLength()).isEqualTo(239);
            assertThat(scp.wrap(apdu(239))).hasSize(5 + 248);
            assertThatThrownBy(() -> scp.wrap(apdu(240))).isInstanceOf(SCPException.class);
        }

        @Test
        void gpcs_11_1_5_extendedLengthApdusAreRejectedInsteadOfTruncated() {
            SCP02 scp = openedRealSession(GP.SECURITY_C_MAC);
            byte[] extended = new byte[7 + 300];
            System.arraycopy(Hex.decode("80E2800000012C"), 0, extended, 0, 7);

            assertThatThrownBy(() -> scp.wrap(extended))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("Extended-length");
        }

        @Test
        void malformedLcIsRejected() {
            SCP02 scp = openedRealSession(GP.SECURITY_C_MAC);

            assertThatThrownBy(() -> scp.wrap(Hex.decode("80E2800005010203")))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("Malformed");
        }

        private static SCP02 openedRealSession(int level) {
            SCP02 scp = realSession(level);
            scp.verifyCardCryptogram(REAL.hostChallenge());
            scp.externalAuthenticate();
            return scp;
        }

        private static byte[] apdu(int dataLength) {
            byte[] apdu = new byte[5 + dataLength];
            System.arraycopy(Hex.decode("80E28000"), 0, apdu, 0, 4);
            apdu[4] = (byte) dataLength;
            return apdu;
        }
    }

    @Test
    void destroyZeroizesTheSessionKeysAndDisablesTheChannel() {
        SCP02 scp = realSession(GP.SECURITY_C_MAC);
        scp.destroy();

        assertThat(scp.sessionMacKey()).containsOnly(0);
        assertThat(scp.sessionEncKey()).containsOnly(0);
        assertThat(scp.dek()).containsOnly(0);
        assertThatThrownBy(() -> scp.verifyCardCryptogram(REAL.hostChallenge())).isInstanceOf(SCPException.class);
        assertThatThrownBy(() -> scp.wrap(Hex.decode("80CA006600"))).isInstanceOf(SCPException.class);
    }
}
