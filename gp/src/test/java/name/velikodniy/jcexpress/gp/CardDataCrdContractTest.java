package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.tlv.TLVBuilder;
import name.velikodniy.jcexpress.tlv.Tags;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CardData} parsing, with Card Recognition Data structured as GlobalPlatform Card
 * Specification 2.3.1 Appendix H.2 defines it (Table H-1 = format 1, Table H-2 = format 2).
 *
 * <p>Contract tests written by the core group against the Card Recognition Data tags of GPCS 2.3.1
 * Table H-1; kept at integration next to {@link CardDataTest} so both readings of the tables are
 * checked against the single {@link CardData} implementation.
 */
class CardDataCrdContractTest {

    /** {globalPlatform 1} = 1.2.840.114283.1, the OID for Card Recognition Data. */
    private static final String GP_CRD_OID = "2A864886FC6B01";

    /** {globalPlatform 2 v} with v = 2.3: 1.2.840.114283.2.2.3 (Table H-1, note 2). */
    private static final String GP_VERSION_2_3 = "2A864886FC6B020203";

    /** {globalPlatform 3} = 1.2.840.114283.3, Card Identification Scheme. */
    private static final String GP_ID_SCHEME = "2A864886FC6B03";

    /** {globalPlatform 4 scp i}: SCP03 i = '70' -> 1.2.840.114283.4.3.112. */
    private static final String SCP03_I70 = "2A864886FC6B040370";

    /** {globalPlatform 4 scp i}: SCP02 i = '15' -> 1.2.840.114283.4.2.21. */
    private static final String SCP02_I15 = "2A864886FC6B040215";

    /** Table H-1 (format 1): one OID per '64' occurrence. */
    private static byte[] format1() {
        return TLVBuilder.create()
                .addConstructed(Tags.GP_CARD_DATA, card -> card
                        .addConstructed(Tags.GP_CARD_RECOGNITION_DATA, rec -> rec
                                .add(Tags.GP_OID, GP_CRD_OID)
                                .addConstructed(0x60, t -> t.add(Tags.GP_OID, GP_VERSION_2_3))
                                .addConstructed(0x63, t -> t.add(Tags.GP_OID, GP_ID_SCHEME))
                                .addConstructed(0x64, t -> t.add(Tags.GP_OID, SCP03_I70))
                                .addConstructed(0x64, t -> t.add(Tags.GP_OID, SCP02_I15))
                                .addConstructed(0x66, t -> t.add(Tags.GP_OID, "2B0601040181E0"))))
                .build();
    }

    /** Table H-2 (format 2): one '64' occurrence holding all OIDs. */
    private static byte[] format2() {
        return TLVBuilder.create()
                .addConstructed(Tags.GP_CARD_DATA, card -> card
                        .addConstructed(Tags.GP_CARD_RECOGNITION_DATA, rec -> rec
                                .add(Tags.GP_OID, GP_CRD_OID)
                                .addConstructed(0x60, t -> t.add(Tags.GP_OID, GP_VERSION_2_3))
                                .addConstructed(0x64, t -> t
                                        .add(Tags.GP_OID, SCP03_I70)
                                        .add(Tags.GP_OID, SCP02_I15))))
                .build();
    }

    @Test
    void shouldParseCardDataResponse() {
        byte[] response = format1();

        CardData data = CardData.parse(response);

        assertThat(data.rawData()).isEqualTo(response);
        assertThat(data.recognitionData().isEmpty()).isFalse();
    }

    @Test
    void oidsAreCollectedAtAnyDepth() {
        CardData data = CardData.parse(format1());

        assertThat(data.oids()).hasSize(6);
        assertThat(data.oids().get(0)).isEqualTo(Hex.decode(GP_CRD_OID));
        assertThat(data.oidStrings()).startsWith("1.2.840.114283.1", "1.2.840.114283.2.2.3");
    }

    /** Tag '64' carries {globalPlatform 4 scp i}; '60' and '63' carry the version and the scheme. */
    @Test
    void scpVersionsAreTheOidsOfEveryTag64Format1() {
        assertThat(CardData.parse(format1()).scpVersions())
                .containsExactly("1.2.840.114283.4.3.112", "1.2.840.114283.4.2.21");
    }

    @Test
    void scpVersionsAreTheOidsOfTheSingleTag64Format2() {
        assertThat(CardData.parse(format2()).scpVersions())
                .containsExactly("1.2.840.114283.4.3.112", "1.2.840.114283.4.2.21");
    }

    @Test
    void scpVersionsAreEmptyWithoutTag64() {
        byte[] response = TLVBuilder.create()
                .addConstructed(Tags.GP_CARD_DATA, card -> card
                        .addConstructed(Tags.GP_CARD_RECOGNITION_DATA, rec -> rec
                                .add(Tags.GP_OID, GP_CRD_OID)
                                .addConstructed(0x60, t -> t.add(Tags.GP_OID, GP_VERSION_2_3))))
                .build();

        assertThat(CardData.parse(response).scpVersions()).isEmpty();
    }

    /** Table H-1 note 2: tag '60' holds {globalPlatform 2 v}, e.g. {globalPlatform 2 2 3} for GP 2.3. */
    @Test
    void gpVersionIsTheCardManagementTypeAndVersionOid() {
        assertThat(CardData.parse(format1()).gpVersion()).contains("1.2.840.114283.2.2.3");
        assertThat(CardData.parse(format2()).gpVersion()).contains("1.2.840.114283.2.2.3");
    }

    @Test
    void gpVersionIsEmptyWithoutTag60() {
        byte[] response = TLVBuilder.create()
                .addConstructed(Tags.GP_CARD_DATA, card -> card
                        .addConstructed(Tags.GP_CARD_RECOGNITION_DATA, rec -> rec
                                .add(Tags.GP_OID, GP_CRD_OID)))
                .build();

        assertThat(CardData.parse(response).gpVersion()).isEmpty();
    }

    @Test
    void shouldHandleEmptyRecognitionData() {
        byte[] response = TLVBuilder.create()
                .addConstructed(Tags.GP_CARD_DATA, card -> card
                        .add(Tags.GP_IIN, "0102030405"))
                .build();

        CardData data = CardData.parse(response);

        assertThat(data.recognitionData().isEmpty()).isTrue();
        assertThat(data.oids()).isEmpty();
        assertThat(data.scpVersions()).isEmpty();
        assertThat(data.gpVersion()).isEmpty();
    }

    @Test
    void toStringShouldContainInfo() {
        CardData data = CardData.parse(format1());

        assertThat(data.toString()).contains("CardData", "rawLength=");
    }

    @Test
    void oidToStringShouldDecodeCorrectly() {
        assertThat(CardData.oidToString(Hex.decode(GP_CRD_OID))).isEqualTo("1.2.840.114283.1");
        assertThat(CardData.oidToString(Hex.decode("550403"))).isEqualTo("2.5.4.3");
    }

    @Test
    void scpOidListIsAList() {
        List<String> versions = CardData.parse(format2()).scpVersions();
        assertThat(versions).hasSize(2);
    }
}
