package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.sm.SMAlgorithm;
import name.velikodniy.jcexpress.sm.SMSession;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The code examples of {@code pace/README.md} (and the BAC/PACE examples of {@code sm/README.md}), compiled and run
 * against the ICAO worked examples and the chip simulators. The only additions to the README code are the
 * package-private seams that fix the terminal's random values to those of the worked examples. Keep both in sync.
 */
class ReadmeExamplesTest {

    /** SELECT of the eMRTD application under the App. G.1 session keys (AES SM, SSC 1 and 2). */
    private static final String SELECT_EMRTD = "0CA4040C1D871101752F676B09FAC86A87D632749A49C7CC8E08C18BA1FCE707BD9F00";
    private static final String SELECT_EMRTD_RESPONSE = "990290008E08BEA7B381C494A0799000";

    @Test
    void pace() {
        ScriptedCard card = IcaoAppendixG1PaceTest.transcript("0022C1A412800A04007F0007020204020283010184010D")
                .expect(SELECT_EMRTD, SELECT_EMRTD_RESPONSE);

        PaceResult result = PaceSession.builder()
                .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
                .parameterId(PaceParameterId.BRAINPOOL_P256R1)
                .mrzPassword("T22000129", "640812", "101031")   // document number, date of birth, date of expiry
                .ephemeralKeySource(IcaoAppendixG1PaceTest.terminalKeys())
                .build()
                .perform(card);

        SMSession secure = result.toSMSession(card);              // AES Secure Messaging, SSC = 0
        APDUResponse select = secure.send(0x00, 0xA4, 0x04, 0x0C, Hex.decode("A0000002471001"));

        assertThat(select.sw()).isEqualTo(0x9000);
        assertThat(card.isFinished()).isTrue();
    }

    @Test
    void canPassword() {
        byte[] kPi = PaceMrz.kdf("123456".getBytes(ISO_8859_1), 3, 16);
        IcaoPaceChipSimulator card = IcaoPaceChipSimulator.random(PaceParameterId.BRAINPOOL_P256R1,
                PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, PasswordRef.CAN, kPi);

        SMSession eid = PaceSession.builder()
                .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
                .parameterId(PaceParameterId.BRAINPOOL_P256R1)
                .canPassword("123456")
                .build()
                .perform(card)
                .toSMSession(card);                       // AES, SSC = 0

        assertThat(card.authenticated).isTrue();
        assertThat(eid.context().algorithm()).isEqualTo(SMAlgorithm.AES);
        assertThat(eid.context().ssc()).isEqualTo(new byte[16]);
    }

    @Test
    void basicAccessControl() {
        ScriptedCard card = IcaoAppendixDBacTest.appendixD3()
                .expect("0CA4020C158709016375432908C044F68E08BF8B92D635FF24F800", "990290008E08FA855A5D4C50A8ED9000");

        BacResult bac = BacSession.builder()
                .mrz("L898902C", "690806", "940623")      // document number, date of birth, date of expiry
                .randomSource(IcaoAppendixDBacTest.icaoRandom())
                .build()
                .perform(card);                           // GET CHALLENGE + EXTERNAL AUTHENTICATE (4.3.4)

        SMSession passport = bac.toSMSession(card);       // 3DES Secure Messaging, SSC from the nonces
        APDUResponse select = passport.send(0x00, 0xA4, 0x02, 0x0C, Hex.decode("011E"));    // SELECT EF.COM

        assertThat(select.sw()).isEqualTo(0x9000);
        assertThat(card.isFinished()).isTrue();
    }

    @Test
    void mrzHelpers() {
        byte[] k = PaceMrz.encodeMrzPassword("T22000129", "640812", "101031");

        assertThat(PaceMrz.mrzInformation("L898902C", "690806", "940623")).isEqualTo("L898902C<369080619406236");
        assertThat(PaceMrz.checkDigit("690806")).isEqualTo(1);
        assertThat(Hex.encode(k)).startsWith("7E2D2A41").endsWith("E9032AAD");
        assertThat(Hex.encode(PaceMrz.bacKeySeed("L898902C", "690806", "940623")))
                .isEqualTo("239AB9CB282DAF66231DC5A4DF6BFBAE");
        assertThat(Hex.encode(PaceMrz.kdf(k, 3, 16))).isEqualTo("89DED1B26624EC1E634C1989302849DD");
        assertThat(PaceParameterId.fromId(13)).isEqualTo(PaceParameterId.BRAINPOOL_P256R1);
    }
}
