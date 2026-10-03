package name.velikodniy.jcexpress.livecard.model;

import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Execution;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/** Runs a scenario class of {@link LifecycleBackendScenarios} through the JUnit Platform test kit on a backend. */
final class BackendRuns {

    /** The offline backends; a real card is never used here. */
    static final List<String> BACKENDS = List.of("embedded", "simulated-gp");

    private BackendRuns() {
    }

    /**
     * Runs a scenario class.
     *
     * @param scenario the scenario class
     * @param backend  {@code embedded} or {@code simulated-gp}
     * @return what failed: the failures of tests and classes
     */
    static List<Throwable> failures(Class<?> scenario, String backend) {
        if (!BACKENDS.contains(backend)) {
            throw new IllegalArgumentException("Offline backends only: " + backend);
        }
        LifecycleBackendScenarios.SEEN.clear();
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .configurationParameters(Map.of(OnlyInTestKit.PARAMETER, "true", "jcx.backend", backend))
                .selectors(selectClass(scenario)).execute();
        return results.allEvents().executions().failed().stream().map(Execution::getTerminationInfo)
                .map(info -> info.getExecutionResult().getThrowable().orElseThrow()).toList();
    }

    /**
     * Runs a scenario class that must pass and returns its observations.
     *
     * @param scenario the scenario class
     * @param backend  {@code embedded} or {@code simulated-gp}
     * @return the observations of the scenario
     * @throws AssertionError if a test or class failed
     */
    static List<String> seen(Class<?> scenario, String backend) {
        List<Throwable> failures = failures(scenario, backend);
        if (!failures.isEmpty()) {
            AssertionError error = new AssertionError(scenario.getSimpleName() + " failed on " + backend);
            failures.forEach(error::addSuppressed);
            throw error;
        }
        return List.copyOf(LifecycleBackendScenarios.SEEN.getOrDefault(scenario.getSimpleName(), List.of()));
    }

    /**
     * Returns the transcripts attached to a failure.
     *
     * @param failure the failure
     * @return the messages of the attached transcripts
     */
    static List<String> transcripts(Throwable failure) {
        return Arrays.stream(failure.getSuppressed()).map(Throwable::getMessage)
                .filter(message -> message != null && message.startsWith("APDU exchanges")).toList();
    }
}
