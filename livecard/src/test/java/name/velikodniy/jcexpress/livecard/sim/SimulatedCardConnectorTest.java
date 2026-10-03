package name.velikodniy.jcexpress.livecard.sim;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The simulated card in the test reader reads its applets' class path when a package is loaded
 * ({@link SimulatedApplets#classPath()}), so applets built after the card was inserted (the third-party applets,
 * compiled on first use) run on it whatever the order of the test classes.
 */
class SimulatedCardConnectorTest {

    @Test
    void aClassPathEntryAddedAfterTheCardWasInsertedIsUsedAtTheNextLoad() {
        List<Path> classPath = new ArrayList<>(List.of(Path.of("applet-classes")));
        SimulatedApplets applets = SimulatedCardConnector.applets(module -> Optional.empty(), () -> classPath);

        classPath.add(Path.of("third-party/PivApplet/classes"));

        assertThat(applets.classPath()).containsExactly(Path.of("applet-classes"),
                Path.of("third-party/PivApplet/classes"));
    }

    @Test
    void appletClassesAreFoundThroughTheGivenLookup() {
        SimulatedApplets applets = SimulatedCardConnector.applets(
                module -> module.equals("F001") ? Optional.of("com.example.Applet") : Optional.empty(), List::of);

        assertThat(applets.className("F001")).contains("com.example.Applet");
        assertThat(applets.className("F002")).isEmpty();
    }
}
