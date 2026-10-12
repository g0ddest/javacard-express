package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.container.applets.GlobalPlatformApplet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The simulator in the container is jCardSim alone, which has no {@code org.globalplatform} package; the
 * implementation of the core module runs in the test JVM only. An applet that uses the GlobalPlatform Card API is
 * refused before anything is sent, with the backends that run it.
 */
class GlobalPlatformApiInContainerTest {

    @Test
    void anAppletThatUsesTheGlobalPlatformApiIsRefusedWithTheWayOut() {
        assertThatThrownBy(() -> AppletClasses.of(GlobalPlatformApplet.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining(GlobalPlatformApplet.class.getName())
                .hasMessageContaining("org.globalplatform.GPSystem")
                .hasMessageContaining("EmbeddedSession")
                .hasMessageContaining("simulated-gp");
    }
}
