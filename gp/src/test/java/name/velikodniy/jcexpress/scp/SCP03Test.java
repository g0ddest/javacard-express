package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.gp.ScpTranscript;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link SCP03} (GlobalPlatform Card Specification Amendment D v1.1.2; S16 mode of v1.2).
 *
 * <p>Expected values come from outside javacard-express: the real NXP JCOP4 transcript
 * {@code SCP03_real_JCOP4_i70}, the Samsung OpenSCP-Java transcripts (Apache-2.0, data only) and
 * spec-derived vectors of the independent reference implementation (see {@link ScpTranscript}).</p>
 */
class SCP03Test {

    private static final ScpTranscript JCOP4 = ScpTranscript.named("SCP03_real_JCOP4_i70");
    private static final ScpTranscript SAMSUNG_128 = ScpTranscript.named("SCP03_Samsung_AES128_S8_level33");

    private static SCP03 jcop4(int level) {
        return SCP03.from(JCOP4.keys(), JCOP4.hostChallenge(), JCOP4.initUpdate(), level);
    }

    private static SecureChannel opened(ScpTranscript t) {
        SecureChannel channel = ScpChannelKnownAnswerTest.authenticate(t);
        channel.externalAuthenticate();
        return channel;
    }

    @Nested
    class InitializeUpdateResponseLayout {

        @Test
        void table_7_3_keyInformationIsThreeBytesFollowedByChallengeCryptogramAndCounter() {
            InitializeUpdateResponse r = InitializeUpdateResponse.parse(JCOP4.initUpdate());

            assertThat(Hex.encode(r.diversificationData())).isEqualTo("00003244342976208448");
            assertThat(r.keyVersion()).isEqualTo(0x01);
            assertThat(r.scpIdentifier()).isEqualTo(3);
            assertThat(r.option()).isEqualTo(0x70);
            assertThat(Hex.encode(r.cardChallenge())).isEqualTo("734ECDCA19E446A3");
            assertThat(Hex.encode(r.cardCryptogram())).isEqualTo("0BC253BCE97DB991");
            assertThat(Hex.encode(r.sequenceCounter())).isEqualTo("000436");
            assertThat(r.s16()).isFalse();
        }

        @Test
        void table_7_3_randomChallengeResponseHasNoSequenceCounter() {
            ScpTranscript t = ScpTranscript.named("SCP03_AES128_i00_level01");
            InitializeUpdateResponse r = InitializeUpdateResponse.parse(t.initUpdate());

            assertThat(t.initUpdate()).hasSize(29);
            assertThat(r.option()).isZero();
            assertThat(r.sequenceCounter()).isEmpty();
        }

        @Test
        void amdD_v1_2_s16ResponseCarries16ByteChallengeAndCryptogram() {
            ScpTranscript t = ScpTranscript.named("SCP03_Samsung_AES128_S16_level33");
            InitializeUpdateResponse r = InitializeUpdateResponse.parse(t.initUpdate());

            assertThat(r.s16()).isTrue();
            assertThat(r.option()).isEqualTo(0x71);
            assertThat(r.cardChallenge()).hasSize(16);
            assertThat(r.cardCryptogram()).hasSize(16);
            assertThat(Hex.encode(r.sequenceCounter())).isEqualTo("00082A");
        }

        @ParameterizedTest(name = "i={0}, {1} bytes")
        @CsvSource({"00, 32", "10, 29", "70, 29", "60, 32", "71, 32"})
        void table_7_3_lengthMustMatchTheIParameter(String option, int length) {
            byte[] response = new byte[length];
            response[11] = 0x03;
            response[12] = (byte) Integer.parseInt(option, 16);

            assertThatThrownBy(() -> InitializeUpdateResponse.parse(response))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("Table 7-3");
        }

        @Test
        void responseOfAnotherProtocolIsRejected() {
            byte[] response = JCOP4.initUpdate().clone();
            response[11] = 0x01;

            assertThatThrownBy(() -> InitializeUpdateResponse.parse(response))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("Unsupported Secure Channel Protocol '01'");
        }
    }

    @Nested
    class Cryptograms {

        @Test
        void amdD_6_2_2_2_cardCryptogramIsDerivedFromSessionMacKey() {
            SCP03 scp = jcop4(GP.SECURITY_C_MAC);   // verifies the real card cryptogram 0BC253BCE97DB991

            assertThat(Hex.encode(scp.sessionMacKey())).isEqualTo("F7062FB6554BB7B140A7336658C4178E");
            assertThat(Hex.encode(scp.sessionEncKey())).isEqualTo("72790CA8EDDD33FF607B2EA804E25344");
            assertThat(Hex.encode(scp.sessionRmacKey())).isEqualTo("D6E86D640A74CAF2DAB8894C64A358E8");
        }

        @Test
        void amdD_6_2_2_2_cryptogramDerivedFromTheStaticMacKeyIsRejected() {
            byte[] response = JCOP4.initUpdate().clone();
            System.arraycopy(Hex.decode("4FF696EF07718BEF"), 0, response, 21, 8);

            assertThatThrownBy(() -> SCP03.from(JCOP4.keys(), JCOP4.hostChallenge(), response, GP.SECURITY_C_MAC))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("Card cryptogram verification failed");
        }

        @Test
        void amdD_6_2_2_3_hostCryptogramMatchesTheRealCard() {
            assertThat(Hex.encode(jcop4(GP.SECURITY_C_MAC).hostCryptogram())).isEqualTo("A5E66CD1A836E3A4");
        }

        @Test
        void amdD_6_2_1_aes192SessionKeysAre24Bytes() {
            ScpTranscript t = ScpTranscript.named("SCP03_Samsung_AES192_S8_level33");
            SCP03 scp = SCP03.from(t.keys(), t.hostChallenge(), t.initUpdate(), t.level());

            assertThat(scp.sessionEncKey()).hasSize(24);
            assertThat(scp.sessionMacKey()).hasSize(24);
            assertThat(scp.sessionRmacKey()).hasSize(24);
        }
    }

    @Nested
    class ExternalAuthenticate {

        @Test
        void table_7_5_externalAuthenticateMatchesTheRealCard() {
            assertThat(Hex.encode(jcop4(GP.SECURITY_C_MAC).externalAuthenticate()))
                    .isEqualTo("8482010010A5E66CD1A836E3A47CD3B3F7B689AE8F");
        }

        @Test
        void table_7_5_externalAuthenticateIsNeverEncryptedEvenAtLevel33() {
            SCP03 scp = SCP03.from(SAMSUNG_128.keys(), SAMSUNG_128.hostChallenge(), SAMSUNG_128.initUpdate(), 0x33);

            byte[] extAuth = scp.externalAuthenticate();

            assertThat(Hex.encode(extAuth)).isEqualTo(Hex.encode(SAMSUNG_128.extAuth()));
            assertThat(Hex.encode(extAuth)).startsWith("8482330010" + Hex.encode(scp.hostCryptogram()));
        }

        @Test
        void externalAuthenticateCanBeProducedOnlyOnceAndMustComeFirst() {
            SCP03 scp = jcop4(GP.SECURITY_C_MAC);
            assertThatThrownBy(() -> scp.wrap(Hex.decode("80CA006600")))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("first command");

            scp.externalAuthenticate();
            assertThatThrownBy(scp::externalAuthenticate).isInstanceOf(SCPException.class);
        }
    }

    @Nested
    class SecureMessaging {

        @Test
        void amdD_6_2_6_encryptionCounterAdvancesForCommandsWithoutData() {
            ScpTranscript t = ScpTranscript.named("SCP03_AES128_i10_level03");
            SecureChannel scp = opened(t);
            ScpTranscript.Command getStatus = t.commands().get(0);
            ScpTranscript.Command getData = t.commands().get(1);    // case 2, no data field
            ScpTranscript.Command storeData = t.commands().get(2);  // encrypted with counter 3

            scp.wrap(getStatus.plain());
            assertThat(Hex.encode(scp.wrap(getData.plain()))).isEqualTo(Hex.encode(getData.wrapped()));
            assertThat(Hex.encode(scp.wrap(storeData.plain()))).isEqualTo(Hex.encode(storeData.wrapped()));
        }

        @Test
        void amdD_6_2_5_rmacIsComputedOverTheCommandMacChainingValue() {
            SCP03 scp = (SCP03) opened(SAMSUNG_128);
            ScpTranscript.Command first = SAMSUNG_128.commands().getFirst();
            scp.wrap(first.plain());
            byte[] chaining = scp.macChaining();

            APDUResponse response = scp.unwrap(new APDUResponse(first.cardResponse()));

            assertThat(Hex.encode(response.data())).isEqualTo(Hex.encode(first.data()));
            assertThat(scp.macChaining()).as("the response does not change the chaining value").isEqualTo(chaining);
        }

        @Test
        void amdD_6_2_7_responseIcvIsTheEncryptedCounterBlockWithMsb80() {
            SecureChannel scp = opened(SAMSUNG_128);
            for (ScpTranscript.Command command : SAMSUNG_128.commands()) {
                scp.wrap(command.plain());
                APDUResponse response = scp.unwrap(new APDUResponse(command.cardResponse()));
                assertThat(Hex.encode(response.data())).as("decrypted %s", command)
                        .isEqualTo(Hex.encode(command.data()));
            }
        }

        @Test
        void amdD_6_2_5_errorStatusWordsCarryNoRmacAndAreReturnedUnchanged() {
            SecureChannel scp = opened(ScpTranscript.named("SCP03_AES128_i60_level33"));

            scp.wrap(Hex.decode("80E40000094F07A000000001020300"));
            APDUResponse response = scp.unwrap(new APDUResponse(Hex.decode("6A88")));

            assertThat(response.sw()).isEqualTo(0x6A88);
            assertThat(response.data()).isEmpty();
        }

        /**
         * Unlike SCP02, SCP03 has no R-MAC chain: the R-MAC of a response is computed over the MAC chaining value
         * of its command (6.2.5, Figure 6-3) and errors carry none, while the encryption counter counts every
         * command (6.2.6). A bare error therefore leaves nothing to resynchronise: the next command and its
         * encrypted response match the reference card.
         */
        @Test
        void amdD_6_2_5_errorStatusWordLeavesChainingValueAndCounterToTheNextCommand() {
            ScpTranscript t = ScpTranscript.named("SCP03_AES128_i70_level33_errorSW");
            SCP03 scp = (SCP03) opened(t);
            ScpTranscript.Command getStatus = t.commands().get(0);
            ScpTranscript.Command delete = t.commands().get(1);
            ScpTranscript.Command getData = t.commands().get(2);
            scp.wrap(getStatus.plain());
            scp.unwrap(new APDUResponse(getStatus.cardResponse()));

            scp.wrap(delete.plain());
            byte[] chaining = scp.macChaining();
            APDUResponse error = scp.unwrap(new APDUResponse(delete.cardResponse()));

            assertThat(Hex.encode(delete.cardResponse())).isEqualTo("6A88");
            assertThat(error.sw()).isEqualTo(0x6A88);
            assertThat(scp.macChaining()).isEqualTo(chaining);
            assertThat(Hex.encode(scp.wrap(getData.plain()))).isEqualTo(Hex.encode(getData.wrapped()));
            assertThat(Hex.encode(scp.unwrap(new APDUResponse(getData.cardResponse())).data()))
                    .isEqualTo(Hex.encode(getData.data()));
        }

        /**
         * "No R-MAC shall be generated and no protection shall be applied to a response that includes an error
         * status word: in this case only the status word shall be returned" (6.2.5): response data next to an
         * error status word would reach the caller unauthenticated in a session that requires R-MAC.
         */
        @ParameterizedTest
        @ValueSource(strings = {"01020304050607086A88", "016985", "000102030405060708090A0B0C0D0E0F6F00"})
        void amdD_6_2_5_errorStatusWordWithResponseDataIsRejectedWhenResponsesAreProtected(String response) {
            SecureChannel scp = opened(ScpTranscript.named("SCP03_AES128_i60_level33"));

            scp.wrap(Hex.decode("80E40000094F07A000000001020300"));
            assertThatThrownBy(() -> scp.unwrap(new APDUResponse(Hex.decode(response))))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining(response.substring(response.length() - 4));
        }

        @Test
        void amdD_6_2_5_withoutRmacResponsesAreNotInterpreted() {
            SecureChannel scp = opened(ScpTranscript.named("SCP03_AES128_i10_level03"));

            scp.wrap(Hex.decode("80E40000094F07A000000001020300"));
            APDUResponse response = scp.unwrap(new APDUResponse(Hex.decode("01026A88")));

            assertThat(Hex.encode(response.data())).isEqualTo("0102");
            assertThat(response.sw()).isEqualTo(0x6A88);
        }

        @Test
        void amdD_6_2_5_successWithoutRmacIsRejected() {
            SecureChannel scp = opened(ScpTranscript.named("SCP03_AES128_i60_level33"));

            scp.wrap(Hex.decode("80CA006600"));
            assertThatThrownBy(() -> scp.unwrap(new APDUResponse(Hex.decode("9000"))))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("R-MAC");
        }

        @Test
        void amdD_6_2_5_tamperedResponseIsRejected() {
            SecureChannel scp = opened(SAMSUNG_128);
            ScpTranscript.Command first = SAMSUNG_128.commands().getFirst();
            byte[] tampered = first.cardResponse().clone();
            tampered[3] ^= 0x40;

            scp.wrap(first.plain());
            assertThatThrownBy(() -> scp.unwrap(new APDUResponse(tampered)))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("Response MAC verification failed");
        }
    }

    @Nested
    class Validation {

        @ParameterizedTest(name = "level {0} with i={1}")
        @CsvSource({"11, 00", "13, 00", "33, 20", "33, 00"})
        void table_5_1_responseProtectionNeedsCardSupport(String level, String option) {
            ScpTranscript t = ScpTranscript.named("SCP03_AES128_i00_level01");
            byte[] response = t.initUpdate().clone();
            response[12] = (byte) Integer.parseInt(option, 16);
            int securityLevel = Integer.parseInt(level, 16);

            assertThatThrownBy(() -> SCP03.from(t.keys(), t.hostChallenge(), response, securityLevel))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("support");
        }

        @ParameterizedTest(name = "level {0}")
        @ValueSource(ints = {0x02, 0x10, 0x30, 0x31, 0x23})
        void table_7_6_rejectsUndefinedSecurityLevels(int level) {
            assertThatThrownBy(() -> jcop4(level))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("not defined");
        }

        @Test
        void table_5_1_rejectsRfuBitsInTheIParameter() {
            byte[] response = JCOP4.initUpdate().clone();
            response[12] = 0x72;

            assertThatThrownBy(() -> SCP03.from(JCOP4.keys(), JCOP4.hostChallenge(), response, GP.SECURITY_C_MAC))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("RFU");
        }

        @Test
        void amdD_v1_2_s16CardsNeedA16ByteHostChallenge() {
            ScpTranscript t = ScpTranscript.named("SCP03_Samsung_AES128_S16_level33");

            assertThatThrownBy(() -> SCP03.from(t.keys(), new byte[8], t.initUpdate(), t.level()))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("16-byte host challenge");
        }

        @Test
        void table_6_1_keysTypedAsTripleDesAreRejected() {
            SCPKeys des = SCPKeys.des3(new byte[16], new byte[16], new byte[16]);

            assertThatThrownBy(() -> SCP03.from(des, JCOP4.hostChallenge(), JCOP4.initUpdate(), 0x01))
                    .isInstanceOf(SCPException.class)
                    .hasMessageContaining("AES");
        }
    }

    @Nested
    class Lengths {

        @ParameterizedTest(name = "{0}")
        @CsvSource({
                "SCP03_AES128_i00_level01, 247",
                "SCP03_AES128_i10_level03, 239",
                "SCP03_AES256_S16_i31_level11, 239",
                "SCP03_AES192_S16_i61_level03, 223"})
        void gpcs_11_1_5_maximumClearDataPerCommand(String transcript, int max) {
            SecureChannel scp = opened(ScpTranscript.named(transcript));

            assertThat(scp.maxCommandDataLength()).isEqualTo(max);
            byte[] tooLong = new byte[5 + max + 1];
            System.arraycopy(Hex.decode("80E28000"), 0, tooLong, 0, 4);
            tooLong[4] = (byte) (max + 1);
            assertThatThrownBy(() -> scp.wrap(tooLong)).isInstanceOf(SCPException.class);
        }
    }

    @Test
    void destroyZeroizesKeysIncludingTheStaticDek() {
        SCP03 scp = jcop4(GP.SECURITY_C_MAC);
        scp.destroy();

        assertThat(scp.sessionMacKey()).containsOnly(0);
        assertThat(scp.dek()).containsOnly(0);
        assertThatThrownBy(scp::externalAuthenticate).isInstanceOf(SCPException.class);
    }
}
