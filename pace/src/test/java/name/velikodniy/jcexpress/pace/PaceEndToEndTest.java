package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PACE runs against {@link IcaoPaceChipSimulator}, an independent chip-side implementation of ICAO Doc 9303-11
 * 4.4 with random nonces and keys, on every standardized curve (Table 12) and for AES-128/192/256.
 */
class PaceEndToEndTest {

    /** MRZ of ICAO Doc 9303-11 App. G: document number T22000129, born 640812, expires 101031. */
    private static final String DOC = "T22000129";
    private static final String DOB = "640812";
    private static final String DOE = "101031";

    @ParameterizedTest(name = "{0}")
    @EnumSource(PaceParameterId.class)
    void succeedsOnEveryStandardizedCurve(PaceParameterId parameterId) {
        assertAuthenticates(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, parameterId);
    }

    static Stream<Arguments> longerSessionKeys() {
        return Stream.of(
                Arguments.of(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_192, PaceParameterId.BRAINPOOL_P384R1),
                Arguments.of(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_192, PaceParameterId.NIST_P384),
                Arguments.of(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_256, PaceParameterId.BRAINPOOL_P512R1),
                Arguments.of(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_256, PaceParameterId.NIST_P521));
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("longerSessionKeys")
    void succeedsWithAes192And256SessionKeys(PaceAlgorithm algorithm, PaceParameterId parameterId) {
        assertAuthenticates(algorithm, parameterId);
    }

    @Test
    void generalAuthenticateUsesChainingAndAsksForUpTo256Bytes() {
        // 4.4.4.3: CLA '10' (chaining) for all but the last command; App. G.1 sends Le = '00' (Ne = 256)
        IcaoPaceChipSimulator chip = chip(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, PaceParameterId.BRAINPOOL_P256R1);

        session(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, PaceParameterId.BRAINPOOL_P256R1).perform(chip);

        assertThat(chip.commands).extracting(IcaoPaceChipSimulator.Command::ins)
                .containsExactly(0x22, 0x86, 0x86, 0x86, 0x86);
        assertThat(chip.commands).extracting(IcaoPaceChipSimulator.Command::cla)
                .containsExactly(0x00, 0x10, 0x10, 0x10, 0x00);
        assertThat(chip.commands.subList(1, 5)).extracting(IcaoPaceChipSimulator.Command::le)
                .containsOnly(256);
        assertThat(chip.commands.getFirst().le()).as("MSE:Set AT is a case 3 command").isNegative();
    }

    @Test
    void wrongPasswordIsRejectedByTheChip() {
        IcaoPaceChipSimulator chip = chip(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, PaceParameterId.NIST_P256);
        PaceSession wrongMrz = PaceSession.builder()
                .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
                .parameterId(PaceParameterId.NIST_P256)
                .mrzPassword(DOC, DOB, "101032")
                .build();

        assertThatThrownBy(() -> wrongMrz.perform(chip))
                .isInstanceOf(PaceException.class)
                .hasMessageContaining("Step 4")
                .hasMessageContaining("6300");
        assertThat(chip.authenticated).isFalse();
    }

    @Test
    void chipWithOtherDomainParametersRejectsMseSetAt() {
        // 4.4.4.1: 6A88 "referenced data (domain parameter) not available"
        IcaoPaceChipSimulator chip = chip(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, PaceParameterId.NIST_P256);

        assertThatThrownBy(() -> session(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, PaceParameterId.BRAINPOOL_P256R1)
                .perform(chip))
                .isInstanceOf(PaceException.class)
                .hasMessageContaining("MSE:Set AT")
                .hasMessageContaining("6A88");
    }

    @Test
    void wrongChipTokenFailsMutualAuthentication() {
        IcaoPaceChipSimulator chip = chip(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, PaceParameterId.NIST_P256)
                .withFault(IcaoPaceChipSimulator.Fault.WRONG_CHIP_TOKEN);

        assertThatThrownBy(() -> session(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, PaceParameterId.NIST_P256)
                .perform(chip))
                .isInstanceOf(PaceException.class)
                .hasMessageContaining("token");
    }

    @Test
    void chipKeyOffTheCurveIsRejected() {
        // 9.4.5 Note / Table 13: ephemeral public key validation (BSI TR-03111)
        IcaoPaceChipSimulator chip = chip(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, PaceParameterId.BRAINPOOL_P256R1)
                .withFault(IcaoPaceChipSimulator.Fault.OFF_CURVE_MAPPING_KEY);

        assertThatThrownBy(() -> session(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, PaceParameterId.BRAINPOOL_P256R1)
                .perform(chip))
                .isInstanceOf(PaceException.class)
                .hasMessageContaining("not on the curve");
    }

    @Test
    void chipEchoingTheTerminalKeyIsRejected() {
        // 4.4.1 step 3d: the inspection system SHOULD check that PK_DH,IC and PK_DH,IFD differ
        IcaoPaceChipSimulator chip = chip(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, PaceParameterId.NIST_P256)
                .withFault(IcaoPaceChipSimulator.Fault.ECHO_TERMINAL_KEY);

        assertThatThrownBy(() -> session(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128, PaceParameterId.NIST_P256)
                .perform(chip))
                .isInstanceOf(PaceException.class)
                .hasMessageContaining("PK_DH");
        assertThat(chip.commands).hasSize(4);
    }

    @Test
    void simulatorReproducesTheChipSideOfIcaoAppendixG1() {
        BigInteger skMapIc = new BigInteger("498FF49756F2DC1587840041839A85982BE7761D14715FB091EFA7BCE9058560", 16);
        BigInteger skDhIc = new BigInteger("107CF58696EF6155053340FD633392BA81909DF7B9706F226F32086C7AFF974A", 16);
        IcaoPaceChipSimulator chip = new IcaoPaceChipSimulator(PaceParameterId.BRAINPOOL_P256R1.ecParameterSpec(),
                PasswordRef.MRZ, Hex.decode("89DED1B26624EC1E634C1989302849DD"),
                PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128.oidBytes(), 13,
                new BigInteger("3F00C4D39D153F2B2A214A078D899B22", 16), skMapIc, skDhIc);
        BigInteger skMapIfd = new BigInteger("7F4EF07B9EA82FD78AD689B38D0BC78CF21F249D953BC46F4C6E19259C010F99", 16);
        BigInteger skDhIfd = new BigInteger("A73FB703AC1436A18E0CFA5ABB3F7BEC7A070E7A6788486BEE230C4A22762595", 16);
        Iterator<BigInteger> terminalKeys = List.of(skMapIfd, skDhIfd).iterator();

        PaceResult result = PaceSession.builder()
                .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
                .parameterId(PaceParameterId.BRAINPOOL_P256R1)
                .mrzPassword(DOC, DOB, DOE)
                .ephemeralKeySource(params -> terminalKeys.next())
                .build()
                .perform(chip);

        assertThat(Hex.encode(result.cardToken())).isEqualTo("3ABB9674BCE93C08");
        assertThat(Hex.encode(chip.ksEnc)).isEqualTo("F5F0E35C0D7161EE6724EE513A0D9A7F");
    }

    private static void assertAuthenticates(PaceAlgorithm algorithm, PaceParameterId parameterId) {
        IcaoPaceChipSimulator chip = chip(algorithm, parameterId);

        PaceResult result = session(algorithm, parameterId).perform(chip);

        assertThat(chip.authenticated).isTrue();
        assertThat(result.encKey()).hasSize(algorithm.keyLength()).isEqualTo(chip.ksEnc);
        assertThat(result.macKey()).hasSize(algorithm.keyLength()).isEqualTo(chip.ksMac);
    }

    private static IcaoPaceChipSimulator chip(PaceAlgorithm algorithm, PaceParameterId parameterId) {
        byte[] kPi = PaceMrz.kdf(PaceMrz.encodeMrzPassword(DOC, DOB, DOE), 3, algorithm.keyLength());
        return IcaoPaceChipSimulator.random(parameterId, algorithm, PasswordRef.MRZ, kPi);
    }

    private static PaceSession session(PaceAlgorithm algorithm, PaceParameterId parameterId) {
        return PaceSession.builder()
                .algorithm(algorithm)
                .parameterId(parameterId)
                .mrzPassword(DOC, DOB, DOE)
                .build();
    }
}
