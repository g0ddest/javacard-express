package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.tlv.TLVBuilder;
import name.velikodniy.jcexpress.tlv.Tags;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link KeyInfoEntry} parsing.
 */
class KeyInfoEntryTest {

    @Test
    void shouldParseSingleComponent() {
        // C0 value: keyId=01, keyVersion=30, keyType=80(DES3), keyLength=10(16)
        byte[] data = Hex.decode("01308010");

        KeyInfoEntry entry = KeyInfoEntry.parse(data);

        assertThat(entry.keyId()).isEqualTo(0x01);
        assertThat(entry.keyVersion()).isEqualTo(0x30);
        assertThat(entry.components()).hasSize(1);
        assertThat(entry.components().get(0).keyType()).isEqualTo(0x80);
        assertThat(entry.components().get(0).keyLength()).isEqualTo(16);
        assertThat(entry.components().get(0).isDes3()).isTrue();
        assertThat(entry.components().get(0).isAes()).isFalse();
    }

    @Test
    void shouldParseMultipleComponents() {
        // C0 value: keyId=01, keyVersion=20, one key with 3 components (Table 11-28 repeats type/length pairs)
        byte[] data = Hex.decode("0120" + "8810" + "8810" + "8810");

        KeyInfoEntry entry = KeyInfoEntry.parse(data);

        assertThat(entry.keyId()).isEqualTo(0x01);
        assertThat(entry.keyVersion()).isEqualTo(0x20);
        assertThat(entry.components()).hasSize(3);
        for (KeyInfoEntry.KeyComponent c : entry.components()) {
            assertThat(c.isAes()).isTrue();
            assertThat(c.keyLength()).isEqualTo(16);
        }
    }

    @Test
    void keyTypeNameShouldReturnReadableName() {
        assertThat(new KeyInfoEntry.KeyComponent(0x80, 16).keyTypeName()).isEqualTo("DES3");
        assertThat(new KeyInfoEntry.KeyComponent(0x88, 16).keyTypeName()).isEqualTo("AES");
        assertThat(new KeyInfoEntry.KeyComponent(0x99, 16).keyTypeName()).contains("UNKNOWN");
    }

    @Test
    void shouldParseAllFromKeyInfoTemplate() {
        // Build E0 template with 2 C0 entries
        byte[] c0_1 = Hex.decode("01308010");       // key 1, v48, DES3-128
        byte[] c0_2 = Hex.decode("02208810");       // key 2, v32, AES-128

        byte[] response = TLVBuilder.create()
                .addConstructed(Tags.GP_KEY_INFO_TEMPLATE, e0 -> e0
                        .add(Tags.GP_KEY_INFO_DATA, c0_1)
                        .add(Tags.GP_KEY_INFO_DATA, c0_2))
                .build();

        List<KeyInfoEntry> entries = KeyInfoEntry.parseAll(response);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).keyId()).isEqualTo(0x01);
        assertThat(entries.get(0).keyVersion()).isEqualTo(0x30);
        assertThat(entries.get(1).keyId()).isEqualTo(0x02);
        assertThat(entries.get(1).keyVersion()).isEqualTo(0x20);
    }

    @Test
    void shouldHandleEmptyResponse() {
        assertThat(KeyInfoEntry.parseAll(new byte[0])).isEmpty();
        assertThat(KeyInfoEntry.parseAll(null)).isEmpty();
    }

    /** GPCS v2.3.1 Table 11-29: 'FF' key type, 2-byte length, then Key Usage and Key Access with lengths. */
    @Test
    void table_11_29_extendedFormatEntryIsParsed() {
        // key 01, KVN 01, AES ('FF 88') of 16 bytes, key usage '38' (C-ENC + R-ENC, 11.1.9), access '01' (SD only)
        KeyInfoEntry entry = KeyInfoEntry.parse(Hex.decode("0101" + "FF880010" + "0138" + "0101"));

        assertThat(entry.keyId()).isEqualTo(0x01);
        assertThat(entry.keyVersion()).isEqualTo(0x01);
        assertThat(entry.components()).containsExactly(new KeyInfoEntry.KeyComponent(0x88, 16));
        assertThat(entry.components().getFirst().isAes()).isTrue();
        assertThat(entry.keyUsage()).containsExactly(0x38);
        assertThat(entry.keyAccess()).containsExactly(0x01);
    }

    @Test
    void table_11_29_extendedEntryWithSeveralComponentsAndWithoutUsageOrAccess() {
        // RSA public key: modulus N ('FF A1', 256 bytes) and exponent e ('FF A0', 3 bytes), usage/access lengths '00'
        KeyInfoEntry entry = KeyInfoEntry.parse(Hex.decode("0270" + "FFA10100" + "FFA00003" + "00" + "00"));

        assertThat(entry.components()).containsExactly(new KeyInfoEntry.KeyComponent(0xA1, 256),
                new KeyInfoEntry.KeyComponent(0xA0, 3));
        assertThat(entry.keyUsage()).isEmpty();
        assertThat(entry.keyAccess()).isEmpty();
    }

    @Test
    void table_11_28_basicEntriesHaveNoUsageOrAccess() {
        KeyInfoEntry entry = KeyInfoEntry.parse(Hex.decode("01FF8810"));

        assertThat(entry.keyUsage()).isEmpty();
        assertThat(entry.keyAccess()).isEmpty();
    }

    @Test
    void table_11_28_basicEntryWithAnIncompleteComponentIsRejected() {
        assertThatThrownBy(() -> KeyInfoEntry.parse(Hex.decode("0130801088")))
                .isInstanceOf(GPException.class)
                .hasMessageContaining("Table 11-28");
    }

    @Test
    void table_11_29_truncatedExtendedEntryIsRejected() {
        assertThatThrownBy(() -> KeyInfoEntry.parse(Hex.decode("0101FF8800")))
                .isInstanceOf(GPException.class)
                .hasMessageContaining("Table 11-29");
        assertThatThrownBy(() -> KeyInfoEntry.parse(Hex.decode("0101FF880010" + "0238")))
                .isInstanceOf(GPException.class)
                .hasMessageContaining("Table 11-29");
        assertThatThrownBy(() -> KeyInfoEntry.parse(Hex.decode("0101FF880010" + "00" + "00" + "99")))
                .isInstanceOf(GPException.class)
                .hasMessageContaining("Table 11-29");
    }

    @Test
    void table_11_29_keyInformationTemplateWithExtendedEntries() {
        byte[] response = TLVBuilder.create()
                .addConstructed(Tags.GP_KEY_INFO_TEMPLATE, e0 -> e0
                        .add(Tags.GP_KEY_INFO_DATA, Hex.decode("0101FF880010" + "0138" + "0101"))
                        .add(Tags.GP_KEY_INFO_DATA, Hex.decode("0201FF880010" + "0134" + "0101"))
                        .add(Tags.GP_KEY_INFO_DATA, Hex.decode("0301FF880010" + "0148" + "0101")))
                .build();

        List<KeyInfoEntry> entries = KeyInfoEntry.parseAll(response);

        assertThat(entries).extracting(KeyInfoEntry::keyId).containsExactly(1, 2, 3);
        assertThat(entries).allSatisfy(e -> assertThat(e.components())
                .containsExactly(new KeyInfoEntry.KeyComponent(0x88, 16)));
        assertThat(entries.get(1).keyUsage()).containsExactly(0x34);
    }

    @Test
    void shouldRejectTooShortEntry() {
        assertThatThrownBy(() -> KeyInfoEntry.parse(new byte[]{0x01, 0x02}))
                .isInstanceOf(GPException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void toStringShouldContainKeyInfo() {
        byte[] data = Hex.decode("01308010");
        KeyInfoEntry entry = KeyInfoEntry.parse(data);

        assertThat(entry.toString()).contains("id=1");
        assertThat(entry.toString()).contains("ver=48");
    }
}
