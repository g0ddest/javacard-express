package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Known-answer test: ICAO Doc 9303 Part 11 (8th ed.), Appendix D.4 "Secure Messaging" (3DES).
 *
 * <p>Session keys KSEnc/KSMAC and the initial SSC are those derived in Appendix D.3. Every protected
 * command, protected response, SSC value and decrypted plaintext below is copied from the public
 * ICAO worked example.</p>
 */
class IcaoAppendixD4SecureMessagingTest {

    private static final byte[] KS_ENC = Hex.decode("979EC13B1CBFE9DCD01AB0FED307EAE5");
    private static final byte[] KS_MAC = Hex.decode("F1CB1F1FB5ADF208806B89DC579DC1F8");
    private static final byte[] SSC = Hex.decode("887022120C06C226");

    private static SMContext context() {
        return new SMContext(SMAlgorithm.DES3, new SMKeys(KS_ENC, KS_MAC), SSC);
    }

    @Test
    void selectEfComIsProtectedAndItsResponseVerified() {
        SMContext ctx = context();

        byte[] wrapped = SMCodec.wrapCommand(ctx, Hex.decode("00A4020C02011E"));
        assertThat(Hex.encode(wrapped)).isEqualTo("0CA4020C158709016375432908C044F68E08BF8B92D635FF24F800");
        assertThat(Hex.encode(ctx.ssc())).isEqualTo("887022120C06C227");

        APDUResponse response = SMCodec.unwrapResponse(ctx, Hex.decode("990290008E08FA855A5D4C50A8ED9000"));
        assertThat(response.sw()).isEqualTo(0x9000);
        assertThat(response.data()).isEmpty();
        assertThat(Hex.encode(ctx.ssc())).isEqualTo("887022120C06C228");
    }

    @Test
    void readBinaryOfEfComDecryptsBothChunks() {
        SMContext ctx = context();
        SMCodec.wrapCommand(ctx, Hex.decode("00A4020C02011E"));
        SMCodec.unwrapResponse(ctx, Hex.decode("990290008E08FA855A5D4C50A8ED9000"));

        assertThat(Hex.encode(SMCodec.wrapCommand(ctx, Hex.decode("00B0000004"))))
                .isEqualTo("0CB000000D9701048E08ED6705417E96BA5500");
        APDUResponse first = SMCodec.unwrapResponse(ctx,
                Hex.decode("8709019FF0EC34F9922651990290008E08AD55CC17140B2DED9000"));
        assertThat(Hex.encode(first.data())).isEqualTo("60145F01");

        assertThat(Hex.encode(SMCodec.wrapCommand(ctx, Hex.decode("00B0000412"))))
                .isEqualTo("0CB000040D9701128E082EA28A70F3C7B53500");
        APDUResponse rest = SMCodec.unwrapResponse(ctx, Hex.decode(
                "871901FB9235F4E4037F2327DCC8964F1F9B8C30F42C8E2FFF224A990290008E08C8B2787EAEA07D749000"));
        assertThat(Hex.encode(rest.data())).isEqualTo("04303130365F36063034303030305C026175");
        assertThat(rest.sw()).isEqualTo(0x9000);
        assertThat(Hex.encode(ctx.ssc())).isEqualTo("887022120C06C22C");
    }

    @Test
    void appendixD3ChallengeCryptogramAndChecksum() {
        // E_IFD = 3DES-CBC(K_Enc, S) with zero IV and no padding (4.3.3.1),
        // M_IFD = retail MAC (ISO 9797-1 MAC algorithm 3) over pad(E_IFD) (4.3.3.2)
        byte[] kEnc = Hex.decode("AB94FDECF2674FDFB9B391F85D7F76F2");
        byte[] kMac = Hex.decode("7962D9ECE03D1ACD4C76089DCE131543");
        byte[] s = Hex.decode("781723860C06C2264608F919887022120B795240CB7049B01C19B33E32804F0B");

        byte[] eIfd = SMAlgorithm.DES3.encrypt(kEnc, s, new byte[8]);

        assertThat(Hex.encode(eIfd))
                .isEqualTo("72C29C2371CC9BDB65B779B8E8D37B29ECC154AA56A8799FAE2F498F76ED92F2");
        assertThat(Hex.encode(SMAlgorithm.DES3.mac(kMac, SMAlgorithm.DES3.pad(eIfd))))
                .isEqualTo("5F1448EEA8AD90A7");
    }
}
