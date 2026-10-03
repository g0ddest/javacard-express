package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The code examples of {@code sm/README.md}, compiled and run with the values of ICAO Doc 9303-11 App. D.4 (3DES
 * Secure Messaging after BAC). Keep both in sync.
 */
class ReadmeExamplesTest {

    private static final String SELECT_RESPONSE = "990290008E08FA855A5D4C50A8ED9000";
    private static final String READ_BINARY_RESPONSE = "8709019FF0EC34F9922651990290008E08AD55CC17140B2DED9000";

    @Test
    void basicUsage() {
        RecordingSession card = new RecordingSession(Hex.decode(SELECT_RESPONSE), Hex.decode(READ_BINARY_RESPONSE));
        byte[] ksEnc = Hex.decode("979EC13B1CBFE9DCD01AB0FED307EAE5");
        byte[] ksMac = Hex.decode("F1CB1F1FB5ADF208806B89DC579DC1F8");
        byte[] ssc = Hex.decode("887022120C06C226");

        SMContext ctx = new SMContext(SMAlgorithm.DES3, new SMKeys(ksEnc, ksMac), ssc);
        SMSession secure = SMSession.wrap(card, ctx);

        secure.send(0x00, 0xA4, 0x02, 0x0C, Hex.decode("011E"));             // SELECT EF.COM
        APDUResponse head = secure.send(0x00, 0xB0, 0x00, 0x00, null, 4);    // READ BINARY, Ne = 4

        assertThat(Hex.encode(head.data())).isEqualTo("60145F01");
        // Anatomy of a wrapped command: CLA' 0C, Lc' 0D, DO'97' 97 01 04, DO'8E', Le' 00
        assertThat(Hex.encode(card.lastCommand())).isEqualTo("0CB000000D9701048E08ED6705417E96BA5500");
    }

    @Test
    void lowLevelCodec() {
        SMContext ctx = new SMContext(SMAlgorithm.DES3,
                new SMKeys(Hex.decode("979EC13B1CBFE9DCD01AB0FED307EAE5"),    // KSEnc
                        Hex.decode("F1CB1F1FB5ADF208806B89DC579DC1F8")),      // KSMAC
                Hex.decode("887022120C06C226"));                              // SSC

        byte[] wrapped = SMCodec.wrapCommand(ctx, Hex.decode("00A4020C02011E"));   // SELECT EF.COM
        APDUResponse response = SMCodec.unwrapResponse(ctx, Hex.decode(SELECT_RESPONSE));

        assertThat(Hex.encode(wrapped)).isEqualTo("0CA4020C158709016375432908C044F68E08BF8B92D635FF24F800");
        assertThat(response.sw()).isEqualTo(0x9000);
        assertThat(Hex.encode(ctx.ssc())).isEqualTo("887022120C06C228");
    }

    @Test
    void classBytesOfTheReadme() {
        assertThat(SMCodec.protectedCla(0x00)).isEqualTo(0x0C);
        assertThat(SMCodec.protectedCla(0x01)).isEqualTo(0x0D);
        assertThat(SMCodec.protectedCla(0x10)).isEqualTo(0x1C);
        assertThat(SMCodec.protectedCla(0x40)).isEqualTo(0x60);
        assertThat(SMCodec.protectedCla(0x4F)).isEqualTo(0x6F);
    }
}
