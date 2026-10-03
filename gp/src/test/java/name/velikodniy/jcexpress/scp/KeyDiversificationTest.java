package name.velikodniy.jcexpress.scp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Known-answer tests for {@link KeyDiversification}.
 *
 * <p>The expected key check values are the public GlobalPlatformPro test vectors
 * (TestPlaintextKeys: VISA2, EMV CPS 1.1 and "KDF3" templates with key diversification data
 * {@code 00010203040506070809}; data only), plus the external KDF3 vector published with them.</p>
 */
class KeyDiversificationTest {

    private static final String KEY_16 = "404142434445464748494A4B4C4D4E4F";
    private static final String KEY_24 = KEY_16 + "5051525354555657";
    private static final String KEY_32 = KEY_16 + "505152535455565758595A5B5C5D5E5F";
    private static final byte[] KDD = Hex.decode("00010203040506070809");

    @Test
    void visa2MatchesPublicKeyCheckValues() {
        SCPKeys keys = KeyDiversification.visa2(SCPKeys.fromMasterKey(Hex.decode(KEY_16)), KDD);

        assertThat(kcvs(keys, KeyInfo.KeyType.DES3)).isEqualTo("2BE598/58DA38/3C328E");
        assertThat(keys.keyType()).contains(KeyInfo.KeyType.DES3);
    }

    @Test
    void emvCps11MatchesPublicKeyCheckValues() {
        SCPKeys keys = KeyDiversification.emvCps11(SCPKeys.fromMasterKey(Hex.decode(KEY_16)), KDD);

        assertThat(kcvs(keys, KeyInfo.KeyType.DES3)).isEqualTo("C33013/6F4CA6/BB8179");
        assertThat(keys.keyType()).contains(KeyInfo.KeyType.DES3);
    }

    @ParameterizedTest(name = "AES-{0}")
    @CsvSource({
            "128, " + KEY_16 + ", E79C05/D1BD77/3FDE8C",
            "192, " + KEY_24 + ", 1DE8EA/47C00C/C04D76",
            "256, " + KEY_32 + ", 2972D2/036F94/5D57B8"})
    void kdf3MatchesPublicKeyCheckValues(int bits, String master, String expected) {
        SCPKeys keys = KeyDiversification.kdf3(SCPKeys.fromMasterKey(Hex.decode(master)), KDD);

        assertThat(keys.keyLength()).isEqualTo(bits / 8);
        assertThat(kcvs(keys, KeyInfo.KeyType.AES)).isEqualTo(expected);
        assertThat(keys.keyType()).contains(KeyInfo.KeyType.AES);
    }

    @Test
    void kdf3MatchesThePublishedExternalVector() {
        SCPKeys master = SCPKeys.fromMasterKey(
                Hex.decode("8C72C72CF908411653018807950D82FBAD947562F0828A0B10B8B9606ABF3BCD"));

        SCPKeys keys = KeyDiversification.kdf3(master, Hex.decode("D9B1DE5D0362DEDCE4FB"));

        assertThat(Hex.encode(keys.enc()))
                .isEqualTo("9AAC5D0B3601F89438A0D9D0B6B256CFB47E6462DFA5228D3420C4AC7C224781");
    }

    @Test
    void kdf3IsNotTheScp03SessionKeyDerivation() {
        // the session KDF of Amendment D 4.1.5 with the diversification data as context gives these values
        SCPKeys keys = KeyDiversification.kdf3(SCPKeys.fromMasterKey(Hex.decode(KEY_16)), KDD);

        assertThat(Hex.encode(KeyInfo.kcvAes(keys.enc()))).isNotEqualTo("C25559");
    }

    @Test
    void kdf3RejectsTripleDesMasterKeys() {
        SCPKeys des = SCPKeys.des3(Hex.decode(KEY_16), Hex.decode(KEY_16), Hex.decode(KEY_16));

        assertThatThrownBy(() -> KeyDiversification.kdf3(des, KDD))
                .isInstanceOf(SCPException.class)
                .hasMessageContaining("AES");
    }

    @Test
    void diversificationDataMustBeTenBytes() {
        SCPKeys master = SCPKeys.fromMasterKey(Hex.decode(KEY_16));

        assertThatThrownBy(() -> KeyDiversification.visa2(master, null)).isInstanceOf(SCPException.class);
        assertThatThrownBy(() -> KeyDiversification.emvCps11(master, null)).isInstanceOf(SCPException.class);
        assertThatThrownBy(() -> KeyDiversification.kdf3(master, null)).isInstanceOf(SCPException.class);
        assertThatThrownBy(() -> KeyDiversification.visa2(master, new byte[5]))
                .isInstanceOf(SCPException.class)
                .hasMessageContaining("10");
    }

    private static String kcvs(SCPKeys keys, KeyInfo.KeyType type) {
        return Hex.encode(KeyInfo.kcv(keys.enc(), type)) + "/" + Hex.encode(KeyInfo.kcv(keys.mac(), type)) + "/"
                + Hex.encode(KeyInfo.kcv(keys.dek(), type));
    }
}
