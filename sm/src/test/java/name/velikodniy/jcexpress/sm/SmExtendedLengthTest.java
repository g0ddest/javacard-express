package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Command and response lengths under Secure Messaging.
 *
 * <p>ISO/IEC 7816-4 (5.1) defines short APDUs (Lc 1..255, Le 1..256) and extended APDUs ('00' Lc1 Lc2, Le up to
 * 65536). ICAO Doc 9303-11 9.8.4 replaces Lc by Lc' after protection, and Figure 5 ends the protected command with
 * Le' = '00' for standard length and '00 00' for extended length. The protected command is extended when its body
 * exceeds 255 bytes or the expected response length exceeds 256 bytes; DO'97' then carries Le in two bytes.
 * Vectors: {@code extended-3des.properties} and {@code extended-aes.properties} (see {@link SmVectors}).</p>
 */
class SmExtendedLengthTest {

    private static final SmVectors DES3 = SmVectors.load("extended-3des.properties");
    private static final SmVectors AES = SmVectors.load("extended-aes.properties");

    static List<SmVectors.Step> des3Steps() {
        return DES3.steps();
    }

    static List<SmVectors.Step> aesSteps() {
        return AES.steps();
    }

    @ParameterizedTest(name = "3DES {0}")
    @MethodSource("des3Steps")
    void des3ExchangesMatchTheReference(SmVectors.Step step) {
        assertExchange(DES3.context(SMAlgorithm.DES3, step), step);
    }

    @ParameterizedTest(name = "AES {0}")
    @MethodSource("aesSteps")
    void aesExchangesMatchTheReference(SmVectors.Step step) {
        assertExchange(AES.context(SMAlgorithm.AES, step), step);
    }

    private static void assertExchange(SMContext ctx, SmVectors.Step step) {
        assertThat(Hex.encode(SMCodec.wrapCommand(ctx, step.plain()))).isEqualTo(Hex.encode(step.command()));
        APDUResponse response = SMCodec.unwrapResponse(ctx, step.response());
        assertThat(Hex.encode(response.data())).isEqualTo(Hex.encode(step.data()));
        assertThat(response.sw()).isEqualTo(step.sw());
    }

    @Nested
    class Framing {

        @Test
        void protectedBodyOf254BytesStaysShort() {
            // 239 bytes pad to 240 (3DES): DO'87' = 87 81 F1 01 <240> (244 bytes) + DO'8E' (10) = 254
            byte[] wrapped = SMCodec.wrapCommand(des3(), plain(0xD6, 239));

            assertThat(wrapped[4] & 0xFF).isEqualTo(254);
            assertThat(wrapped).hasSize(4 + 1 + 254 + 1);
            assertThat(wrapped[wrapped.length - 1]).isZero();
        }

        @Test
        void protectedBodyAbove255BytesUsesExtendedLcAndLe() {
            // 240 bytes pad to 248: body = 4 + 248 + 10 = 262 > 255 -> '00' Lc1 Lc2 ... Le' '00 00'
            byte[] wrapped = SMCodec.wrapCommand(des3(), plain(0xD6, 240));

            assertThat(Arrays.copyOfRange(wrapped, 4, 7)).containsExactly(0x00, 0x01, 0x06);
            assertThat(wrapped).hasSize(4 + 3 + 262 + 2);
            assertThat(Arrays.copyOfRange(wrapped, wrapped.length - 2, wrapped.length)).containsExactly(0, 0);
        }

        @Test
        void le256IsEncodedAsOneByteDo97InAShortCommand() {
            byte[] wrapped = SMCodec.wrapCommand(des3(), Hex.decode("00B0000000"));

            assertThat(Hex.encode(Arrays.copyOfRange(wrapped, 4, 8))).isEqualTo("0D970100");
            assertThat(wrapped).hasSize(4 + 1 + 13 + 1);
        }

        @Test
        void le257NeedsTwoByteDo97AndAnExtendedCommand() {
            byte[] wrapped = SMCodec.wrapCommand(des3(), Hex.decode("00B00000000101"));

            assertThat(Hex.encode(Arrays.copyOfRange(wrapped, 4, 11))).isEqualTo("00000E97020101");
            assertThat(Hex.encode(Arrays.copyOfRange(wrapped, wrapped.length - 2, wrapped.length))).isEqualTo("0000");
        }

        @Test
        void extendedPlainCommandWithSmallLeIsWrappedAsShortCommand() {
            SmVectors.Step step = DES3.step(4);
            byte[] wrapped = SMCodec.wrapCommand(DES3.context(SMAlgorithm.DES3, step), step.plain());

            assertThat(wrapped[4] & 0xFF).isEqualTo(wrapped.length - 6);
        }
    }

    @Nested
    class MalformedCommands {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "00B000",               // shorter than the header
                "00D6000002AA",         // Lc = 2 but one data byte
                "00D6000001AABBCC",     // trailing bytes after Le
                "00B0000000AA",         // '00' marker without a complete extended length
                "00D60000000003AABB",   // extended Lc = 3 but two data bytes
                "00D6000000000000",     // extended Lc = 0 with trailing Le
        })
        void inconsistentLengthsAreRejected(String apdu) {
            assertThatThrownBy(() -> SMCodec.wrapCommand(des3(), Hex.decode(apdu)))
                    .isInstanceOf(SMException.class)
                    .hasMessageContaining("APDU");
        }

        @Test
        void rejectedCommandDoesNotAdvanceTheSsc() {
            SMContext ctx = des3();
            byte[] before = ctx.ssc();

            assertThatThrownBy(() -> SMCodec.wrapCommand(ctx, Hex.decode("00D6000002AA")))
                    .isInstanceOf(SMException.class);
            assertThat(ctx.ssc()).isEqualTo(before);
        }
    }

    @Nested
    class ThroughSmSession {

        @Test
        void sessionSendsLargeDataAsExtendedProtectedCommand() {
            SmVectors.Step step = DES3.step(1);
            RecordingSession card = new RecordingSession(step.response());
            SMSession session = SMSession.wrap(card, DES3.context(SMAlgorithm.DES3, step));
            byte[] data = Arrays.copyOfRange(step.plain(), 7, step.plain().length);

            APDUResponse response = session.send(0x00, 0xD6, 0x00, 0x00, data);

            assertThat(Hex.encode(card.lastCommand())).isEqualTo(Hex.encode(step.command()));
            assertThat(response.sw()).isEqualTo(0x9000);
        }

        @Test
        void sessionRequestsLargeResponseWithExtendedLe() {
            SmVectors.Step step = AES.step(2);
            RecordingSession card = new RecordingSession(step.response());
            SMSession session = SMSession.wrap(card, AES.context(SMAlgorithm.AES, step));

            APDUResponse response = session.send(0x00, 0xB0, 0x00, 0x00, null, 1000);

            assertThat(Hex.encode(card.lastCommand())).isEqualTo(Hex.encode(step.command()));
            assertThat(response.data()).hasSize(1000).isEqualTo(step.data());
        }
    }

    private static SMContext des3() {
        return DES3.context(SMAlgorithm.DES3, DES3.step(0));
    }

    private static byte[] plain(int ins, int dataLength) {
        byte[] apdu = new byte[5 + dataLength];
        apdu[1] = (byte) ins;
        apdu[4] = (byte) dataLength;
        return apdu;
    }
}
