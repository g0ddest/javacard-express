package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.gp.AppletInfo;
import name.velikodniy.jcexpress.livecard.CardContent;
import name.velikodniy.jcexpress.livecard.Deployment;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.junit.LiveCardTest;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestReporter;

import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LC-LIFE: the developer cycle on the real card for a CAP file made by this project's converter (port of
 * real-card step 2): deploy, exact answers equal to jCardSim's, persistence across a card reset, delete.
 * The steps build on each other and run in order.
 */
@LiveCardTest
@Order(3)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppletLifecycleLiveTest {

    /** HelloApplet's commands with the answers of the validated real-card run (step 2, 9/9 equal to jCardSim). */
    static final List<ApduCase> CASES = List.of(
            new ApduCase("hello (static array initializer)", "8001000000", "48656C6C6F2C20636172649000"),
            new ApduCase("echo 5 bytes", "8002000005010203040500", "01020304059000"),
            new ApduCase("short arithmetic 7*6+1000, 7-6", "8003070600", "0412019000"),
            new ApduCase("ISOException SW=6A81", "80046A81", "6A81"),
            new ApduCase("persistent counter #1", "8005000002", "00019000"),
            new ApduCase("persistent counter #2", "8005000002", "00029000"),
            new ApduCase("transient array fill AB", "8006AB0004", "ABABABAB9000"),
            new ApduCase("unknown INS", "80FF000000", "6D00"),
            new ApduCase("wrong CLA", "0001000000", "6E00"));

    private static Deployment hello;

    @BeforeAll
    static void deploy(LiveCard card, TestReporter reporter) {
        hello = card.deploy(TestApplet.HELLO.pkg(card.config()));
        reporter.publishEntry("CAP", hello.capSize() + " bytes, " + hello.verification());
    }

    private static AID module(LiveCard card) {
        return TestApplet.HELLO.moduleAid(card.config());
    }

    @Test
    @Order(1)
    void loadFileAndSelectableInstanceAreRegistered(LiveCard card) {
        CardContent content = card.content();

        assertThat(content.loadFile(TestApplet.HELLO.packageAid(card.config()))).isPresent();
        AppletInfo instance = content.application(module(card)).orElseThrow();
        assertThat(instance.lifeCycleState()).isEqualTo(0x07);
        assertThat(instance.privilegeBytes()).containsOnly(0);
        assertThat(instance.executableLoadFileAid()).isEqualTo(HexFormat.of().parseHex(hello.packageAid()));
    }

    @Test
    @Order(2)
    void answersExactlyLikeJCardSim(LiveCard card) {
        List<String> simulator = ApduCase.onSimulator(TestApplet.HELLO, module(card), new byte[0], CASES);
        card.session().select(module(card));
        List<String> real = ApduCase.run(card.session(), CASES);

        SoftAssertions softly = new SoftAssertions();
        for (int i = 0; i < CASES.size(); i++) {
            softly.assertThat(real.get(i)).as("card: " + CASES.get(i)).isEqualTo(CASES.get(i).expected());
            softly.assertThat(simulator.get(i)).as("jCardSim: " + CASES.get(i)).isEqualTo(CASES.get(i).expected());
        }
        softly.assertAll();
    }

    /**
     * A card reset deselects HelloApplet: a command sent before any SELECT reaches the card's default
     * application, the ISD, which answers GET DATA CPLC (HelloApplet would answer '6D00'). The persistent counter
     * survives the reset.
     */
    @Test
    @Order(3)
    void persistentCounterSurvivesACardReset(LiveCard card) {
        card.session().select(module(card));
        int before = counter(card);

        card.reset();
        APDUResponse unselected = card.session().send(0x80, 0xCA, 0x9F, 0x7F, null, 256);
        card.session().select(module(card));

        assertThat(unselected.sw()).as("GET DATA after the reset, before any SELECT").isEqualTo(0x9000);
        assertThat(unselected.data()).as("CPLC from the ISD").startsWith(0x9F, 0x7F);
        assertThat(counter(card)).isEqualTo(before + 1);
    }

    @Test
    @Order(4)
    void deleteRemovesLoadFileAndInstance(LiveCard card) {
        assertThat(card.delete(TestApplet.HELLO.packageAid(card.config()), true).sw()).isEqualTo(0x9000);

        CardContent content = card.content();
        assertThat(content.loadFile(TestApplet.HELLO.packageAid(card.config()))).isEmpty();
        assertThat(content.application(module(card))).isEmpty();
        APDUResponse select = card.session().send(0x00, 0xA4, 0x04, 0x00, module(card).toBytes(), 256);
        assertThat(select.sw()).isEqualTo(0x6A82);
    }

    private static int counter(LiveCard card) {
        APDUResponse response = card.session().send(0x80, 0x05, 0x00, 0x00, null, 2);
        assertThat(response.sw()).isEqualTo(0x9000);
        byte[] data = response.data();
        return ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
    }
}
