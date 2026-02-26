package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.tlv.TLVBuilder;
import name.velikodniy.jcexpress.tlv.Tags;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CardData} parsing.
 */
class CardDataTest {

    // GlobalPlatform OID: 1.2.840.114283.1 = 2A 86 48 86 FC 6B 01
    private static final byte[] GP_OID = Hex.decode("2A864886FC6B01");

    // GlobalPlatform SCP03 OID: 1.2.840.114283.4.0 = 2A 86 48 86 FC 6B 04 00
    private static final byte[] SCP03_OID = Hex.decode("2A864886FC6B0400");

    /**
     * Builds a realistic Card Data (0x66) response with Card Recognition Data.
     */
    private byte[] buildCardDataResponse() {
        return TLVBuilder.create()
                .addConstructed(Tags.GP_CARD_DATA, card -> card
                        .addConstructed(Tags.GP_CARD_RECOGNITION_DATA, rec -> rec
                                .add(Tags.GP_OID, GP_OID)
                                .addConstructed(0x60, scheme -> scheme
                                        .add(Tags.GP_OID, SCP03_OID))))
                .build();
    }

    @Test
    void shouldParseCardDataResponse() {
        byte[] response = buildCardDataResponse();

        CardData data = CardData.parse(response);

        assertThat(data.rawData()).isEqualTo(response);
        assertThat(data.recognitionData()).isNotNull();
        assertThat(data.recognitionData().isEmpty()).isFalse();
    }

    @Test
    void shouldExtractOids() {
        byte[] response = buildCardDataResponse();

        CardData data = CardData.parse(response);
        List<byte[]> oids = data.oids();

        assertThat(oids).isNotEmpty();
        assertThat(oids.get(0)).isEqualTo(GP_OID);
    }

    @Test
    void shouldConvertOidsToStrings() {
        byte[] response = buildCardDataResponse();

        CardData data = CardData.parse(response);
        List<String> oidStrings = data.oidStrings();

        assertThat(oidStrings).contains("1.2.840.114283.1");
    }

    @Test
    void shouldHandleEmptyRecognitionData() {
        // Response with 0x66 but no 0x73 inside
        byte[] response = TLVBuilder.create()
                .addConstructed(Tags.GP_CARD_DATA, card -> card
                        .add(Tags.GP_IIN, "0102030405"))
                .build();

        CardData data = CardData.parse(response);

        assertThat(data.recognitionData().isEmpty()).isTrue();
        assertThat(data.oids()).isEmpty();
    }

    @Test
    void toStringShouldContainInfo() {
        byte[] response = buildCardDataResponse();

        CardData data = CardData.parse(response);

        assertThat(data.toString()).contains("CardData");
        assertThat(data.toString()).contains("rawLength=");
    }

    @Test
    void oidToStringShouldDecodeCorrectly() {
        // GlobalPlatform OID: 1.2.840.114283.1
        assertThat(CardData.oidToString(GP_OID)).isEqualTo("1.2.840.114283.1");

        // Common Name OID: 2.5.4.3
        assertThat(CardData.oidToString(Hex.decode("550403"))).isEqualTo("2.5.4.3");
    }

    @Test
    void shouldExtractGpVersion() {
        byte[] response = buildCardDataResponse();

        CardData data = CardData.parse(response);

        assertThat(data.gpVersion()).isPresent();
        assertThat(data.gpVersion().get()).isEqualTo("1.2.840.114283.1");
    }

    @Test
    void gpVersionShouldReturnEmptyWhenNoGpOid() {
        // Build response with non-GP OID only
        byte[] response = TLVBuilder.create()
                .addConstructed(Tags.GP_CARD_DATA, card -> card
                        .addConstructed(Tags.GP_CARD_RECOGNITION_DATA, rec -> rec
                                .add(Tags.GP_OID, "550403"))) // 2.5.4.3 — not GP
                .build();

        CardData data = CardData.parse(response);

        assertThat(data.gpVersion()).isEmpty();
    }

    @Test
    void shouldExtractScpVersions() {
        byte[] response = buildCardDataResponse();

        CardData data = CardData.parse(response);
        List<String> versions = data.scpVersions();

        assertThat(versions).hasSize(1);
        assertThat(versions.get(0)).isEqualTo("1.2.840.114283.4.0");
    }

    @Test
    void scpVersionsShouldReturnEmptyWhenNoScheme() {
        // Build response without 0x60 constructed tag
        byte[] response = TLVBuilder.create()
                .addConstructed(Tags.GP_CARD_DATA, card -> card
                        .addConstructed(Tags.GP_CARD_RECOGNITION_DATA, rec -> rec
                                .add(Tags.GP_OID, GP_OID)))
                .build();

        CardData data = CardData.parse(response);

        assertThat(data.scpVersions()).isEmpty();
    }
}
