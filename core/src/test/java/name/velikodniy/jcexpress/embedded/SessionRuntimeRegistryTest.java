package name.velikodniy.jcexpress.embedded;

import com.licel.jcardsim.smartcardio.CardSimulator;
import com.licel.jcardsim.utils.AIDUtil;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.AppletInstallParameters;
import name.velikodniy.jcexpress.embedded.gp.CardApplication;
import name.velikodniy.jcexpress.fakes.gp.CardStateApplet;
import name.velikodniy.jcexpress.fakes.gp.FixedAidApplet;
import org.globalplatform.GPRegistryEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the runtime of an embedded session tells the card's GlobalPlatform Registry about an installation
 * (GlobalPlatform Card Specification v2.3.1 Annex A.1, Installation: the install parameters carry the instance
 * AID, the Privileges and the application specific parameters).
 */
class SessionRuntimeRegistryTest {

    private static final AID INSTANCE = AID.fromHex("F0000000040101");

    @Test
    void thePrivilegesOfTheInstallParametersEnterTheRegistry_gpcs_A_1() {
        SessionRuntime runtime = new SessionRuntime();
        CardSimulator simulator = new CardSimulator(runtime);
        byte[] parameters = AppletInstallParameters.encode(INSTANCE, new byte[]{0x10, 0x04, 0x00}, new byte[0]);

        simulator.loadApplet(AIDUtil.create(INSTANCE.toBytes()), CardStateApplet.class);
        runtime.installApplet(AIDUtil.create(INSTANCE.toBytes()), parameters, (short) 0, (byte) parameters.length);

        CardApplication application = runtime.globalPlatform().application(INSTANCE).orElseThrow();
        assertThat(application.privileges()).containsExactly(0x10, 0x04, 0x00);
        assertThat(application.hasPrivilege(GPRegistryEntry.PRIVILEGE_CARD_LOCK)).isTrue();
        assertThat(application.hasPrivilege(GPRegistryEntry.PRIVILEGE_GLOBAL_REGISTRY)).isTrue();
        assertThat(application.hasPrivilege(GPRegistryEntry.PRIVILEGE_CARD_TERMINATE)).isFalse();
    }

    @Test
    void anAppletThatRegistersUnderAnotherAidIsInTheRegistryUnderThatAid() {
        AID registered = AID.fromHex("F00000000A0A0A");
        try (EmbeddedSession card = new EmbeddedSession()) {
            card.installWithoutSelecting(FixedAidApplet.class, INSTANCE, new byte[0]);

            assertThat(card.globalPlatform().applications()).extracting(CardApplication::aid).containsExactly(registered);
            assertThat(card.globalPlatform().application(registered).orElseThrow().state())
                    .isEqualTo(CardApplication.SELECTABLE);
            card.select(registered);
            assertThat(card.send(0x80, 0x01).requireSuccess().u8(0)).isEqualTo(0x07);
        }
    }
}
