package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CPLCData}: the 42-byte Card Production Life Cycle data (tag '9F7F') with 4-byte equipment
 * identifiers, checked against public dumps of real cards.
 */
class CPLCDataTest {

    /** Real card, GlobalPlatformPro log (pastebin ZQSDaJFm): '9F7F' '2A' + 42 bytes, decoded by the tool. */
    private static final String REAL_CARD = "9F7F2A"
            + "4790" + "0520" + "8211" + "5300" + "0101" + "6356" + "16375307" + "1541"
            + "0000" + "0000" + "0000" + "0000" + "0000" + "0000" + "00000000" + "0000" + "0000" + "00000000";

    /** Galaxy Nexus embedded secure element CPLC as published (nelenkov.blogspot.com, 2012/08). */
    private static final String GALAXY_NEXUS = "4790" + "5044" + "4791" + "0078" + "3300" + "1017" + "08244500"
            + "4645" + "0000" + "0000" + "0000" + "0000" + "1726" + "3638" + "32343435" + "0000" + "0000"
            + "00000000";

    @Test
    void realCardCplcWithTagAndLength() {
        CPLCData cplc = CPLCData.parse(Hex.decode(REAL_CARD));

        assertThat(cplc.icFabricator()).isEqualTo(0x4790);
        assertThat(cplc.icType()).isEqualTo(0x0520);
        assertThat(cplc.osId()).isEqualTo(0x8211);
        assertThat(cplc.osReleaseDate()).isEqualTo(0x5300);
        assertThat(cplc.osReleaseLevel()).isEqualTo(0x0101);
        assertThat(cplc.icFabricationDate()).isEqualTo(0x6356);
        assertThat(cplc.serialNumberHex()).isEqualTo("16375307");
        assertThat(cplc.icBatchId()).isEqualTo(0x1541);
        assertThat(cplc.icPrePersonalizationEquipId()).isZero();
        assertThat(cplc.icPersonalizationEquipId()).isZero();
    }

    @Test
    void equipmentIdentifiersAreFourBytesAndShiftTheFieldsAfterThem() {
        CPLCData cplc = CPLCData.parse(Hex.decode(GALAXY_NEXUS));

        assertThat(cplc.icFabricator()).isEqualTo(0x4790);
        assertThat(cplc.icType()).isEqualTo(0x5044);
        assertThat(cplc.serialNumberHex()).isEqualTo("08244500");
        assertThat(cplc.icBatchId()).isEqualTo(0x4645);
        assertThat(cplc.icPrePersonalizer()).isEqualTo(0x1726);
        assertThat(cplc.icPrePersonalizationDate()).isEqualTo(0x3638);
        assertThat(cplc.icPrePersonalizationEquipId()).isEqualTo(0x32343435);
        assertThat(cplc.icPersonalizer()).isZero();
        assertThat(cplc.icPersonalizationDate()).isZero();
        assertThat(cplc.icPersonalizationEquipId()).isZero();
    }

    @Test
    void equipmentIdentifiersKeepAllFourBytes() {
        byte[] data = Hex.decode(GALAXY_NEXUS);
        System.arraycopy(Hex.decode("89ABCDEF"), 0, data, 38, 4);

        assertThat(CPLCData.parse(data).icPersonalizationEquipId()).isEqualTo(0x89ABCDEF);
    }

    @Test
    void lengthOfTheTagWrapperIsBerCoded() {
        assertThat(CPLCData.parse(Hex.decode("9F7F812A" + GALAXY_NEXUS)).icType()).isEqualTo(0x5044);
    }

    @Test
    void cplcShorterThan42BytesIsRejected() {
        assertThatThrownBy(() -> CPLCData.parse(Hex.decode(GALAXY_NEXUS.substring(0, 76))))
                .isInstanceOf(GPException.class)
                .hasMessageContaining("42");
    }

    @Test
    void wrapperLongerThanTheDataIsRejected() {
        assertThatThrownBy(() -> CPLCData.parse(Hex.decode("9F7F2B" + GALAXY_NEXUS)))
                .isInstanceOf(GPException.class);
    }

    @Test
    void formatting() {
        CPLCData cplc = CPLCData.parse(Hex.decode(REAL_CARD));

        assertThat(CPLCData.formatDate(0x6356)).isEqualTo("6356");
        assertThat(CPLCData.formatDate(0xFFFF)).isEqualTo("FFFF");
        assertThat(cplc.toString()).contains("4790").contains("16375307");
    }

    @Test
    void getCplcParsesTheRealCardResponse() {
        ScriptedCard card = ScriptedCard.scp02().thenAnswer(REAL_CARD + "9000");

        CPLCData cplc = card.open().getCPLC();

        assertThat(card.plainCommands()).containsExactly("80CA9F7F00");
        assertThat(cplc.serialNumberHex()).isEqualTo("16375307");
    }
}
