package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AES Secure Messaging per ICAO Doc 9303 Part 11, 9.8.7: AES-CBC with key KSEnc and
 * {@code IV = E(KSEnc, SSC)} (9.8.7.1), CMAC-8 over the SSC-prefixed datagram (9.8.7.2), SSC starting at
 * zero after PACE (9.8.7.3) and incremented before every command and response (9.8.2).
 *
 * <p>KSEnc/KSMAC are the PACE session keys of ICAO App. G.1; the protected APDUs come from
 * {@code aes-session.properties} (see {@link SmVectors}).</p>
 */
class AesSecureMessagingVectorTest {

    private static final SmVectors VECTORS = SmVectors.load("aes-session.properties");

    @Test
    void commandDataIsEncryptedWithIvDerivedFromSsc_selectApplication() {
        SmVectors.Step step = VECTORS.step(0);
        SMContext ctx = VECTORS.context(SMAlgorithm.AES, step);

        assertThat(Hex.encode(SMCodec.wrapCommand(ctx, step.plain()))).isEqualTo(Hex.encode(step.command()));
    }

    @Test
    void commandDataIsEncryptedWithIvDerivedFromSsc_selectEfCom() {
        SmVectors.Step step = VECTORS.step(1);
        SMContext ctx = VECTORS.context(SMAlgorithm.AES, step);

        assertThat(Hex.encode(SMCodec.wrapCommand(ctx, step.plain()))).isEqualTo(Hex.encode(step.command()));
    }

    @Test
    void commandWithoutDataCarriesOnlyDo97AndMac() {
        SmVectors.Step step = VECTORS.step(2);
        SMContext ctx = VECTORS.context(SMAlgorithm.AES, step);

        assertThat(Hex.encode(SMCodec.wrapCommand(ctx, step.plain()))).isEqualTo(Hex.encode(step.command()));
    }

    @Test
    void responseDataIsDecryptedWithIvDerivedFromResponseSsc_shortResponse() {
        assertExchange(VECTORS.step(2));
    }

    @Test
    void responseDataIsDecryptedWithIvDerivedFromResponseSsc_twoBlockResponse() {
        assertExchange(VECTORS.step(3));
    }

    @Test
    void successfulSessionStaysInSyncWithTheChip() {
        SMContext ctx = VECTORS.context(SMAlgorithm.AES, VECTORS.step(0));
        for (int i = 0; i < 4; i++) {
            SmVectors.Step step = VECTORS.step(i);
            assertThat(Hex.encode(ctx.ssc())).as(step.name()).isEqualTo(Hex.encode(step.ssc()));
            assertThat(Hex.encode(SMCodec.wrapCommand(ctx, step.plain()))).as(step.name())
                    .isEqualTo(Hex.encode(step.command()));
            APDUResponse response = SMCodec.unwrapResponse(ctx, step.response());
            assertThat(Hex.encode(response.data())).as(step.name()).isEqualTo(Hex.encode(step.data()));
            assertThat(response.sw()).as(step.name()).isEqualTo(step.sw());
        }
    }

    private static void assertExchange(SmVectors.Step step) {
        SMContext ctx = VECTORS.context(SMAlgorithm.AES, step);
        SMCodec.wrapCommand(ctx, step.plain());

        APDUResponse response = SMCodec.unwrapResponse(ctx, step.response());

        assertThat(Hex.encode(response.data())).isEqualTo(Hex.encode(step.data()));
        assertThat(response.sw()).isEqualTo(step.sw());
    }
}
