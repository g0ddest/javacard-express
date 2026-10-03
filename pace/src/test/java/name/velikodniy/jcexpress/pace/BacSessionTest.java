package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.sm.SMAlgorithm;
import name.velikodniy.jcexpress.sm.SMContext;
import name.velikodniy.jcexpress.sm.SMKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Basic Access Control (ICAO Doc 9303-11, 4.3) against {@link IcaoBacChipSimulator}, an independent chip side with
 * random nonces and keys. The byte-exact exchange is checked against App. D in {@link IcaoAppendixDBacTest}.
 */
class BacSessionTest {

    /** MRZ of ICAO Doc 9303-11 App. D.2 (TD3 example). */
    private static final String MRZ_INFORMATION = "L898902C<369080619406236";

    @Test
    void establishesTheSameSessionKeysAndSscAsTheChip() {
        IcaoBacChipSimulator chip = IcaoBacChipSimulator.forMrzInformation(MRZ_INFORMATION);

        BacResult result = icaoMrzSession().perform(chip);

        assertThat(chip.authenticated).isTrue();
        // the simulator skips the OPTIONAL parity adjustment (9.7.1.1): same DES keys, other parity bits
        assertThat(withoutParity(result.encKey())).isEqualTo(withoutParity(chip.ksEnc));
        assertThat(withoutParity(result.macKey())).isEqualTo(withoutParity(chip.ksMac));
        assertThat(result.ssc()).isEqualTo(chip.ssc);
    }

    @Test
    void sendsGetChallengeAndExternalAuthenticateAsSpecified() {
        // 4.3.4: GET CHALLENGE '00 84 00 00' Le '08'; EXTERNAL AUTHENTICATE '00 82 00 00' with 40 bytes, Le '28'
        IcaoBacChipSimulator chip = IcaoBacChipSimulator.forMrzInformation(MRZ_INFORMATION);

        icaoMrzSession().perform(chip);

        assertThat(chip.commands).extracting(IcaoBacChipSimulator.Command::ins).containsExactly(0x84, 0x82);
        assertThat(chip.commands).extracting(IcaoBacChipSimulator.Command::cla).containsOnly(0x00);
        assertThat(chip.commands).extracting(IcaoBacChipSimulator.Command::le).containsExactly(8, 40);
        assertThat(chip.commands.get(1).data()).hasSize(40);
    }

    @Test
    void documentNumberWithoutFillerIsPaddedLikeTheMrz() {
        IcaoBacChipSimulator chip = IcaoBacChipSimulator.forMrzInformation(MRZ_INFORMATION);

        BacSession.builder().mrz("L898902C", "690806", "940623").build().perform(chip);

        assertThat(chip.authenticated).isTrue();
    }

    @Test
    void resultStartsTwoKeyTripleDesSecureMessagingWithTheBacSsc() {
        IcaoBacChipSimulator chip = IcaoBacChipSimulator.forMrzInformation(MRZ_INFORMATION);
        BacResult result = icaoMrzSession().perform(chip);

        SMContext context = result.toSMContext();

        assertThat(context.algorithm()).isEqualTo(SMAlgorithm.DES3);
        assertThat(context.ssc()).isEqualTo(chip.ssc);
        assertThat(context.encKey()).isEqualTo(result.encKey());
        assertThat(context.macKey()).isEqualTo(result.macKey());
    }

    @Test
    void wrongMrzIsRejectedByTheChip() {
        IcaoBacChipSimulator chip = IcaoBacChipSimulator.forMrzInformation(MRZ_INFORMATION);
        BacSession wrongExpiry = BacSession.builder().mrz("L898902C<", "690806", "940624").build();

        assertThatThrownBy(() -> wrongExpiry.perform(chip))
                .isInstanceOf(BacException.class)
                .hasMessageContaining("EXTERNAL AUTHENTICATE")
                .hasMessageContaining("6300");
        assertThat(chip.authenticated).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "SHORT_CHALLENGE, GET CHALLENGE",
            "SHORT_RESPONSE, 40 bytes",
            "WRONG_CHECKSUM, M_IC",
            "WRONG_RND_IFD, RND.IFD",
            "WRONG_RND_IC, RND.IC",
    })
    void chipResponsesThatFailTheInspectionSystemChecksAreRejected(IcaoBacChipSimulator.Fault fault, String message) {
        // 4.3.1 step 4: check M_IC, decrypt E_IC, check that the chip returned RND.IFD (and RND.IC, step 3e)
        IcaoBacChipSimulator chip = IcaoBacChipSimulator.forMrzInformation(MRZ_INFORMATION).withFault(fault);

        assertThatThrownBy(() -> icaoMrzSession().perform(chip))
                .isInstanceOf(BacException.class)
                .hasMessageContaining(message);
    }

    @Test
    void accessKeysAreTwoKeyTripleDesKeys() {
        assertThatThrownBy(() -> BacSession.builder().accessKeys(new SMKeys(new byte[24], new byte[16])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16");
    }

    @Test
    void accessKeysOrMrzMustBeGiven() {
        assertThatThrownBy(() -> BacSession.builder().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void destroyedSessionCannotBePerformed() {
        IcaoBacChipSimulator chip = IcaoBacChipSimulator.forMrzInformation(MRZ_INFORMATION);
        BacSession session = icaoMrzSession();

        session.destroy();

        assertThat(session.isDestroyed()).isTrue();
        assertThatThrownBy(() -> session.perform(chip)).isInstanceOf(IllegalStateException.class);
        assertThat(chip.commands).isEmpty();
    }

    @Test
    void resultDoesNotPrintTheSessionKeys() {
        BacResult result = new BacResult(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16},
                new byte[16], new byte[8]);

        assertThat(result.toString()).doesNotContain("0102030405").contains("redacted");
    }

    private static byte[] withoutParity(byte[] key) {
        byte[] masked = key.clone();
        for (int i = 0; i < masked.length; i++) {
            masked[i] &= (byte) 0xFE;
        }
        return masked;
    }

    private static BacSession icaoMrzSession() {
        return BacSession.builder().mrz("L898902C<", "690806", "940623").build();
    }
}
