package name.velikodniy.jcexpress.livecard.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which applet is selected when, and how often the runtime calls an applet's {@code select()} and {@code deselect()},
 * is the same on jCardSim and on the simulated GlobalPlatform card: a declared install selects nothing (INSTALL [for
 * install and make selectable] through the Issuer Security Domain, GPCS v2.3.1 11.5); card content management
 * deselects the selected applet; after the PER_CLASS installs the first PER_CLASS applet of the class is selected for
 * {@code @BeforeAll}; before a test the nearest applet is selected unless it still is, so that CLEAR_ON_DESELECT
 * memory carries over between the tests of a PER_CLASS instance; and {@code history()} holds the current test's
 * exchanges.
 */
class SelectionOnEveryBackendTest {

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void beforeAllTalksToTheFirstPerClassAppletAndItStaysSelected(String backend) {
        assertThat(BackendRuns.seen(LifecycleBackendScenarios.PerClassBeforeAll.class, backend)).containsExactly(
                "beforeAll true selects=1 deselects=0", "first selects=1 deselects=0 transient 5A",
                "second selects=1 deselects=0 transient 6B");
    }

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void aPerTestInstanceIsSelectedOnceBeforeItsTest(String backend) {
        assertThat(BackendRuns.seen(LifecycleBackendScenarios.PerTestCounts.class, backend)).containsExactly(
                "first selects=1 deselects=0", "second selects=2 deselects=1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void cardContentManagementDeselectsTheSelectedApplet_gpcs231_11_5(String backend) {
        assertThat(BackendRuns.seen(LifecycleBackendScenarios.Mixed.class, backend)).containsExactly(
                "first selects=2 deselects=1 transient 00", "second selects=3 deselects=2 transient 00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void theSelectionCarriesOverUntilAResetOrADeselect(String backend) {
        assertThat(BackendRuns.seen(LifecycleBackendScenarios.Tracking.class, backend)).containsExactly(
                "fci true", "kept selects=2 deselects=1 transient 11", "after reset selects=3 deselects=1",
                "after deselect selects=4 deselects=2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void historyHoldsTheExchangesOfTheCurrentTest(String backend) {
        assertThat(BackendRuns.seen(LifecycleBackendScenarios.HistoryScope.class, backend)).containsExactly(
                "beforeAll []", "first [00A40400 80130000]", "second [00A40400 80130000 80130000]", "afterAll 3");
    }

    @Test
    void theSameClassesGiveTheSameObservationsOnBothBackends() {
        for (Class<?> scenario : List.of(LifecycleBackendScenarios.PerClassBeforeAll.class,
                LifecycleBackendScenarios.PerTestCounts.class, LifecycleBackendScenarios.Mixed.class,
                LifecycleBackendScenarios.Tracking.class, LifecycleBackendScenarios.HistoryScope.class)) {
            assertThat(BackendRuns.seen(scenario, "simulated-gp")).as(scenario.getSimpleName())
                    .isEqualTo(BackendRuns.seen(scenario, "embedded"));
        }
    }
}
