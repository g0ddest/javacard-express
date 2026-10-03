package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Known-answer tests for the SCP03 data derivation scheme
 * (GlobalPlatform Amendment D v1.1.2 section 4.1.5, NIST SP 800-108 counter mode with AES-CMAC).
 */
class SCP03KeyDerivationTest {

    private static final byte[] DEFAULT_KEY = Hex.decode("404142434445464748494A4B4C4D4E4F");

    /**
     * Session keys and cryptograms for the default key set and the context
     * host challenge C81ED3B6B3DEF556 || card challenge 86C8BD65FA1044EE, as published in
     * GlobalPlatformPro issue #98 (data only). Amd D 6.2.1 (S-ENC '04', S-MAC '06'),
     * 6.2.2.2 (card cryptogram '00'), 6.2.2.3 (host cryptogram '01').
     */
    @Test
    void amdD_4_1_5_sessionKeysAndCryptogramsMatchPublishedValues() {
        byte[] context = Hex.decode("C81ED3B6B3DEF55686C8BD65FA1044EE");
        byte[] sEnc = SCP03KeyDerivation.derive(DEFAULT_KEY, GP.SCP03_DERIVE_ENC, 128, context);
        byte[] sMac = SCP03KeyDerivation.derive(DEFAULT_KEY, GP.SCP03_DERIVE_C_MAC, 128, context);

        assertThat(Hex.encode(sEnc)).isEqualTo("3D991C5571391EB5BEF93DB2A4FD13D8");
        assertThat(Hex.encode(sMac)).isEqualTo("9BAB6C2C1EFEAC6E4F3B214ABABAC3B1");
        assertThat(Hex.encode(SCP03KeyDerivation.derive(sMac, GP.SCP03_DERIVE_CARD_CRYPTO, 64, context)))
                .isEqualTo("CF4A8EE6E34A4321");
        assertThat(Hex.encode(SCP03KeyDerivation.derive(sMac, GP.SCP03_DERIVE_HOST_CRYPTO, 64, context)))
                .isEqualTo("DC79BEA879C62D44");
    }

    /** Host cryptogram derivation vector of the Yubico .NET SDK DerivationTests (Apache-2.0, data only). */
    @Test
    void amdD_6_2_2_3_hostCryptogramMatchesYubicoVector() {
        byte[] key = Hex.decode("FC90AA67CDC5DABFD5051663045DFA23");
        byte[] context = Hex.decode("360CB43F4301B894CAAFA4DAC615236A");
        assertThat(Hex.encode(SCP03KeyDerivation.derive(key, GP.SCP03_DERIVE_HOST_CRYPTO, 64, context)))
                .isEqualTo("45330AB30BB1A079");
    }

    /**
     * Card cryptograms of the Samsung OpenSCP-Java S8 transcripts (Apache-2.0, data only) for AES-128/192/256
     * key sets. The card cryptogram is derived with S-MAC, so every value checks the L = '0080' / '00C0' /
     * '0100' session key derivation, including the second PRF iteration (counter '02') of Amd D 4.1.5.
     */
    @ParameterizedTest(name = "AES-{0}")
    @CsvSource({
            "128, F4932BA02FFC3098D172790099D28382, DC2DBE8974C8B0DE",
            "192, F4932BA02FFC3098D172790099D2838236F2E61068D56F44, 6E7C64F962A822A4",
            "256, F4932BA02FFC3098D172790099D2838236F2E61068D56F4401CC0374C25AF8CB, 8AFA7267CB63740E"
    })
    void amdD_6_2_1_sessionMacKeyOfEveryLengthReproducesCardCryptogram(int bits, String keyMac, String cryptogram) {
        byte[] context = Hex.decode("06F85B77251BF794" + "9CE033FA78E6B10D");
        byte[] sMac = SCP03KeyDerivation.derive(Hex.decode(keyMac), GP.SCP03_DERIVE_C_MAC, bits, context);

        assertThat(sMac).hasSize(bits / 8);
        assertThat(Hex.encode(SCP03KeyDerivation.derive(sMac, GP.SCP03_DERIVE_CARD_CRYPTO, 64, context)))
                .isEqualTo(cryptogram);
    }

    @ParameterizedTest(name = "L={0}")
    @CsvSource({"64, 8", "128, 16", "192, 24", "256, 32"})
    void amdD_4_1_5_outputLengthIsLBits(int bits, int expectedBytes) {
        byte[] out = SCP03KeyDerivation.derive(DEFAULT_KEY, GP.SCP03_DERIVE_ENC, bits, new byte[16]);
        assertThat(out).hasSize(expectedBytes);
    }

    @Test
    void amdD_4_1_5_lengthIsPartOfEveryPrfInput() {
        // L is part of the fixed input data, so the 192-bit output is not a prefix of the 256-bit output
        byte[] l192 = SCP03KeyDerivation.derive(DEFAULT_KEY, GP.SCP03_DERIVE_ENC, 192, new byte[16]);
        byte[] l256 = SCP03KeyDerivation.derive(DEFAULT_KEY, GP.SCP03_DERIVE_ENC, 256, new byte[16]);
        assertThat(Hex.encode(l256)).doesNotStartWith(Hex.encode(l192));
    }

    @Test
    void amdD_4_1_5_rejectsLengthsOtherThanTheFourDefinedValues() {
        assertThatThrownBy(() -> SCP03KeyDerivation.derive(DEFAULT_KEY, GP.SCP03_DERIVE_ENC, 96, new byte[16]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("96");
    }
}
