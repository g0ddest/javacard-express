package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CPLCData} parsing.
 */
class CPLCDataTest {

    // 38-byte CPLC: all mandatory fields
    // IC Fabricator(2) + IC Type(2) + OS ID(2) + OS Release Date(2) + OS Release Level(2)
    // + IC Fabrication Date(2) + IC Serial Number(4) + IC Batch ID(2) + IC Module Fabricator(2)
    // + IC Module Packaging Date(2) + ICC Manufacturer(2) + IC Embedding Date(2)
    // + IC Pre-Personalizer(2) + IC Pre-Personalization Date(2) + IC Pre-Personalization Equip(2)
    // + IC Personalizer(2) + IC Personalization Date(2) + IC Personalization Equip(2)
    private static final String CPLC_38 =
            "4790"           // IC Fabricator
          + "6354"           // IC Type
          + "4700"           // OS ID
          + "3210"           // OS Release Date
          + "3F00"           // OS Release Level
          + "5123"           // IC Fabrication Date
          + "DEADBEEF"       // IC Serial Number (4 bytes)
          + "1234"           // IC Batch ID
          + "0066"           // IC Module Fabricator
          + "5200"           // IC Module Packaging Date
          + "0033"           // ICC Manufacturer
          + "5210"           // IC Embedding Date
          + "0011"           // IC Pre-Personalizer
          + "5220"           // IC Pre-Personalization Date
          + "AABB"           // IC Pre-Personalization Equipment
          + "0022"           // IC Personalizer
          + "5230"           // IC Personalization Date
          + "CCDD";          // IC Personalization Equipment

    @Test
    void shouldParseCPLCResponse() {
        byte[] data = Hex.decode(CPLC_38);

        CPLCData cplc = CPLCData.parse(data);

        assertThat(cplc.icFabricator()).isEqualTo(0x4790);
        assertThat(cplc.icType()).isEqualTo(0x6354);
        assertThat(cplc.osId()).isEqualTo(0x4700);
        assertThat(cplc.osReleaseDate()).isEqualTo(0x3210);
        assertThat(cplc.osReleaseLevel()).isEqualTo(0x3F00);
        assertThat(cplc.icFabricationDate()).isEqualTo(0x5123);
        assertThat(cplc.icSerialNumber()).isEqualTo(Hex.decode("DEADBEEF"));
        assertThat(cplc.icBatchId()).isEqualTo(0x1234);
        assertThat(cplc.icModuleFabricator()).isEqualTo(0x0066);
        assertThat(cplc.icModulePackagingDate()).isEqualTo(0x5200);
        assertThat(cplc.iccManufacturer()).isEqualTo(0x0033);
        assertThat(cplc.icEmbeddingDate()).isEqualTo(0x5210);
        assertThat(cplc.icPrePersonalizer()).isEqualTo(0x0011);
        assertThat(cplc.icPrePersonalizationDate()).isEqualTo(0x5220);
        assertThat(cplc.icPrePersonalizationEquipId()).isEqualTo(0xAABB);
        assertThat(cplc.icPersonalizer()).isEqualTo(0x0022);
        assertThat(cplc.icPersonalizationDate()).isEqualTo(0x5230);
        assertThat(cplc.icPersonalizationEquipId()).isEqualTo(0xCCDD);
    }

    @Test
    void shouldParseWithTagWrapper() {
        byte[] raw = Hex.decode(CPLC_38);
        // Wrap in 9F7F tag: 9F 7F len data
        byte[] wrapped = new byte[3 + raw.length];
        wrapped[0] = (byte) 0x9F;
        wrapped[1] = (byte) 0x7F;
        wrapped[2] = (byte) raw.length;
        System.arraycopy(raw, 0, wrapped, 3, raw.length);

        CPLCData cplc = CPLCData.parse(wrapped);

        assertThat(cplc.icFabricator()).isEqualTo(0x4790);
        assertThat(cplc.icSerialNumber()).isEqualTo(Hex.decode("DEADBEEF"));
    }

    @Test
    void shouldParseWithExtendedLengthTagWrapper() {
        byte[] raw = Hex.decode(CPLC_38);
        // Wrap in 9F7F tag with 0x81 length encoding: 9F 7F 81 len data
        byte[] wrapped = new byte[4 + raw.length];
        wrapped[0] = (byte) 0x9F;
        wrapped[1] = (byte) 0x7F;
        wrapped[2] = (byte) 0x81;
        wrapped[3] = (byte) raw.length;
        System.arraycopy(raw, 0, wrapped, 4, raw.length);

        CPLCData cplc = CPLCData.parse(wrapped);

        assertThat(cplc.icFabricator()).isEqualTo(0x4790);
    }

    @Test
    void shouldRejectTooShortResponse() {
        byte[] tooShort = Hex.decode("47906354470032103F00512300");

        assertThatThrownBy(() -> CPLCData.parse(tooShort))
                .isInstanceOf(GPException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void shouldExtractSerialNumber() {
        CPLCData cplc = CPLCData.parse(Hex.decode(CPLC_38));

        assertThat(cplc.serialNumberHex()).isEqualTo("DEADBEEF");
    }

    @Test
    void formatDateShouldReturnHexString() {
        assertThat(CPLCData.formatDate(0x3210)).isEqualTo("3210");
        assertThat(CPLCData.formatDate(0x5123)).isEqualTo("5123");
        assertThat(CPLCData.formatDate(0x0000)).isEqualTo("0000");
        assertThat(CPLCData.formatDate(0xFFFF)).isEqualTo("FFFF");
    }

    @Test
    void toStringShouldContainFields() {
        CPLCData cplc = CPLCData.parse(Hex.decode(CPLC_38));

        String s = cplc.toString();
        assertThat(s).contains("CPLC");
        assertThat(s).contains("4790");      // fabricator
        assertThat(s).contains("DEADBEEF");  // serial
    }
}
