package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.gp.AppletInfo;
import name.velikodniy.jcexpress.gp.CAPFile;
import name.velikodniy.jcexpress.gp.GPSession;
import name.velikodniy.jcexpress.livecard.CardContent;
import name.velikodniy.jcexpress.livecard.Deployment;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.junit.LiveCardTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HexFormat;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LC-GP: an applet that uses the GlobalPlatform Card API ({@code org.globalplatform}) on the card. Its CAP file
 * imports the package from the converter's built-in linking data, in version 1.0, the lowest that has everything
 * the applet uses (JCVM 3.1 4.5.2). The applet passes INITIALIZE UPDATE and EXTERNAL AUTHENTICATE to its Security
 * Domain, the Issuer Security Domain, so the host opens the session through the applet with the ISD keys (GPCS
 * v2.3.1 7.3.1) at the security levels of the setting {@code securityLevels}. Such a session is an authentication
 * of the card's key set: the guard verifies the card cryptogram itself before it lets EXTERNAL AUTHENTICATE
 * through, exactly as for the ISD. The steps build on each other and run in order.
 */
@LiveCardTest
@Order(9)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlobalPlatformApiLiveTest {

    private static final byte[] VALUE = {0x0A, 0x0B, 0x0C, 0x0D};
    /** package_info of the Import component (JCVM 3.1 6.7): minor 0, major 1, AID of org.globalplatform. */
    private static final String IMPORT_OF_VERSION_1_0 = "000106A00000015100";

    private static Deployment vault;

    @BeforeAll
    static void deploy(LiveCard card, TestReporter reporter) {
        vault = card.deploy(TestApplet.GP_API.pkg(card.config()));
        reporter.publishEntry("CAP", vault.capSize() + " bytes, " + vault.verification());
    }

    /**
     * The configured levels, in their order.
     *
     * @param card the connected card (its settings)
     * @return level and its hex name
     */
    static Stream<Arguments> securityLevels(LiveCard card) {
        return card.config().securityLevels().stream().map(level -> Arguments.of(level, String.format("%02X", level)));
    }

    private static AID module(LiveCard card) {
        return TestApplet.GP_API.moduleAid(card.config());
    }

    @Test
    @Order(1)
    void loadedCapFileImportsVersion10OfThePackage() {
        String loadFile = HexFormat.of().withUpperCase().formatHex(CAPFile.fromFile(vault.capFile()).loadFileData());

        assertThat(loadFile).as("the Import component names org.globalplatform 1.0").contains(IMPORT_OF_VERSION_1_0);
    }

    @Test
    @Order(2)
    void withoutASessionTheAppletRefusesTheValue(LiveCard card) {
        card.session().select(module(card));

        assertThat(card.session().send(0x80, 0x14, 0, 0, null, 1).data()).as("life cycle state SELECTABLE")
                .containsExactly(0x07);
        assertThat(card.session().send(0x80, 0x10, 0, 0, VALUE).sw()).as("not authenticated").isEqualTo(0x6982);
    }

    @Order(3)
    @ParameterizedTest(name = "security level {1}")
    @MethodSource("securityLevels")
    void hostOpensTheSecureChannelThroughTheApplet(int level, String name, LiveCard card) {
        card.session().select(module(card));
        int verified = card.session().guard().verifiedHandshakes();
        card.note("secure channel through the applet " + module(card).toHex() + " at security level " + name);

        try (GPSession channel = GPSession.on(card.session()).keys(card.config().keys().toScpKeys())
                .keyVersion(card.config().keyVersion()).securityLevel(level).open()) {
            assertThat(card.session().guard().verifiedHandshakes()).as("verified by the guard").isEqualTo(verified + 1);
            assertThat(channel.cardInfo().scpVersion()).isEqualTo(3);
            assertThat(channel.secureChannel().securityLevel()).isEqualTo(level);
            assertThat(channel.send(0x80, 0x10, 0, 0, VALUE).sw()).as("the value, checked by unwrap").isEqualTo(0x9000);
            assertThat(channel.send(0x80, 0x12, 0, 0, null, 256).data()).as("the value, through wrap")
                    .isEqualTo(VALUE);
            assertThat(channel.send(0x80, 0x14, 0, 0, null, 256).data()).as("application specific state")
                    .containsExactly(0x0F);
        }
    }

    /** GPCS v2.3.1 5.3.1: the state an application gives itself is the one the registry reports (GET STATUS). */
    @Test
    @Order(4)
    void registryReportsTheStateTheAppletGaveItself(LiveCard card) {
        AppletInfo instance = card.content().application(module(card)).orElseThrow();

        assertThat(instance.lifeCycleState()).isEqualTo(0x0F);
    }

    @Test
    @Order(5)
    void deleteRemovesLoadFileAndInstance(LiveCard card) {
        assertThat(card.delete(TestApplet.GP_API.packageAid(card.config()), true).sw()).isEqualTo(0x9000);

        CardContent content = card.content();
        assertThat(content.loadFile(TestApplet.GP_API.packageAid(card.config()))).isEmpty();
        assertThat(content.application(module(card))).isEmpty();
    }
}
