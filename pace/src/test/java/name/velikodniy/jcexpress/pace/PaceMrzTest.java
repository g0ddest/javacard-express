package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.sm.SMKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PaceMrz}: MRZ_information, the password encoding K = f(pi) of ICAO Doc 9303-11 9.7.3
 * (Table 14), the BAC key seed (App. D.2) and the KDF (9.7.1). Expected values are from the ICAO worked
 * examples App. D.1, D.2 and G.
 */
class PaceMrzTest {

    @Test
    void checkDigitsOfIcaoAppendixD2() {
        assertThat(PaceMrz.checkDigit("L898902C<")).isEqualTo(3);
        assertThat(PaceMrz.checkDigit("690806")).isEqualTo(1);
        assertThat(PaceMrz.checkDigit("940623")).isEqualTo(6);
        assertThat(PaceMrz.checkDigit("D23145890734")).isEqualTo(9);
    }

    @Test
    void checkDigitForLettersShouldWork() {
        // A=10, B=11, ..., Z=35; weights 7, 3, 1
        assertThat(PaceMrz.checkDigit("A")).isZero();
        assertThat(PaceMrz.checkDigit("B")).isEqualTo(7);
    }

    @Test
    void checkDigitForFillerShouldBeZero() {
        assertThat(PaceMrz.checkDigit("<")).isZero();
        assertThat(PaceMrz.checkDigit("<<<")).isZero();
    }

    @Test
    void mrzInformationOfIcaoAppendixD2() {
        assertThat(PaceMrz.mrzInformation("L898902C<", "690806", "940623")).isEqualTo("L898902C<369080619406236");
    }

    @Test
    void documentNumberShorterThanNineCharactersIsPaddedWithFillers() {
        // The MRZ field has 9 characters; 'L898902C' is printed as 'L898902C<' (App. D.2)
        assertThat(PaceMrz.mrzInformation("L898902C", "690806", "940623")).isEqualTo("L898902C<369080619406236");
        assertThat(Hex.encode(PaceMrz.encodeMrzPassword("L898902C", "690806", "940623")))
                .isEqualTo("239AB9CB282DAF66231DC5A4DF6BFBAEDF477565");
    }

    @Test
    void trailingFillersAreNormalisedToTheNineCharacterField() {
        assertThat(PaceMrz.mrzInformation("L898902C<<<", "690806", "940623")).isEqualTo("L898902C<369080619406236");
        assertThat(PaceMrz.mrzInformation("D23145890734<", "340712", "950712"))
                .isEqualTo("D23145890734934071279507122");
    }

    @Test
    void documentNumberLongerThanNineCharactersIsUsedCompletely() {
        // App. D.2 TD1/TD2 example and 9.7.3 Note: the complete document number is used
        assertThat(PaceMrz.mrzInformation("D23145890734", "340712", "950712"))
                .isEqualTo("D23145890734934071279507122");
    }

    @Test
    void bacKeySeedIsTheFirst16BytesOfTheMrzHash() {
        // App. D.2 steps 3-4
        assertThat(Hex.encode(PaceMrz.bacKeySeed("L898902C<", "690806", "940623")))
                .isEqualTo("239AB9CB282DAF66231DC5A4DF6BFBAE");
    }

    @Test
    void kdfOfIcaoAppendixD1() {
        // App. D.1: KDF(Kseed, 1) and KDF(Kseed, 2) before parity adjustment
        byte[] kSeed = Hex.decode("239AB9CB282DAF66231DC5A4DF6BFBAE");

        assertThat(Hex.encode(PaceMrz.kdf(kSeed, 1, 16))).isEqualTo("AB94FCEDF2664EDFB9B291F85D7F77F2");
        assertThat(Hex.encode(PaceMrz.kdf(kSeed, 2, 16))).isEqualTo("7862D9ECE03C1BCD4D77089DCF131442");
    }

    @Test
    void mrzPasswordEncodingAndKeyOfIcaoAppendixG() {
        byte[] k = PaceMrz.encodeMrzPassword("T22000129", "640812", "101031");

        assertThat(Hex.encode(k)).isEqualTo("7E2D2A41C74EA0B38CD36F863939BFA8E9032AAD");
        assertThat(Hex.encode(PaceMrz.kdf(k, 3, 16))).isEqualTo("89DED1B26624EC1E634C1989302849DD");
    }

    @Test
    @SuppressWarnings("deprecation")
    void computeKSeedIsTheMrzPasswordEncoding() {
        assertThat(PaceMrz.computeKSeed("L898902C", "690806", "940623"))
                .isEqualTo(PaceMrz.encodeMrzPassword("L898902C<", "690806", "940623"));
    }

    @Test
    void kdfUsesSha256ForLongerKeys() {
        // 9.7.1.2: SHA-256 for 192- and 256-bit AES keys
        byte[] k = PaceMrz.encodeMrzPassword("T22000129", "640812", "101031");
        SMKeys keys = PaceMrz.deriveKeys(k, 32);

        assertThat(keys.encKey()).hasSize(32);
        assertThat(keys.macKey()).hasSize(32).isNotEqualTo(keys.encKey());
        assertThat(PaceMrz.kdf(k, 1, 24)).isEqualTo(Arrays.copyOf(PaceMrz.kdf(k, 1, 32), 24));
    }

    @ParameterizedTest(name = "[{0}] [{1}] [{2}]")
    @CsvSource({
            "l898902c, 690806, 940623",   // lower case is not an MRZ character
            "<<<<<<<<<, 690806, 940623",  // only fillers
            "L8989<02C, 690806, 940623",  // filler inside the number
            "'', 690806, 940623",
            "L898902C, 69080, 940623",    // date with 5 characters
            "L898902C, 690806, 9406231",  // date with 7 characters
            "L898902C, 6908O6, 940623",   // letter O in a date
    })
    void invalidMrzFieldsAreRejected(String documentNumber, String dateOfBirth, String dateOfExpiry) {
        assertThatThrownBy(() -> PaceMrz.mrzInformation(documentNumber, dateOfBirth, dateOfExpiry))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownDateComponentsMayBeFillers() {
        // Doc 9303-3: unknown parts of a date are replaced by filler characters
        assertThat(PaceMrz.mrzInformation("L898902C", "6908<<", "940623")).startsWith("L898902C<36908<<");
    }

    @Test
    void nullDocNumberShouldThrow() {
        assertThatThrownBy(() -> PaceMrz.mrzInformation(null, "690806", "940623"))
                .isInstanceOf(NullPointerException.class);
    }
}
