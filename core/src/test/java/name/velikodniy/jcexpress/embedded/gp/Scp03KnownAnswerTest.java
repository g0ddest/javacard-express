package name.velikodniy.jcexpress.embedded.gp;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The card side of SCP03 against public transcripts that this project did not produce: what a real card and an
 * independent emulation answered and accepted. The sessions replay them with the card challenge of the transcript
 * (GlobalPlatform Card Specification v2.3 Amendment D v1.1.2).
 */
class Scp03KnownAnswerTest {

    private static final AID ISD = SecurityDomain.ISSUER_SECURITY_DOMAIN;
    /**
     * Marks a response the emulation encrypted with a whole block of padding more than the padding rule adds
     * (its plain text is the data, '80' and 20 zero bytes): a card pads to the next block boundary (GPCS v2.3.1
     * B.2.3), so only the command of that exchange is compared.
     */
    private static final String OVER_PADDED = "";

    /**
     * A real NXP JCOP 4 with i = '70' (GlobalPlatformPro test dump, as published in
     * github.com/PeculiarVentures/scp; the case SCP03_real_JCOP4_i70 of the gp module's
     * scp-public-transcripts.txt): key 404142...4F, host challenge 0102030405060708. The card answered with a
     * pseudo-random challenge after sequence counter '000436'; the application that used the secure channel was
     * the Issuer Security Domain itself.
     */
    @Test
    void aRealCardsPseudoRandomChallengeAndCardCryptogramAreReproduced_amdD_6_2_2_1_and_6_2_2_2() {
        SecurityDomain domain = SecurityDomain.withTestKeys().keyVersion(0x01)
                .scp03Option(SecurityDomain.OPTION_PSEUDO_RANDOM_CHALLENGE | SecurityDomain.OPTION_R_MAC_R_ENCRYPTION)
                .keyDiversificationData(Hex.decode("00003244342976208448"));
        domain.sequenceCounter(0x000435);
        byte[] buffer = Arrays.copyOf(Hex.decode("80500000080102030405060708"), 64);

        short length = domain.session().process(ISD, buffer, 0, 13, 5);

        assertThat(Hex.encode(Arrays.copyOfRange(buffer, 5, 5 + length)))
                .isEqualTo("00003244342976208448010370734ECDCA19E446A30BC253BCE97DB991000436");
        assertThat(domain.sequenceCounter()).isEqualTo(0x000436);
    }

    /** The EXTERNAL AUTHENTICATE that the same real card accepted, at the security level C-MAC. */
    @Test
    void theExternalAuthenticateARealCardAcceptedIsAccepted_amdD_6_2_2_3_and_6_2_3() {
        SecurityDomain domain = SecurityDomain.withTestKeys().keyVersion(0x01)
                .scp03Option(SecurityDomain.OPTION_PSEUDO_RANDOM_CHALLENGE | SecurityDomain.OPTION_R_MAC_R_ENCRYPTION);
        domain.sequenceCounter(0x000435);
        domain.session().process(ISD, Arrays.copyOf(Hex.decode("80500000080102030405060708"), 64), 0, 13, 5);
        byte[] externalAuthenticate = Hex.decode("8482010010A5E66CD1A836E3A47CD3B3F7B689AE8F");

        short length = domain.session().process(ISD, externalAuthenticate, 0, externalAuthenticate.length, 5);

        assertThat(length).isZero();
        assertThat(domain.securityLevel()).isEqualTo(0x81);
        assertThat(domain.failedAuthentications()).isZero();
    }

    /**
     * The Samsung OpenSCP-Java emulation transcripts for AES-128, AES-192 and AES-256 with 8-byte challenges at the
     * security level '33' (SmartCardScp03Aes128S8ModeEmulation and its siblings, Apache-2.0; the cases
     * SCP03_Samsung_AES*_S8_level33 of the gp module's scp-public-transcripts.txt). Keys, challenges, the card
     * cryptogram, EXTERNAL AUTHENTICATE, the protected commands and the protected responses are verbatim from
     * the transcript; the plain commands and responses are what the transcript's reference recovered.
     */
    record Transcript(String name, String keyEnc, String keyMac, String keyDek, String cardCryptogram,
                      String externalAuthenticate, String[][] exchanges) {
        @Override
        public String toString() {
            return name;
        }
    }

    static Stream<Transcript> samsungTranscripts() {
        return Stream.of(
                new Transcript("AES-128", "1D72CD9283FD55162722C6BEAA4DC187", "F4932BA02FFC3098D172790099D28382",
                        "B4BDC610C3F6793708FF1132E2C5BF60", "DC2DBE8974C8B0DE",
                        "8482330010B08D6CE26B6CB3CCB411CF0296EB7B1D", new String[][]{
                            {"84F22000185230BA64388B4A40E0B4DA5CC1DF51C285E4020D99D5AED1", "80F22000024F00",
                                "08A00000015141434C010010A0000002202003010101000000000006010010A00000022020030101"
                                    + "01000000000011010005A0000002480100",
                                "BD3292BFB1A23C4478E37292BA1EDF438770CE472FB7611FBDBD1C981A27FA4780A81A95D93C05F9"
                                    + "C4C94839DED0363CFEA57CE2ECFB572B26F3474DAEEBBABC202942381F9755F5"},
                            {"84F24000181819D47B42BBE6B9449BBC2BD43A090DAC1F2F0A52D9F34B", "80F24000024F00",
                                "0AA9A8A7A6A5A4A3A2A1A00F800AA0A1A2A3A4A5A6A7A8A9070009A00000015141434C00070010A0"
                                    + "000002202003010301000000000011070007A00000024804000700",
                                "BB82442BB5CC8C839620615D1F163D3DDBC9357D68EF4BAD997CFBB79A24C224A89488C44B25C3B2"
                                    + "3D489E4E58A309D438FDD6E453D0E07216541FB142B977A3A7D4C4048BBE2BA068F04A0A4A9C50BA"
                                    + "D232F8CA8EA1F40E"},
                            {"84F280001809F07C3DF47956B1052951FA28211BA7BABC05C321D9B3BF", "80F28000024F00",
                                "08A0000001510000000F9E", OVER_PADDED}}),
                new Transcript("AES-192", "1D72CD9283FD55162722C6BEAA4DC1877F4C0CD0ECC15E05",
                        "F4932BA02FFC3098D172790099D2838236F2E61068D56F44",
                        "B4BDC610C3F6793708FF1132E2C5BF60523AEAC06B32F204", "6E7C64F962A822A4",
                        "848233001063B6CEFAC0EC098333860788C65220BA", new String[][]{
                            {"84F2200018D9EDCBCB7F69CB1EF0508E6EDE933A6D80091E7D99CB3E51", "80F22000024F00",
                                "08A00000015141434C010010A0000002202003010101000000000006010010A00000022020030101"
                                    + "01000000000011010005A0000002480100",
                                "99077B167D43A4F313B59B63CC23EFD3B5158BDEF8F24D85E250570A4AAB81869A92307350267F0F"
                                    + "BC2278FA3D34D2FD5D2B4E8C0362C01D082C76A17B80AEA4BA5FB9D7DA3BB368"},
                            {"84F2400018CDC4B0480CF151C1132655133115A8CA1A89964F2554551C", "80F24000024F00",
                                "0AA9A8A7A6A5A4A3A2A1A00F800AA0A1A2A3A4A5A6A7A8A9070009A00000015141434C00070010A0"
                                    + "000002202003010301000000000011070007A00000024804000700",
                                "AB7A97B6C673DF3D95378D06B7B42E25D7C3B22D6D1A42299FFED17F5973950EC68C77700FC01947"
                                    + "067470178A1D06152ED648E95E8C3510B61CF0036DFD8C9F6FA167D32FDEB3F81A0E6B2BB35BCD4C"
                                    + "D104692D131D7776"},
                            {"84F280001896800333FA638A32DBCCBF4C7E52FBD5DA469A954E1D58F6", "80F28000024F00",
                                "08A0000001510000000F9E", OVER_PADDED}}),
                new Transcript("AES-256", "1D72CD9283FD55162722C6BEAA4DC1877F4C0CD0ECC15E052AAC39A99AF9AD72",
                        "F4932BA02FFC3098D172790099D2838236F2E61068D56F4401CC0374C25AF8CB",
                        "B4BDC610C3F6793708FF1132E2C5BF60523AEAC06B32F204B851B6CC007C8D3C", "8AFA7267CB63740E",
                        "848233001050E003735F92228269A094FFC07429FD", new String[][]{
                            {"84F2200018BD57D1382AE8F66F7EB5F5991B92D1399044157C7DFD2761", "80F22000024F00",
                                "08A00000015141434C010010A0000002202003010101000000000006010010A00000022020030101"
                                    + "01000000000011010005A0000002480100",
                                "47D81041004B7E9208E3BEF1372E7CDE8CD995AEF207F138C80D45156F2D36F2B15BDC9C4D6FDB97"
                                    + "74344495CCC83AE7BA0B39C734BF9CEBD07204AA5A67DF2D7E663D55FC4944C0"},
                            {"84F240001845D2B475C3EFF8BBB254D0B6A8E6CA97CF5265D907A76070", "80F24000024F00",
                                "0AA9A8A7A6A5A4A3A2A1A00F800AA0A1A2A3A4A5A6A7A8A9070009A00000015141434C00070010A0"
                                    + "000002202003010301000000000011070007A00000024804000700",
                                "59E8DCFDC22D436336552128F790E1B383D6942ED4025F30FE8D95541E634E238BFD963D88DF822D"
                                    + "8EBCC1272A9D56C7D1CBC306039647FC4977EFF562C0B8C01314B1C8B2D168A581A98C65B676B3EE"
                                    + "4032E91A9C0858EC"},
                            {"84F28000187083CA3ADA0F76CF7B3FFAC60ABE13599D521EE7B9224C49", "80F28000024F00",
                                "08A0000001510000000F9E",
                                "E58CB33A46F76909ADDDFF0C2821F4F25F22B5553C534DC6"}}));
    }

    @ParameterizedTest
    @MethodSource("samsungTranscripts")
    void aPublicLevel33SessionIsReproduced_amdD_6_2(Transcript transcript) {
        SecurityDomain domain = SecurityDomain.withKeys(Hex.decode(transcript.keyEnc()),
                Hex.decode(transcript.keyMac()), Hex.decode(transcript.keyDek())).keyVersion(0x30)
                .scp03Option(SecurityDomain.OPTION_R_MAC_R_ENCRYPTION)
                .keyDiversificationData(Hex.decode("A1A01243058551312085"));
        domain.challenges(() -> Hex.decode("9CE033FA78E6B10D"));
        Scp03Session session = domain.session();
        byte[] buffer = Arrays.copyOf(Hex.decode("805030000806F85B77251BF794"), 64);

        short length = session.process(ISD, buffer, 0, 13, 5);

        // key diversification data, key information (KVN, SCP 03, i), card challenge, card cryptogram (Table 7-3)
        assertThat(Hex.encode(Arrays.copyOfRange(buffer, 5, 5 + length)))
                .isEqualTo("A1A01243058551312085" + "300360" + "9CE033FA78E6B10D" + transcript.cardCryptogram());
        byte[] externalAuthenticate = Hex.decode(transcript.externalAuthenticate());
        assertThat(session.process(ISD, externalAuthenticate, 0, externalAuthenticate.length, 5)).isZero();
        assertThat(session.currentLevel()).isEqualTo(0xB3);

        for (String[] exchange : transcript.exchanges()) {
            byte[] command = Arrays.copyOf(Hex.decode(exchange[0]), 300);
            int wrappedLength = Hex.decode(exchange[0]).length;
            // 6.2.4 and 6.2.6: C-MAC verified, data decrypted: the plain command of the transcript (header, Lc, data)
            short plainLength = session.unwrap(ISD, command, 0, wrappedLength);
            assertThat(Hex.encode(Arrays.copyOf(command, plainLength))).isEqualTo(exchange[1]);

            // 6.2.5 and 6.2.7: the response data encrypted, then the R-MAC in place of the status bytes
            byte[] plainResponse = Hex.decode(exchange[2] + "9000");
            byte[] response = Arrays.copyOf(plainResponse, 300);
            short protectedLength = session.wrap(ISD, response, 0, plainResponse.length);
            if (!exchange[3].equals(OVER_PADDED)) {
                assertThat(Hex.encode(Arrays.copyOf(response, protectedLength))).isEqualTo(exchange[3]);
            }
        }
    }
}
