package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.gp.CPLCData;
import name.velikodniy.jcexpress.gp.CardData;
import name.velikodniy.jcexpress.gp.KeyInfoEntry;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.junit.LiveCardTest;
import name.velikodniy.jcexpress.tlv.TLV;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LC-ID: read-only identification of the card (port of the real-card probe). Only SELECT and GET DATA are sent,
 * without authentication; the results are published as report entries.
 */
@LiveCardTest
@Order(1)
class CardIdentificationLiveTest {

    @BeforeEach
    void selectIssuerSecurityDomain(LiveCard card) {
        card.session().select(AID.fromHex(card.config().isd()));
    }

    /** ISO/IEC 7816-3 8.1: TS is '3B' (direct) or '3F' (inverse convention). */
    @Test
    void answersToResetWithAnIsoAtr(LiveCard card, TestReporter reporter) {
        reporter.publishEntry("reader", card.reader());
        reporter.publishEntry("ATR", card.atr());
        reporter.publishEntry("protocol", card.protocol());

        assertThat(card.atr()).matches("3[BF]([0-9A-F]{2})+");
        assertThat(card.protocol()).isIn("T=0", "T=1");
    }

    /** GlobalPlatform Card Specification v2.3.1 11.9: the FCI names the selected Security Domain. */
    @Test
    void issuerSecurityDomainAnswersWithItsFci(LiveCard card) {
        APDUResponse response = card.session().send(0x00, 0xA4, 0x04, 0x00, Hex.decode(card.config().isd()), 256);

        assertThat(response.sw()).isEqualTo(0x9000);
        TLV fci = response.tlv().find(0x6F).orElseThrow();
        assertThat(fci.children().find(0x84).orElseThrow().valueHex()).isEqualTo(card.config().isd());
    }

    /** GET DATA '9F7F': Card Production Life Cycle data, 42 bytes. */
    @Test
    void cardProductionLifeCycleDataIs42Bytes(LiveCard card, TestReporter reporter) {
        APDUResponse response = getData(card, 0x9F, 0x7F);

        assertThat(response.sw()).isEqualTo(0x9000);
        CPLCData cplc = CPLCData.parse(response.data());
        reporter.publishEntry("CPLC", String.format("IC fabricator %04X, IC type %04X, OS %04X, OS release %04X/%04X",
                cplc.icFabricator(), cplc.icType(), cplc.osId(), cplc.osReleaseDate(), cplc.osReleaseLevel()));
    }

    /** GET DATA '0066': the Card Recognition Data announce SCP03, the protocol the guard verifies. */
    @Test
    void cardRecognitionDataAnnounceScp03(LiveCard card, TestReporter reporter) {
        APDUResponse response = getData(card, 0x00, 0x66);

        assertThat(response.sw()).isEqualTo(0x9000);
        CardData data = CardData.parse(response.data());
        reporter.publishEntry("GlobalPlatform", data.gpVersion().orElse("(not announced)"));
        reporter.publishEntry("secure channel", data.secureChannelProtocols().toString());
        assertThat(data.secureChannelProtocols()).anySatisfy(scp -> assertThat(scp.protocol()).isEqualTo(3));
    }

    /** GET DATA '00E0': the key set has ENC, MAC and DEK (identifiers 1-3) of the configured AES length. */
    @Test
    void keyInformationMatchesTheConfiguredKeys(LiveCard card, TestReporter reporter) {
        APDUResponse response = getData(card, 0x00, 0xE0);

        assertThat(response.sw()).isEqualTo(0x9000);
        List<KeyInfoEntry> keys = KeyInfoEntry.parseAll(response.data());
        reporter.publishEntry("keys", keys.toString());
        int version = card.config().keyVersion() != 0 ? card.config().keyVersion() : keys.getFirst().keyVersion();
        int length = card.config().keys().toScpKeys().keyLength();
        assertThat(keys).filteredOn(key -> key.keyVersion() == version)
                .extracting(KeyInfoEntry::keyId).contains(1, 2, 3);
        assertThat(keys).filteredOn(key -> key.keyVersion() == version).allSatisfy(key -> assertThat(key.components())
                .allSatisfy(component -> {
                    assertThat(component.keyType()).isEqualTo(KeyInfoEntry.KeyComponent.TYPE_AES);
                    assertThat(component.keyLength()).isEqualTo(length);
                }));
    }

    /** IIN, CIN and the sequence counter are optional data objects: absent ('6A88') or tag-prefixed. */
    @ParameterizedTest(name = "GET DATA {0}")
    @CsvSource({"0042, 42", "0045, 45", "00C1, C1"})
    void optionalDataObjectsAreAbsentOrWellFormed(String tag, String prefix, LiveCard card) {
        byte[] p = Hex.decode(tag);
        APDUResponse response = getData(card, p[0] & 0xFF, p[1] & 0xFF);

        assertThat(response.sw()).isIn(0x9000, 0x6A88);
        if (response.sw() == 0x9000) {
            assertThat(response.dataAsHex()).startsWith(prefix);
        }
    }

    private static APDUResponse getData(LiveCard card, int p1, int p2) {
        return card.session().send(0x80, 0xCA, p1, p2, null, 256);
    }
}
