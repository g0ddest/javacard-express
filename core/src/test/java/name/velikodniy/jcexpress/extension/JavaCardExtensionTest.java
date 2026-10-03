package name.velikodniy.jcexpress.extension;

import name.velikodniy.jcexpress.JavaCardExtension;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Execution;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * {@link JavaCardExtension} executed through the JUnit Platform (the way users run it): session
 * lifetime for PER_METHOD, PER_CLASS, static fields, inherited fields, {@code @Nested}, parameterized
 * and concurrent tests; field type handling; logging switches.
 *
 * <p>Lifetimes checked here: a session is open while its test (including {@code @AfterEach}) runs and
 * closed afterwards; nothing leaks.</p>
 */
class JavaCardExtensionTest {

    @BeforeEach
    void clearRecordings() {
        ExtensionScenarios.SEEN.clear();
    }

    private static EngineExecutionResults run(Class<?> scenario, Map<String, String> parameters) {
        Map<String, String> all = new HashMap<>(parameters);
        all.put(Scenario.PARAMETER, "true");
        return EngineTestKit.engine("junit-jupiter")
                .configurationParameters(all)
                .selectors(selectClass(scenario))
                .execute();
    }

    private static EngineExecutionResults run(Class<?> scenario) {
        return run(scenario, Map.of());
    }

    private static List<String> failures(EngineExecutionResults results) {
        return results.allEvents().executions().failed().stream()
                .map(Execution::getTerminationInfo)
                .map(info -> info.getExecutionResult().getThrowable().map(Throwable::toString).orElse("?"))
                .toList();
    }

    private static Set<SmartCardSession> distinct(String scenario) {
        Set<SmartCardSession> set = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(ExtensionScenarios.SEEN.getOrDefault(scenario, List.of()));
        return set;
    }

    private static void assertAllClosed(String scenario) {
        for (SmartCardSession session : distinct(scenario)) {
            assertThatThrownBy(() -> session.send(0x80, 0x01))
                    .as("session %s of scenario %s must be closed after the run", session, scenario)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }
    }

    @Test
    void perMethodLifecycleGivesEachTestItsOwnCardAndClosesIt() {
        assertThat(failures(run(ExtensionScenarios.PerMethod.class))).isEmpty();
        assertThat(distinct("PerMethod")).hasSize(2);
        assertAllClosed("PerMethod");
    }

    @Test
    void perClassLifecycleSharesOneCardBetweenBeforeAllAndAllTests() {
        assertThat(failures(run(ExtensionScenarios.PerClass.class))).isEmpty();
        assertThat(ExtensionScenarios.SEEN.get("PerClass")).hasSize(3);
        assertThat(distinct("PerClass")).hasSize(1);
        assertAllClosed("PerClass");
    }

    @Test
    void staticFieldIsInjectedBeforeBeforeAllAndClosedAfterTheClass() {
        assertThat(failures(run(ExtensionScenarios.StaticField.class))).isEmpty();
        assertThat(distinct("StaticField")).hasSize(1);
        assertAllClosed("StaticField");
    }

    @Test
    void inheritedFieldIsInjected() {
        assertThat(failures(run(ExtensionScenarios.InheritedField.class))).isEmpty();
        assertThat(distinct("InheritedField")).hasSize(1);
        assertAllClosed("InheritedField");
    }

    @Test
    void nestedTestsGetCardsForEveryInstanceAndNothingLeaks() {
        assertThat(failures(run(ExtensionScenarios.NestedClasses.class))).isEmpty();
        // outerTest: 1 outer card; innerA and innerB: a fresh outer and inner card each
        assertThat(distinct("Nested")).hasSize(5);
        assertAllClosed("Nested");
    }

    @Test
    void concurrentTestsDoNotCloseEachOthersCards() {
        EngineExecutionResults results = run(ExtensionScenarios.Parallel.class, Map.of(
                "junit.jupiter.execution.parallel.enabled", "true",
                "junit.jupiter.execution.parallel.config.strategy", "fixed",
                "junit.jupiter.execution.parallel.config.fixed.parallelism", "2"));
        assertThat(failures(results)).isEmpty();
        assertThat(distinct("Parallel")).hasSize(2);
        assertAllClosed("Parallel");
    }

    @Test
    void parameterizedInvocationsGetFreshCards() {
        assertThat(failures(run(ExtensionScenarios.Parameterized.class))).isEmpty();
        assertThat(distinct("Parameterized")).hasSize(3);
        assertAllClosed("Parameterized");
    }

    @Test
    void cardIsUsableInAfterEach() {
        assertThat(failures(run(ExtensionScenarios.AfterEachUsesCard.class))).isEmpty();
        assertAllClosed("AfterEachUsesCard");
    }

    @Test
    void logTrueWrapsTheCardInALoggingSessionThatWorks() {
        assertThat(failures(run(ExtensionScenarios.LogTrue.class))).isEmpty();
        assertAllClosed("LogTrue");
    }

    @Test
    void fieldsOfSessionSubtypesAreInjected() {
        assertThat(failures(run(ExtensionScenarios.FieldTypes.class))).isEmpty();
        assertAllClosed("FieldTypes");
    }

    @Test
    void globalLogParameterWrapsOnlyFieldsThatCanHoldALoggingSession() {
        assertThat(failures(run(ExtensionScenarios.GlobalLog.class, Map.of("jcx.log", "true")))).isEmpty();
        assertAllClosed("GlobalLog");
    }

    @Test
    void logTrueOnAnEmbeddedSessionFieldIsAClearConfigurationError() {
        assertThat(failures(run(ExtensionScenarios.LogOnEmbeddedField.class)))
                .singleElement().asString()
                .contains("ExtensionConfigurationException")
                .contains("card")
                .contains("LoggingSession");
        assertThat(ExtensionScenarios.SEEN).doesNotContainKey("LogOnEmbeddedField");
    }

    /** A parameter honours @SmartCard(log = true) as a field does; one that cannot hold the wrapper is an error. */
    @Test
    void logTrueOnAParameterWrapsItsSessionLikeAField() {
        assertThat(failures(run(ExtensionScenarios.LogParameters.class)))
                .singleElement().asString()
                .contains("@SmartCard(log = true)")
                .contains("parameter 0 (name.velikodniy.jcexpress.embedded.EmbeddedSession)")
                .contains("LoggingSession");
        assertThat(ExtensionScenarios.SEEN).containsOnlyKeys("LogParameters");
        assertAllClosed("LogParameters");
    }

    @Test
    void fieldsOfOtherTypesAreRejected() {
        assertThat(failures(run(ExtensionScenarios.WrongFieldType.class)))
                .singleElement().asString()
                .contains("ExtensionConfigurationException")
                .contains("java.lang.String");
    }

    @Test
    void persistentMemoryIsRejectedBecauseNoBackendModelsIt() {
        assertThat(failures(run(ExtensionScenarios.PersistentMemory.class)))
                .singleElement().asString()
                .contains("ExtensionConfigurationException")
                .contains("persistentMemory");
    }

    /** Following the hint must lead to a working setup: the module, not only Testcontainers, is needed. */
    @Test
    void containerModeWithoutTheContainerModuleNamesTheModule() {
        assertThat(failures(run(ExtensionScenarios.ContainerModeWithoutTheModule.class)))
                .singleElement().asString()
                .contains("ExtensionConfigurationException")
                .contains("name.velikodniy:javacard-express-container")
                .contains("Docker");
        assertThat(ExtensionScenarios.SEEN).doesNotContainKey("ContainerModeWithoutTheModule");
    }

    /** The simulated GlobalPlatform card and the real card exist only for @JavaCardTest classes (jcx.backend). */
    @Test
    void theGlobalPlatformBackendsOnAFieldAreRejectedOutsideAJavaCardTest() {
        assertThat(failures(run(ExtensionScenarios.SimulatedGpField.class)))
                .singleElement().asString()
                .contains("ExtensionConfigurationException")
                .contains("@SmartCard(mode = Mode.SIMULATED_GP)")
                .contains("@JavaCardTest")
                .contains("-Djcx.backend=simulated-gp");
        assertThat(failures(run(ExtensionScenarios.LiveCardField.class)))
                .singleElement().asString()
                .contains("@SmartCard(mode = Mode.LIVECARD)")
                .contains("-Djcx.backend=livecard");
        assertThat(ExtensionScenarios.SEEN).isEmpty();
    }

    /** A parameter receives an embedded session; a mode it cannot have is an error, not silently jCardSim. */
    @Test
    void aParameterThatAsksForAnotherBackendIsRejected() {
        EngineExecutionResults results = run(ExtensionScenarios.BackendParameters.class);

        assertThat(failures(results)).hasSize(2)
                .anySatisfy(failure -> assertThat(failure).contains("@SmartCard(mode = Mode.SIMULATED_GP)")
                        .contains("@JavaCardTest").contains("-Djcx.backend=simulated-gp"))
                .anySatisfy(failure -> assertThat(failure).contains("@SmartCard(mode = Mode.CONTAINER)")
                        .contains("field"));
        assertThat(ExtensionScenarios.SEEN).containsOnlyKeys("BackendParametersEmbedded");
    }

    @Test
    void readmeCompleteTestLifecycleWorks() {
        EngineExecutionResults results = run(ExtensionScenarios.ReadmeLifecycle.class);
        assertThat(failures(results)).isEmpty();
        assertThat(results.testEvents().succeeded().count()).isEqualTo(2);
    }

    @Test
    void scenariosAreDisabledOutsideTheTestKit() {
        Map<String, Long> skipped = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(ExtensionScenarios.PerMethod.class))
                .execute().containerEvents().skipped().stream()
                .collect(Collectors.groupingBy(e -> e.getTestDescriptor().getDisplayName(), Collectors.counting()));
        assertThat(skipped.keySet()).singleElement().asString().endsWith("PerMethod");
    }
}
