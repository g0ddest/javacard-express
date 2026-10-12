package name.velikodniy.jcexpress.embedded;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The explanation of an install that failed for a missing class tells a library the test class path lacks from
 * a package of the card platform the simulator does not implement.
 */
class MissingClassesTest {

    @Test
    void aGlobalPlatformPackageTheSimulatorLacksIsNotALibraryToDeclare() {
        String text = MissingClasses.describe("com.example.UpgradableApplet",
                Set.of("org.globalplatform.upgrade.UpgradeManager"));

        assertThat(text).contains("org.globalplatform.upgrade.UpgradeManager of the GlobalPlatform API")
                .contains("the simulator implements the package org.globalplatform only")
                .doesNotContain("test dependency");
    }

    @Test
    void aLibraryClassIsStillALibraryToDeclare() {
        String text = MissingClasses.describe("com.example.Applet", Set.of("org.example.lib.Codec"));

        assertThat(text).contains("org.example.lib.Codec").contains("test dependency")
                .doesNotContain("GlobalPlatform");
    }
}
