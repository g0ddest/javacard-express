package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.gp.AppletInfo;
import name.velikodniy.jcexpress.gp.GPSession;
import name.velikodniy.jcexpress.gp.KeyInfoEntry;
import name.velikodniy.jcexpress.gp.Lifecycle;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.junit.LiveCardTest;
import name.velikodniy.jcexpress.scp.GP;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LC-SC: SCP03 secure channel (GlobalPlatform Amendment D) at the security levels of the setting
 * {@code securityLevels} (default '01' C-MAC and '03' C-MAC with C-DECRYPTION, both validated on the real card),
 * each handshake verified independently by the guard, with read-only GET DATA and GET STATUS under secure
 * messaging. Level '00' runs only when listed: a card may reject it, and some cards count such an EXTERNAL
 * AUTHENTICATE as a failed attempt.
 */
@LiveCardTest
@Order(8)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecureChannelLiveTest {

    /**
     * The configured levels, in their order.
     *
     * @param card the connected card (its settings)
     * @return level and its hex name
     */
    static Stream<Arguments> securityLevels(LiveCard card) {
        return card.config().securityLevels().stream().map(level -> Arguments.of(level, String.format("%02X", level)));
    }

    @Order(3)
    @ParameterizedTest(name = "security level {1}")
    @MethodSource("securityLevels")
    void authenticatesAndReadsUnderSecureMessaging(int level, String name, LiveCard card) {
        byte[] plainCplc = cplcWithoutSecureMessaging(card);
        int verified = card.session().guard().verifiedHandshakes();
        GPSession gp = card.gp(level);
        try {
            assertThat(gp.cardInfo().scpVersion()).isEqualTo(3);
            assertThat(gp.secureChannel().securityLevel()).isEqualTo(level);
            assertThat(card.session().guard().verifiedHandshakes()).as("verified by the guard").isEqualTo(verified + 1);
            assertThat(gp.getData(0x9F, 0x7F).data()).as("CPLC read in the secure channel").isEqualTo(plainCplc);
            List<AppletInfo> isd = gp.getStatus(Lifecycle.SCOPE_ISD);
            assertThat(isd).singleElement()
                    .satisfies(entry -> assertThat(entry.aidHex()).isEqualTo(card.config().isd()));
            assertThat(gp.getLoadFiles()).allSatisfy(entry -> assertThat(entry.lifeCycleState()).isNotZero());
        } finally {
            gp.close();
        }
    }

    /** Amendment D 6.2.2.1: with random card challenges ("i" b5 = 0) every session gets a new challenge. */
    @Test
    @Order(2)
    void everySessionHasFreshChallengesAndCryptograms(LiveCard card) {
        GPSession first = card.gp(GP.SECURITY_C_MAC);
        GPSession second = card.gp(GP.SECURITY_C_MAC);

        assertThat(first.cardInfo().cardChallenge()).isNotEqualTo(second.cardInfo().cardChallenge());
        assertThat(first.cardInfo().cardCryptogram()).isNotEqualTo(second.cardInfo().cardCryptogram());
        first.close();
        second.close();
    }

    /** The Key Information Template, read under C-MAC, lists the key version the session authenticated with. */
    @Test
    @Order(1)
    void keyInformationListsTheAuthenticatedKeyVersion(LiveCard card) {
        GPSession gp = card.gp(GP.SECURITY_C_MAC);
        try {
            int version = gp.cardInfo().keyVersion();
            assertThat(gp.getKeyInformation()).extracting(KeyInfoEntry::keyVersion).contains(version);
        } finally {
            gp.close();
        }
    }

    private static byte[] cplcWithoutSecureMessaging(LiveCard card) {
        card.session().select(AID.fromHex(card.config().isd()));
        APDUResponse response = card.session().send(0x80, 0xCA, 0x9F, 0x7F, null, 256);
        assertThat(response.sw()).isEqualTo(0x9000);
        return response.data();
    }
}
