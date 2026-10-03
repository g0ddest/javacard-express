package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.sm.SMAlgorithm;
import name.velikodniy.jcexpress.sm.SMKeys;
import name.velikodniy.jcexpress.sm.SMSession;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Known-answer test: ICAO Doc 9303 Part 11 (8th ed.), Appendix D "Worked example: Basic Access Control" (D.1 key
 * derivation, D.2 MRZ_information, D.3 authentication and establishment of session keys, D.4 Secure Messaging).
 *
 * <p>All values and APDUs are copied from the public ICAO worked example. With the inspection system's random values
 * of the example (RND.IFD and K.IFD), {@link BacSession} must reproduce the inspection system side of D.3 byte for
 * byte, and the Secure Messaging session it establishes must reproduce the protected APDUs of D.4.</p>
 */
class IcaoAppendixDBacTest {

    private static final String RND_IC = "4608F91988702212";
    private static final String RND_IFD = "781723860C06C226";
    private static final String K_IFD = "0B795240CB7049B01C19B33E32804F0B";
    private static final String CMD_DATA = "72C29C2371CC9BDB65B779B8E8D37B29ECC154AA56A8799FAE2F498F76ED92F2"
            + "5F1448EEA8AD90A7";
    private static final String RESP_DATA = "46B9342A41396CD7386BF5803104D7CEDC122B9132139BAF2EEDC94EE178534F"
            + "2F2D235D074D7449";
    private static final String KS_ENC = "979EC13B1CBFE9DCD01AB0FED307EAE5";
    private static final String KS_MAC = "F1CB1F1FB5ADF208806B89DC579DC1F8";
    private static final String SSC = "887022120C06C226";

    @Test
    void documentBasicAccessKeysOfAppendixD2() {
        // App. D.2 step 5 (with the parity adjustment of App. D.1 step 4)
        SMKeys keys = BacSession.documentBasicAccessKeys("L898902C<", "690806", "940623");

        assertThat(Hex.encode(keys.encKey())).isEqualTo("AB94FDECF2674FDFB9B391F85D7F76F2");
        assertThat(Hex.encode(keys.macKey())).isEqualTo("7962D9ECE03D1ACD4C76089DCE131543");
    }

    @Test
    void inspectionSystemSideOfAppendixD3IsReproducedByteForByte() {
        // GET CHALLENGE with Le '08', EXTERNAL AUTHENTICATE with cmd_data and Le '28' (4.3.4)
        ScriptedCard chip = appendixD3();

        BacResult result = icaoSession().perform(chip);

        assertThat(chip.isFinished()).isTrue();
        assertThat(Hex.encode(result.encKey())).isEqualTo(KS_ENC);
        assertThat(Hex.encode(result.macKey())).isEqualTo(KS_MAC);
        assertThat(Hex.encode(result.ssc())).isEqualTo(SSC);
    }

    @Test
    void accessKeysWithoutParityAdjustmentGiveTheSameExchange() {
        // 9.7.1.1: the parity adjustment is OPTIONAL; DES ignores the parity bits (KDF output of App. D.1 step 3)
        ScriptedCard chip = appendixD3();
        BacSession session = BacSession.builder()
                .accessKeys(new SMKeys(Hex.decode("AB94FCEDF2664EDFB9B291F85D7F77F2"),
                        Hex.decode("7862D9ECE03C1BCD4D77089DCF131442")))
                .randomSource(icaoRandom())
                .build();

        BacResult result = session.perform(chip);

        assertThat(chip.isFinished()).isTrue();
        assertThat(Hex.encode(result.encKey())).isEqualTo(KS_ENC);
    }

    @Test
    void establishedSessionProtectsTheCommandsOfAppendixD4() {
        // D.4: SELECT EF.COM, READ BINARY of the first 4 bytes, READ BINARY of the remaining 18 bytes
        ScriptedCard chip = appendixD3()
                .expect("0CA4020C158709016375432908C044F68E08BF8B92D635FF24F800", "990290008E08FA855A5D4C50A8ED9000")
                .expect("0CB000000D9701048E08ED6705417E96BA5500",
                        "8709019FF0EC34F9922651990290008E08AD55CC17140B2DED9000")
                .expect("0CB000040D9701128E082EA28A70F3C7B53500",
                        "871901FB9235F4E4037F2327DCC8964F1F9B8C30F42C8E2FFF224A990290008E08C8B2787EAEA07D749000");
        BacResult result = icaoSession().perform(chip);
        SMSession secure = result.toSMSession(chip);

        APDUResponse select = secure.send(0x00, 0xA4, 0x02, 0x0C, Hex.decode("011E"));
        APDUResponse head = secure.send(0x00, 0xB0, 0x00, 0x00, null, 4);
        APDUResponse rest = secure.send(0x00, 0xB0, 0x00, 0x04, null, 0x12);

        assertThat(secure.context().algorithm()).isEqualTo(SMAlgorithm.DES3);
        assertThat(select.sw()).isEqualTo(0x9000);
        assertThat(Hex.encode(head.data())).isEqualTo("60145F01");
        assertThat(Hex.encode(rest.data())).isEqualTo("04303130365F36063034303030305C026175");
        assertThat(Hex.encode(secure.context().ssc())).isEqualTo("887022120C06C22C");
        assertThat(chip.isFinished()).isTrue();
    }

    private static BacSession icaoSession() {
        return BacSession.builder()
                .mrz("L898902C<", "690806", "940623")
                .randomSource(icaoRandom())
                .build();
    }

    /** Supplies RND.IFD and then K.IFD of App. D.3 step 2. */
    static BacSession.RandomSource icaoRandom() {
        Iterator<byte[]> values = List.of(Hex.decode(RND_IFD), Hex.decode(K_IFD)).iterator();
        return bytes -> {
            byte[] next = values.next();
            assertThat(bytes).hasSameSizeAs(next);
            System.arraycopy(next, 0, bytes, 0, next.length);
        };
    }

    static ScriptedCard appendixD3() {
        return new ScriptedCard()
                .expect("0084000008", RND_IC + "9000")
                .expect("0082000028" + CMD_DATA + "28", RESP_DATA + "9000");
    }
}
