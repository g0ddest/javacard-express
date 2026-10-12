package name.velikodniy.jcexpress.embedded.gp;

import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.fakes.gp.CardStateApplet;
import org.globalplatform.GPSystem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * On the embedded backend a test of a {@code @JavaCardTest} class can declare the card's {@link GlobalPlatformCard}
 * as a parameter, next to the session.
 */
@JavaCardTest
@InstallApplet(CardStateApplet.class)
class GlobalPlatformCardParameterTest {

    @Test
    void theTestSeesTheGlobalPlatformEnvironmentTheAppletSees(SmartCardSession card, GlobalPlatformCard globalPlatform) {
        globalPlatform.cardState(GPSystem.CARD_SECURED);

        assertThat(card.send(0x80, 0x34).requireSuccess().u8(0)).isEqualTo(0x0F);
        assertThat(globalPlatform.applications()).hasSize(1);
        assertThat(card.send(0x80, 0x32, 0x0F, 0x00).requireSuccess().u8(0)).isEqualTo(1);
        assertThat(globalPlatform.applications().get(0).state()).isEqualTo(0x0F);
    }
}
