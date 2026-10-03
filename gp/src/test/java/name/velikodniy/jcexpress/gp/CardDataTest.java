package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.tlv.TLVBuilder;
import name.velikodniy.jcexpress.tlv.Tags;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CardData}: the Card Recognition Data of GPCS v2.3.1 Appendix H.2, Table H-1.
 */
class CardDataTest {

    /**
     * Card Recognition Data of the public real SCP02 card (GlobalPlatformPro log, pastebin ZQSDaJFm):
     * GP 2.1.1 ('60'), SCP02 with i=15 ('64'), Java Card 2 ('66').
     */
    private static final String REAL_CARD = "663F733D06072A864886FC6B01600C060A2A864886FC6B02020101630906072A864886FC"
            + "6B03640B06092A864886FC6B040215660C060A2B060104012A026E0102";

    @Test
    void table_h_1_allOidsOfTheRealCard() {
        CardData data = CardData.parse(Hex.decode(REAL_CARD));

        assertThat(data.oidStrings()).containsExactly("1.2.840.114283.1", "1.2.840.114283.2.2.1.1",
                "1.2.840.114283.3", "1.2.840.114283.4.2.21", "1.3.6.1.4.1.42.2.110.1.2");
        assertThat(data.rawData()).isEqualTo(Hex.decode(REAL_CARD));
    }

    @Test
    void table_h_1_cardManagementTypeAndVersionComesFromTag60() {
        CardData data = CardData.parse(Hex.decode(REAL_CARD));

        assertThat(data.gpVersion()).contains("1.2.840.114283.2.2.1.1");
    }

    @Test
    void table_h_1_secureChannelProtocolAndItsIParameterComeFromTag64() {
        CardData data = CardData.parse(Hex.decode(REAL_CARD));

        assertThat(data.scpVersions()).containsExactly("1.2.840.114283.4.2.21");
        assertThat(data.secureChannelProtocols()).containsExactly(new CardData.SecureChannelProtocol(2, 0x15));
    }

    @Test
    void table_h_1_severalSecureChannelProtocols() {
        byte[] response = TLVBuilder.create()
                .addConstructed(Tags.GP_CARD_DATA, card -> card
                        .addConstructed(Tags.GP_CARD_RECOGNITION_DATA, rec -> rec
                                .add(Tags.GP_OID, "2A864886FC6B01")
                                .addConstructed(0x64, scp -> scp.add(Tags.GP_OID, "2A864886FC6B040370"))
                                .addConstructed(0x64, scp -> scp.add(Tags.GP_OID, "2A864886FC6B040255"))))
                .build();

        CardData data = CardData.parse(response);

        assertThat(data.secureChannelProtocols()).containsExactly(new CardData.SecureChannelProtocol(3, 0x70),
                new CardData.SecureChannelProtocol(2, 0x55));
        assertThat(data.gpVersion()).isEmpty();
    }

    /** Table H-2 (format 2) and note 4: a single '64' embedding several {globalPlatform 4 scp i} OIDs. */
    @Test
    void table_h_2_oneTag64WithSeveralSecureChannelProtocols() {
        byte[] response = TLVBuilder.create()
                .addConstructed(Tags.GP_CARD_DATA, card -> card
                        .addConstructed(Tags.GP_CARD_RECOGNITION_DATA, rec -> rec
                                .add(Tags.GP_OID, "2A864886FC6B01")
                                .addConstructed(0x60, version -> version.add(Tags.GP_OID, "2A864886FC6B020203"))
                                .addConstructed(0x64, scp -> scp
                                        .add(Tags.GP_OID, "2A864886FC6B040370")
                                        .add(Tags.GP_OID, "2A864886FC6B040215"))))
                .build();

        CardData data = CardData.parse(response);

        assertThat(data.gpVersion()).contains("1.2.840.114283.2.2.3");
        assertThat(data.scpVersions()).containsExactly("1.2.840.114283.4.3.112", "1.2.840.114283.4.2.21");
        assertThat(data.secureChannelProtocols()).containsExactly(new CardData.SecureChannelProtocol(3, 0x70),
                new CardData.SecureChannelProtocol(2, 0x15));
    }

    @Test
    void missingRecognitionDataGivesEmptyResults() {
        byte[] response = TLVBuilder.create()
                .addConstructed(Tags.GP_CARD_DATA, card -> card.add(Tags.GP_IIN, "0102030405"))
                .build();

        CardData data = CardData.parse(response);

        assertThat(data.recognitionData().isEmpty()).isTrue();
        assertThat(data.oids()).isEmpty();
        assertThat(data.gpVersion()).isEmpty();
        assertThat(data.scpVersions()).isEmpty();
        assertThat(data.secureChannelProtocols()).isEmpty();
    }

    @Test
    void oidToStringDecodesBase128Arcs() {
        assertThat(CardData.oidToString(Hex.decode("2A864886FC6B01"))).isEqualTo("1.2.840.114283.1");
        assertThat(CardData.oidToString(Hex.decode("550403"))).isEqualTo("2.5.4.3");
        assertThat(CardData.oidToString(new byte[0])).isEmpty();
    }

    @Test
    void toStringShouldContainInfo() {
        CardData data = CardData.parse(Hex.decode(REAL_CARD));

        assertThat(data.toString()).contains("CardData").contains("rawLength=65");
    }
}
