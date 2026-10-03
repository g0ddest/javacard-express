package name.velikodniy.jcexpress.pace;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.sm.SMContext;
import name.velikodniy.jcexpress.sm.SMSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PaceSession} builder and error handling. Protocol runs are tested against the ICAO transcript
 * ({@link IcaoAppendixG1PaceTest}) and an independent chip simulator ({@link PaceEndToEndTest}).
 */
class PaceSessionTest {

    private static final PaceAlgorithm ALG = PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128;
    private static final PaceParameterId PARAM = PaceParameterId.NIST_P256;

    /**
     * Stub session that records the command data and answers with queued responses (then 9000).
     */
    private static class PaceCardStub implements SmartCardSession {
        final List<APDUResponse> responses = new ArrayList<>();
        final List<byte[]> sentData = new ArrayList<>();
        int callIndex = 0;

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
            if (data != null) sentData.add(data.clone());
            if (callIndex < responses.size()) {
                return responses.get(callIndex++);
            }
            return new APDUResponse(new byte[]{(byte) 0x90, 0x00});
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
            return send(cla, ins, p1, p2, data, -1);
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2) {
            return send(cla, ins, p1, p2, null, -1);
        }

        @Override
        public APDUResponse send(int cla, int ins) {
            return send(cla, ins, 0, 0, null, -1);
        }

        @Override
        public void install(Class<? extends Applet> appletClass) {}
        @Override
        public void install(Class<? extends Applet> appletClass, AID aid) {}
        @Override
        public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {}
        @Override
        public void select(Class<? extends Applet> appletClass) {}
        @Override
        public void select(AID aid) {}
        @Override
        public void reset() {}
        @Override
        public byte[] transmit(byte[] rawApdu) { return new byte[]{(byte) 0x90, 0x00}; }
        @Override
        public void close() {}
    }

    @Test
    void builderShouldCreateSession() {
        byte[] password = new byte[16];
        PaceSession session = PaceSession.builder()
                .algorithm(ALG)
                .parameterId(PARAM)
                .password(PasswordRef.CAN, password)
                .build();
        assertThat(session).isNotNull();
    }

    @Test
    void nullAlgorithmShouldThrow() {
        assertThatThrownBy(() -> PaceSession.builder()
                .parameterId(PARAM)
                .password(PasswordRef.CAN, new byte[16])
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullParameterIdShouldThrow() {
        assertThatThrownBy(() -> PaceSession.builder()
                .algorithm(ALG)
                .password(PasswordRef.CAN, new byte[16])
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullPasswordShouldThrow() {
        assertThatThrownBy(() -> PaceSession.builder()
                .algorithm(ALG)
                .parameterId(PARAM)
                .build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void performShouldSendMseSetAtFirst() {
        // Prepare a stub that will fail at step 1 (after MSE:Set AT succeeds)
        PaceCardStub stub = new PaceCardStub();
        // MSE:Set AT → success
        stub.responses.add(new APDUResponse(new byte[]{(byte) 0x90, 0x00}));
        // GA Step 1 → fail
        stub.responses.add(new APDUResponse(new byte[]{(byte) 0x6A, (byte) 0x88}));

        PaceSession session = PaceSession.builder()
                .algorithm(ALG)
                .parameterId(PARAM)
                .password(PasswordRef.CAN, new byte[16])
                .build();

        assertThatThrownBy(() -> session.perform(stub))
                .isInstanceOf(PaceException.class)
                .hasMessageContaining("Step 1");

        // MSE:Set AT was sent
        assertThat(stub.sentData).isNotEmpty();
    }

    @Test
    void resultShouldCreateSMContext() {
        PaceResult result = new PaceResult(
                new byte[16], new byte[16], new byte[8], new byte[8]);
        SMContext ctx = result.toSMContext();
        assertThat(ctx).isNotNull();
        assertThat(ctx.encKey()).hasSize(16);
        assertThat(ctx.macKey()).hasSize(16);
    }

    @Test
    void resultShouldCreateSMSession() {
        PaceResult result = new PaceResult(
                new byte[16], new byte[16], new byte[8], new byte[8]);
        PaceCardStub stub = new PaceCardStub();
        SMSession smSession = result.toSMSession(stub);
        assertThat(smSession).isNotNull();
        assertThat(smSession.delegate()).isSameAs(stub);
    }

    @Test
    void resultDoesNotPrintTheSessionKeys() {
        // session keys of ICAO App. G.1; the authentication tokens are exchanged in the clear and may be shown
        PaceResult result = new PaceResult(Hex.decode("F5F0E35C0D7161EE6724EE513A0D9A7F"),
                Hex.decode("FE251C7858B356B24514B3BD5F4297D1"), Hex.decode("3ABB9674BCE93C08"),
                Hex.decode("C2B0BD78D94BA866"));

        assertThat(result.toString())
                .doesNotContainIgnoringCase("F5F0E35C")
                .doesNotContainIgnoringCase("FE251C78")
                .containsIgnoringCase("3ABB9674BCE93C08");
    }

    @Test
    void destroyedSessionCannotBePerformed() {
        PaceCardStub stub = new PaceCardStub();
        PaceSession session = PaceSession.builder().algorithm(ALG).parameterId(PARAM)
                .password(PasswordRef.CAN, new byte[16]).build();

        session.destroy();

        assertThat(session.isDestroyed()).isTrue();
        assertThatThrownBy(() -> session.perform(stub)).isInstanceOf(IllegalStateException.class);
        assertThat(stub.callIndex).isZero();
    }

    @Test
    void rawPasswordKeyIsUsedAsGivenEvenForMrz() {
        // password(ref, bytes) takes K_pi itself; it must not be derived a second time (K_pi of ICAO App. G)
        byte[] kPi = Hex.decode("89DED1B26624EC1E634C1989302849DD");
        IcaoPaceChipSimulator chip = IcaoPaceChipSimulator.random(PARAM, ALG, PasswordRef.MRZ, kPi);

        PaceSession.builder().algorithm(ALG).parameterId(PARAM).password(PasswordRef.MRZ, kPi).build().perform(chip);

        assertThat(chip.authenticated).isTrue();
    }

    @Test
    void canIsEncodedAsIso8859StringAndDerivedWithCounter3() {
        // ICAO 9303-11 9.7.3: K_pi = KDF(f(pi), 3), Table 14: f(CAN) = ISO/IEC 8859-1 encoded character string
        byte[] kPi = PaceMrz.kdf("123456".getBytes(ISO_8859_1), 3, ALG.keyLength());
        IcaoPaceChipSimulator chip = IcaoPaceChipSimulator.random(
                PaceParameterId.BRAINPOOL_P256R1, ALG, PasswordRef.CAN, kPi);

        PaceSession.builder().algorithm(ALG).parameterId(PaceParameterId.BRAINPOOL_P256R1)
                .canPassword("123456").build().perform(chip);

        assertThat(chip.authenticated).isTrue();
        assertThat(Hex.encode(chip.commands.getFirst().data())).contains("830102");
    }

    @Test
    void pinAndPukUseTheirPasswordReferences() {
        // BSI TR-03110: PIN (3) and PUK (4) are encoded like the CAN
        byte[] pinKey = PaceMrz.kdf("4711".getBytes(ISO_8859_1), 3, ALG.keyLength());
        IcaoPaceChipSimulator pinChip = IcaoPaceChipSimulator.random(PARAM, ALG, PasswordRef.PIN, pinKey);
        byte[] pukKey = PaceMrz.kdf("1234567890".getBytes(ISO_8859_1), 3, ALG.keyLength());
        IcaoPaceChipSimulator pukChip = IcaoPaceChipSimulator.random(PARAM, ALG, PasswordRef.PUK, pukKey);

        PaceSession.builder().algorithm(ALG).parameterId(PARAM).pinPassword("4711").build().perform(pinChip);
        PaceSession.builder().algorithm(ALG).parameterId(PARAM).pukPassword("1234567890").build().perform(pukChip);

        assertThat(pinChip.authenticated).isTrue();
        assertThat(pukChip.authenticated).isTrue();
    }

    @Test
    void passwordKeyMustHaveTheSessionKeyLength() {
        assertThatThrownBy(() -> PaceSession.builder()
                .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_256)
                .parameterId(PARAM)
                .password(PasswordRef.CAN, new byte[16])
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32");
    }

    @Test
    void emptyOrNonLatin1SecretsAreRejected() {
        assertThatThrownBy(() -> PaceSession.builder().canPassword(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PaceSession.builder().pinPassword("\u20AC123"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mrzPasswordShouldDeriveFromFields() {
        PaceSession session = PaceSession.builder()
                .algorithm(ALG)
                .parameterId(PARAM)
                .mrzPassword("L898902C<", "690806", "940623")
                .build();
        assertThat(session).isNotNull();
    }
}
