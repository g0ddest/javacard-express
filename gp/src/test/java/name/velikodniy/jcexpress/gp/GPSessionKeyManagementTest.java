package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.scp.KeyInfo;
import name.velikodniy.jcexpress.scp.SCP02;
import name.velikodniy.jcexpress.scp.SCPKeys;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PUT KEY (GPCS v2.3.1 11.8, Tables 11-64 to 11-71): the key type comes from the key set, the key value is
 * encrypted with the DEK of the current channel (SCP02: 3DES-ECB with the session DEK, E.4.7; SCP03:
 * AES-CBC with the static Key-DEK, Amendment D 6.2.8) and followed by its key check value.
 *
 * <p>Expected data fields were computed with the independent spec-derived reference implementation
 * (see {@link ScpTranscript}); the SCP02 session uses the real card handshake with sequence counter
 * '009B' and Key Version Number 'FF', the SCP03 session the real JCOP4 handshake (static DEK 40..4F).</p>
 */
class GPSessionKeyManagementTest {

    private static final SCPKeys DES_KEYS = SCPKeys.des3(Hex.decode("11111111111111112222222222222222"),
            Hex.decode("33333333333333334444444444444444"), Hex.decode("55555555555555556666666666666666"));
    private static final SCPKeys AES_KEYS = SCPKeys.aes(Hex.decode("000102030405060708090A0B0C0D0E0F"),
            Hex.decode("101112131415161718191A1B1C1D1E1F"), Hex.decode("202122232425262728292A2B2C2D2E2F"));

    /** GlobalPlatformPro TestPlaintextKeys.testKeyEncryption_SCP02 (public data): key 40..4F, seq '0000'. */
    @Test
    void e_4_7_desKeyIsEncryptedWithTheSessionDekInEcbMode() {
        SCP02 channel = SCP02.from(SCPKeys.defaultKeys(),
                Hex.decode("00010203040506070809" + "0002" + "0000" + "112233445566" + "0000000000000000"));

        byte[] field = PutKeyData.keyDataField(Hex.decode("404142434445464748494A4B4C4D4E4F"),
                KeyInfo.KeyType.DES3, channel);

        assertThat(Hex.encode(field)).isEqualTo("8010" + "EFBEE6C6D99D7B70BDE9D7E927F020AF" + "03" + "8BAF47");
    }

    @Test
    void table_11_68_desKeySetOverScp02AddsAKeySetWhenTheCurrentVersionIsAFactoryVersion() {
        ScriptedCard card = ScriptedCard.scp02().thenAnswer("019000");

        card.open().putKeys(DES_KEYS, 0x01);

        assertThat(card.plainCommands()).containsExactly("80D8008143" + "01"
                + "80107213DEBAC6A31AEBF4740A011783C7AC03D2B91C"
                + "801006A3B3E1B86274ECB85DA909AF7E0B8E03E18DE2"
                + "801069A8BE9FB1F50FCDF1567A20C1DF94E303DDB736" + "00");
    }

    @Test
    void table_11_16_aesKeySetOverScp02UsesKeyTypeAesWithItsLength() {
        ScriptedCard card = ScriptedCard.scp02().thenAnswer("029000");

        card.open().putKeys(AES_KEYS, 0x02, 0x01);

        assertThat(card.plainCommands()).containsExactly("80D8018146" + "02"
                + "881110" + "6EB7D8F3988B58B91AC852DBEE5F3580" + "03C35280"
                + "881110" + "88C3AC7A8E83F89E2BACFC5D7F4C5865" + "03013808"
                + "881110" + "124162F18BE7DA3044EF3099215AEDE6" + "03840DE5" + "00");
    }

    @Test
    void amdD_6_2_8_aesKeySetOverScp03IsEncryptedWithTheStaticDek() {
        ScriptedCard card = ScriptedCard.scp03().thenAnswer("039000");

        card.open().putKeys(AES_KEYS, 0x03, 0x01);

        assertThat(card.plainCommands()).containsExactly("80D8018146" + "03"
                + "881110" + "3D0FA4B855D2A5AA4954B8B5DF582A3A" + "03C35280"
                + "881110" + "790ACCDA858B997029FA9AE50C9CD028" + "03013808"
                + "881110" + "8CAA7F589AA0CEB6350A45E70A6E435B" + "03840DE5" + "00");
    }

    @Test
    void amdD_6_2_8_aes192KeyIsPaddedToTwoBlocksAndCarriesItsClearLength() {
        ScriptedCard card = ScriptedCard.scp03().thenAnswer("049000");

        card.open().putKey(1, Hex.decode("000102030405060708090A0B0C0D0E0F1011121314151617"),
                KeyInfo.KeyType.AES, 0x04, 0x00);

        assertThat(card.plainCommands()).containsExactly("80D8000128" + "04" + "882118"
                + "3D0FA4B855D2A5AA4954B8B5DF582A3A091178493F68BAFFEC01366CE60189E2" + "0338FCB6" + "00");
    }

    @Test
    void table_11_71_desKeySetOverScp03() {
        ScriptedCard card = ScriptedCard.scp03().thenAnswer("059000");

        card.open().putKeys(DES_KEYS, 0x05, 0x01);

        assertThat(card.plainCommands()).containsExactly("80D8018143" + "05"
                + "801031E6A4F24C33D66CAEEEF57762EC698003D2B91C"
                + "8010ACCCBDCEC4C193AF6282B22628E335E103E18DE2"
                + "80100BCA2A8E8246AB1B0D859407D17D7A3603DDB736" + "00");
    }

    @Test
    void untypedKeySetsTakeTheKeyTypeOfTheChannel() {
        ScriptedCard card = ScriptedCard.scp03().thenAnswer("039000");
        SCPKeys untyped = SCPKeys.of(AES_KEYS.enc(), AES_KEYS.mac(), AES_KEYS.dek());

        card.open().putKeys(untyped, 0x03, 0x01);

        assertThat(card.plainCommands().getFirst()).startsWith("80D801814603881110");
    }

    @Test
    void table_11_65_keyVersionNumbersAreValidatedBeforeSending() {
        ScriptedCard card = ScriptedCard.scp02();
        GPSession gp = card.open();

        assertThatThrownBy(() -> gp.putKeys(DES_KEYS, 0x80, 0x01)).isInstanceOf(GPException.class);
        assertThatThrownBy(() -> gp.putKeys(DES_KEYS, 0x00, 0x01)).isInstanceOf(GPException.class);
        assertThatThrownBy(() -> gp.putKeys(DES_KEYS, 0x01, 0xFF)).isInstanceOf(GPException.class);
        assertThat(card.commandCount()).isZero();
    }

    @Test
    void invalidKeyLengthForTheKeyTypeIsRejectedBeforeSending() {
        ScriptedCard card = ScriptedCard.scp02();
        GPSession gp = card.open();

        assertThatThrownBy(() -> gp.putKey(1, new byte[32], KeyInfo.KeyType.DES3, 0x01, 0x00))
                .isInstanceOf(GPException.class)
                .hasMessageContaining("Table 11-16");
        assertThat(card.commandCount()).isZero();
    }

    @Test
    void rejectedPutKeyReportsTheStatusWord() {
        ScriptedCard card = ScriptedCard.scp02().thenAnswer("6A80");
        GPSession gp = card.open();

        assertThatThrownBy(() -> gp.putKeys(DES_KEYS, 0x01))
                .isInstanceOf(GPException.class)
                .satisfies(e -> assertThat(((GPException) e).statusWord()).isEqualTo(0x6A80));
    }
}
