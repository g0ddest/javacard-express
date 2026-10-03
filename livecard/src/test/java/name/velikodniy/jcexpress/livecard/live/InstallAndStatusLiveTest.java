package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.gp.AppletInfo;
import name.velikodniy.jcexpress.livecard.AppletInstance;
import name.velikodniy.jcexpress.livecard.CardContent;
import name.velikodniy.jcexpress.livecard.Deployment;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.junit.LiveCardTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LC-INST: GlobalPlatform card content management (port of real-card step 3): install parameters ('C9'), two
 * instances of one module, GET STATUS of applications and of load files with modules ('10'), lock and unlock
 * (SET STATUS), deleting one instance only. The steps build on each other and run in order.
 */
@LiveCardTest
@Order(4)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InstallAndStatusLiveTest {

    private static final String PARAMS_1 = "AABBCC";
    private static final String PARAMS_2 = "112233445566";

    private static AID first;
    private static AID second;

    @BeforeAll
    static void deployTwoInstances(LiveCard card) {
        AID module = TestApplet.PARAMS.moduleAid(card.config());
        first = module;
        second = TestApplet.PARAMS.instanceAid(card.config(), 0x02);
        Deployment params = card.deploy(TestApplet.PARAMS.pkg(card.config()),
                AppletInstance.of(module).withParameters(Hex.decode(PARAMS_1)));
        card.install(params, AppletInstance.of(module).as(second).withParameters(Hex.decode(PARAMS_2)));
    }

    @Test
    @Order(1)
    void bothInstancesAreSelectableWithoutPrivileges(LiveCard card) {
        CardContent content = card.content();

        for (AID instance : List.of(first, second)) {
            AppletInfo info = content.application(instance).orElseThrow();
            assertThat(info.lifeCycleState()).as(instance.toHex()).isEqualTo(0x07);
            assertThat(info.privilegeBytes()).as(instance.toHex()).containsOnly(0);
            assertThat(info.executableLoadFileAid()).isEqualTo(TestApplet.PARAMS.packageAid(card.config()).toBytes());
        }
    }

    @Test
    @Order(2)
    void loadFileListsItsModule(LiveCard card) {
        String packageAid = TestApplet.PARAMS.packageAid(card.config()).toHex();

        AppletInfo loadFile = card.loadFilesAndModules().stream()
                .filter(entry -> entry.aidHex().equals(packageAid))
                .findFirst().orElseThrow();

        assertThat(loadFile.executableModuleAids()).singleElement()
                .isEqualTo(TestApplet.PARAMS.moduleAid(card.config()).toBytes());
    }

    @Test
    @Order(3)
    void eachInstanceReceivedItsInstallParametersAndKnowsItsAid(LiveCard card) {
        assertParametersAndAid(card, first, PARAMS_1);
        assertParametersAndAid(card, second, PARAMS_2);
    }

    /** The application is unlocked again even if an assertion fails, so no LOCKED leftover remains. */
    @Test
    @Order(4)
    void lockedInstanceCannotBeSelectedUntilUnlocked(LiveCard card) {
        card.lock(first);
        try {
            assertThat(card.content().application(first).orElseThrow().lifeCycleState()).isEqualTo(0x87);
            assertThat(select(card, first).sw()).as("SELECT of a LOCKED application").isEqualTo(0x6A82);
        } finally {
            card.unlock(first);
        }
        assertThat(card.content().application(first).orElseThrow().lifeCycleState()).isEqualTo(0x07);
        assertThat(select(card, first).sw()).isEqualTo(0x9000);
    }

    @Test
    @Order(5)
    void deletingOneInstanceKeepsTheOtherAndTheLoadFile(LiveCard card) {
        card.delete(second, false);

        CardContent content = card.content();
        assertThat(content.application(second)).isEmpty();
        assertThat(content.application(first)).isPresent();
        assertThat(content.loadFile(TestApplet.PARAMS.packageAid(card.config()))).isPresent();
    }

    private static void assertParametersAndAid(LiveCard card, AID instance, String parameters) {
        card.session().select(instance);
        APDUResponse params = card.session().send(0x80, 0x01, 0x00, 0x00, null, 256);
        APDUResponse aid = card.session().send(0x80, 0x02, 0x00, 0x00, null, 256);

        assertThat(params.dataAsHex() + String.format("%04X", params.sw())).isEqualTo(parameters + "9000");
        assertThat(aid.dataAsHex() + String.format("%04X", aid.sw())).isEqualTo(instance.toHex() + "9000");
    }

    private static APDUResponse select(LiveCard card, AID instance) {
        return card.session().send(0x00, 0xA4, 0x04, 0x00, instance.toBytes(), 256);
    }
}
