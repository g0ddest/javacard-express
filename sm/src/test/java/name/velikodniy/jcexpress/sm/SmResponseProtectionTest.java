package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Protection of response APDUs (ICAO Doc 9303-11, 9.8.4, 9.8.5 and Figure 6).
 *
 * <p>Warnings and errors that are not SM errors come back protected ({@code [DO'87'] DO'99' DO'8E'}
 * with SW1-SW2 copied into DO'99'); only SM errors are returned as a bare status word, after which the
 * chip has aborted Secure Messaging (9.8.3). Vectors: {@code des3-session.properties} (continuing the
 * ICAO App. D.4 session) and {@code aes-session.properties} (session after ICAO App. G.1).</p>
 */
class SmResponseProtectionTest {

    private static final SmVectors DES3 = SmVectors.load("des3-session.properties");
    private static final SmVectors AES = SmVectors.load("aes-session.properties");

    @Nested
    class ProtectedStatusWords {

        @Test
        void protectedWarning6282WithDataIsVerifiedDecryptedAndCountedInSsc() {
            SmVectors.Step step = DES3.step(0);
            SMContext ctx = DES3.context(SMAlgorithm.DES3, step);
            assertThat(Hex.encode(SMCodec.wrapCommand(ctx, step.plain()))).isEqualTo(Hex.encode(step.command()));

            APDUResponse response = SMCodec.unwrapResponse(ctx, step.response());

            assertThat(response.sw()).isEqualTo(0x6282);
            assertThat(Hex.encode(response.data())).isEqualTo("3030305C026175");
            assertThat(Hex.encode(ctx.ssc())).isEqualTo(Hex.encode(DES3.step(1).ssc()));
        }

        @Test
        void protectedError6A82KeepsTheSscInStepWithTheChip() {
            SmVectors.Step error = DES3.step(1);
            SMContext ctx = DES3.context(SMAlgorithm.DES3, error);
            SMCodec.wrapCommand(ctx, error.plain());

            APDUResponse response = SMCodec.unwrapResponse(ctx, error.response());

            assertThat(response.sw()).isEqualTo(0x6A82);
            assertThat(response.data()).as("SM data objects must not leak as response data").isEmpty();
            SmVectors.Step next = DES3.step(2);
            assertThat(Hex.encode(SMCodec.wrapCommand(ctx, next.plain()))).isEqualTo(Hex.encode(next.command()));
            assertThat(Hex.encode(SMCodec.unwrapResponse(ctx, next.response()).data())).isEqualTo("60145F01");
        }

        @Test
        void aesSessionSurvivesProtectedWarningAndError() {
            SMContext ctx = AES.context(SMAlgorithm.AES, AES.step(4));
            for (int i = 4; i < AES.steps().size(); i++) {
                SmVectors.Step step = AES.step(i);
                assertThat(Hex.encode(SMCodec.wrapCommand(ctx, step.plain()))).as(step.name())
                        .isEqualTo(Hex.encode(step.command()));
                APDUResponse response = SMCodec.unwrapResponse(ctx, step.response());
                assertThat(response.sw()).as(step.name()).isEqualTo(step.sw());
                assertThat(Hex.encode(response.data())).as(step.name()).isEqualTo(Hex.encode(step.data()));
            }
            assertThat(ctx.isTerminated()).isFalse();
        }

        @Test
        void statusWordIsTakenFromAuthenticatedDo99NotFromTheTrailer() {
            SmVectors.Step step = DES3.step(0);
            SMContext ctx = DES3.context(SMAlgorithm.DES3, step);
            SMCodec.wrapCommand(ctx, step.plain());
            byte[] tampered = step.response().clone();
            tampered[tampered.length - 2] = (byte) 0x90;
            tampered[tampered.length - 1] = 0x00;

            assertThat(SMCodec.unwrapResponse(ctx, tampered).sw()).isEqualTo(0x6282);
        }
    }

    @Nested
    class UnauthenticatedResponses {

        @Test
        void forgedDataUnderNon9000StatusIsRejected() {
            SMContext ctx = DES3.context(SMAlgorithm.DES3, DES3.step(0));
            SMCodec.wrapCommand(ctx, DES3.step(0).plain());

            assertThatThrownBy(() -> SMCodec.unwrapResponse(ctx, Hex.decode("DEADBEEF6283")))
                    .isInstanceOf(SMException.class);
        }

        @Test
        void protectedResponseWithoutMacIsRejectedWhateverItsStatus() {
            SMContext ctx = DES3.context(SMAlgorithm.DES3, DES3.step(1));
            SMCodec.wrapCommand(ctx, DES3.step(1).plain());

            assertThatThrownBy(() -> SMCodec.unwrapResponse(ctx, Hex.decode("99026A826A82")))
                    .isInstanceOf(SMException.class)
                    .hasMessageContaining("DO'8E'");
        }

        @Test
        void tamperedWarningDataFailsMacVerification() {
            SmVectors.Step step = DES3.step(0);
            SMContext ctx = DES3.context(SMAlgorithm.DES3, step);
            SMCodec.wrapCommand(ctx, step.plain());
            byte[] tampered = step.response().clone();
            tampered[5] ^= 0x01;

            assertThatThrownBy(() -> SMCodec.unwrapResponse(ctx, tampered))
                    .isInstanceOf(SMException.class)
                    .hasMessageContaining("MAC verification failed");
        }

        @Test
        void unexpectedDataObjectIsRejected() {
            SMContext ctx = DES3.context(SMAlgorithm.DES3, DES3.step(0));
            SMCodec.wrapCommand(ctx, DES3.step(0).plain());

            assertThatThrownBy(() -> SMCodec.unwrapResponse(ctx,
                    Hex.decode("8102DEAD990290008E0800000000000000009000")))
                    .isInstanceOf(SMException.class)
                    .hasMessageContaining("DO'99'");
        }
    }

    @Nested
    class UnprotectedStatusWords {

        @Test
        void smErrorWithoutSecureMessagingIsReturnedAndEndsTheSession() {
            SMContext ctx = DES3.context(SMAlgorithm.DES3, DES3.step(0));
            SMCodec.wrapCommand(ctx, DES3.step(0).plain());
            byte[] sscAfterCommand = ctx.ssc();

            APDUResponse response = SMCodec.unwrapResponse(ctx, Hex.decode("6988"));

            assertThat(response.sw()).isEqualTo(0x6988);
            assertThat(response.data()).isEmpty();
            assertThat(ctx.ssc()).isEqualTo(sscAfterCommand);
            assertThat(ctx.isTerminated()).isTrue();
            assertThat(ctx.terminationReason()).contains("6988");
        }

        @Test
        void terminatedSessionRefusesFurtherCommands() {
            SMContext ctx = DES3.context(SMAlgorithm.DES3, DES3.step(0));
            SMCodec.wrapCommand(ctx, DES3.step(0).plain());
            SMCodec.unwrapResponse(ctx, Hex.decode("6987"));

            assertThatThrownBy(() -> SMCodec.wrapCommand(ctx, Hex.decode("00B0000004")))
                    .isInstanceOf(SMException.class)
                    .hasMessageContaining("terminated")
                    .hasMessageContaining("6987");
        }

        @Test
        void bareSuccessStatusIsNeverAcceptedAsProtected() {
            SMContext ctx = DES3.context(SMAlgorithm.DES3, DES3.step(0));
            SMCodec.wrapCommand(ctx, DES3.step(0).plain());

            assertThatThrownBy(() -> SMCodec.unwrapResponse(ctx, Hex.decode("9000")))
                    .isInstanceOf(SMException.class)
                    .hasMessageContaining("Unprotected 9000");
            assertThat(ctx.isTerminated()).isTrue();
        }
    }
}
