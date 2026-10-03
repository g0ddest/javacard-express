package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Odd instruction bytes under Secure Messaging (ICAO Doc 9303-11 9.8.4: "In case INS is even, DO'87' SHALL be used,
 * and in case INS is odd, DO'85' SHALL be used"). DO'85' carries the cryptogram without the padding-content
 * indicator. Vectors: READ BINARY with odd INS 'B1' and offset data object '54' ({@code odd-ins-*.properties}).
 */
class SmOddInsTest {

    @Test
    void oddInsCommandDataIsCarriedInDo85_des3() {
        assertExchange(SmVectors.load("odd-ins-3des.properties"), SMAlgorithm.DES3);
    }

    @Test
    void oddInsCommandDataIsCarriedInDo85_aes() {
        assertExchange(SmVectors.load("odd-ins-aes.properties"), SMAlgorithm.AES);
    }

    @Test
    void evenInsStillUsesDo87WithPaddingIndicator() {
        SmVectors vectors = SmVectors.load("odd-ins-3des.properties");
        byte[] even = vectors.step(0).plain().clone();
        even[1] = (byte) 0xB0;

        byte[] wrapped = SMCodec.wrapCommand(vectors.context(SMAlgorithm.DES3, vectors.step(0)), even);

        // DO'87' 87 09 01 <8> (11) + DO'97' (3) + DO'8E' (10) = 24 = '18'
        assertThat(Hex.encode(wrapped)).startsWith("0CB00000188709");
    }

    private static void assertExchange(SmVectors vectors, SMAlgorithm algorithm) {
        SmVectors.Step step = vectors.step(0);
        SMContext ctx = vectors.context(algorithm, step);

        byte[] wrapped = SMCodec.wrapCommand(ctx, step.plain());
        assertThat(wrapped[5]).as("first data object").isEqualTo((byte) 0x85);
        assertThat(Hex.encode(wrapped)).isEqualTo(Hex.encode(step.command()));

        APDUResponse response = SMCodec.unwrapResponse(ctx, step.response());
        assertThat(Hex.encode(response.data())).isEqualTo(Hex.encode(step.data()));
        assertThat(response.sw()).isEqualTo(step.sw());
    }
}
