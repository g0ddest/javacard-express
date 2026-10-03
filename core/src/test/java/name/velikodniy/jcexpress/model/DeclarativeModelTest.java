package name.velikodniy.jcexpress.model;

import name.velikodniy.jcexpress.backend.AidScheme;
import name.velikodniy.jcexpress.model.built.BuiltApplet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Execution;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * The declarative model ({@code @JavaCardTest}, {@code @InstallApplet}) run through the JUnit Platform the way
 * users run it, on the default jCardSim backend: install before a scope and delete after it, per test and per
 * class isolation, method-level applets, several instances, nested classes, parameterized tests, imperative
 * installs, fields, deselection, failure transcripts, backend conditions and configuration errors.
 */
class DeclarativeModelTest {

    @BeforeEach
    void clear() {
        ModelScenarios.SEEN.clear();
    }

    private static EngineExecutionResults run(Class<?> scenario, Map<String, String> parameters) {
        Map<String, String> all = new HashMap<>(parameters);
        all.put(OnlyInTestKit.PARAMETER, "true");
        return EngineTestKit.engine("junit-jupiter").configurationParameters(all).selectors(selectClass(scenario))
                .execute();
    }

    private static EngineExecutionResults run(Class<?> scenario) {
        return run(scenario, Map.of());
    }

    private static List<Throwable> failures(EngineExecutionResults results) {
        return results.allEvents().executions().failed().stream().map(Execution::getTerminationInfo)
                .map(info -> info.getExecutionResult().getThrowable().orElseThrow()).toList();
    }

    private static List<String> seen(String scenario) {
        return ModelScenarios.SEEN.getOrDefault(scenario, List.of());
    }

    @Test
    void everyTestGetsAFreshInstanceThatIsDeletedAfterwards() {
        assertThat(failures(run(ModelScenarios.PerTest.class))).isEmpty();

        assertThat(seen("PerTest")).containsExactly("first 0001 0002", "created 0001", "second 0001", "created 0002");
    }

    @Test
    void perClassIsolationSharesOneInstanceUntilAfterAll() {
        assertThat(failures(run(ModelScenarios.PerClass.class))).isEmpty();

        assertThat(seen("PerClass")).containsExactly("first 0001", "second 0002", "afterAll 0002");
    }

    @Test
    void aMethodLevelAppletExistsOnlyDuringItsTestAndIsSelectedFirst() {
        assertThat(failures(run(ModelScenarios.MethodLevel.class))).isEmpty();

        assertThat(seen("MethodLevel")).containsExactly("selected 0F", "model 0000", "other gone", "selected 0000");
    }

    @Test
    void severalInstancesReceiveTheirOwnInstallParameters() {
        assertThat(failures(run(ModelScenarios.Instances.class))).isEmpty();

        assertThat(seen("Instances")).containsExactly("first AABBCC", "second 112233", "aid true");
    }

    @Test
    void nestedClassesSeeEnclosingInstancesAndDeleteTheirOwn() {
        assertThat(failures(run(ModelScenarios.Outer.class))).isEmpty();

        assertThat(seen("Nested")).containsExactly("outer 0001", "inner selects other 0F", "inner sees outer 0001",
                "other deleted with the nested class");
    }

    @Test
    void everyParameterizedInvocationGetsAFreshInstance() {
        assertThat(failures(run(ModelScenarios.Parameterized.class))).isEmpty();

        assertThat(seen("Parameterized")).containsExactly("1->0001", "2->0002", "3->0003");
    }

    @Test
    void anImperativeInstallInBeforeEachIsDeletedAfterTheTest() {
        assertThat(failures(run(ModelScenarios.Imperative.class))).isEmpty();

        assertThat(seen("Imperative")).containsExactly("other 0F", "aid true");
    }

    @Test
    void aSmartCardFieldReceivesTheCardOfTheRun() {
        assertThat(failures(run(ModelScenarios.Field.class))).isEmpty();

        assertThat(seen("Field")).containsExactly("same true", "works 0001");
    }

    @Test
    void deselectClearsClearOnDeselectMemory() {
        assertThat(failures(run(ModelScenarios.Deselect.class))).isEmpty();

        assertThat(seen("Deselect")).containsExactly("before 5A", "after 00");
    }

    @Test
    void aFailedTestCarriesItsApduExchanges() {
        List<Throwable> failures = failures(run(ModelScenarios.Failing.class));

        assertThat(failures).singleElement().satisfies(failure -> assertThat(Arrays.stream(failure.getSuppressed())
                .map(Throwable::getMessage)).anySatisfy(message -> assertThat(message)
                .contains("APDU exchanges of the test").contains("C: 801000000").contains("R: 00019000")
                .contains("C: 801100000").contains("# install " + ModelApplet.class.getName())));
    }

    @Test
    void backendConditionsSkipWithTheReason() {
        EngineExecutionResults results = run(ModelScenarios.Conditions.class);

        assertThat(failures(results)).isEmpty();
        assertThat(seen("Conditions")).containsExactly("ran everywhere");
        assertThat(results.testEvents().skipped().stream().map(event -> event.getPayload(String.class).orElse("")))
                .anySatisfy(reason -> assertThat(reason).contains("jcx.backend=embedded")
                        .contains("jCardSim differs here"))
                .anySatisfy(reason -> assertThat(reason).contains("runs only on [LIVECARD]"));
    }

    @Test
    void twoDeclarationsWithTheSameInstanceAidAreAConfigurationError() {
        List<Throwable> failures = failures(run(ModelScenarios.Duplicate.class));

        assertThat(seen("Duplicate")).isEmpty();
        assertThat(failures).singleElement().satisfies(failure -> assertThat(failure)
                .hasMessageContaining("already installed with the same instance AID")
                .hasMessageContaining("aid suffix"));
    }

    @Test
    void staticFieldsKeepTheirValuesAcrossTheInstancesOfAClassRunAndStartFreshInTheNextRun() {
        assertThat(failures(run(ModelScenarios.Statics.class))).isEmpty();
        assertThat(failures(run(ModelScenarios.Statics.class))).isEmpty();

        assertThat(seen("Statics")).containsExactly("0001", "0002", "0001", "0002");
    }

    @Test
    void theAidPrefixIsASetting() {
        assertThat(failures(run(ModelScenarios.Prefix.class, Map.of("jcx.aidPrefix", "F012345678")))).isEmpty();
        assertThat(failures(run(ModelScenarios.Prefix.class))).isEmpty();

        assertThat(seen("Prefix")).satisfiesExactly(
                aid -> assertThat(aid).startsWith("F012345678").hasSize(2 * 9),
                aid -> assertThat(aid).startsWith("F04A4358").hasSize(2 * 8));
    }

    @Test
    void appletsBuiltByThePluginGetTheProjectsPrefixAndTheBuildAidsAreNoted() {
        assertThat(failures(run(ModelScenarios.Built.class))).isEmpty();

        String prefix = AidScheme.forProject("com.example:built-applets").prefix();
        assertThat(seen("Built")).satisfiesExactly(
                aid -> assertThat(aid).startsWith(prefix).hasSize(2 * (5 + 2 + 2)),
                answer -> assertThat(answer).isEqualTo("42"),
                note -> assertThat(note).contains("build of " + BuiltApplet.class.getName()
                        + ": package AID A00000006299, applet AID A0000000629901, Java Card 3.0.4; this run: package"
                        + " AID " + prefix));
    }

    @Test
    void theAidPrefixSettingWinsOverTheProjectsPrefix() {
        assertThat(failures(run(ModelScenarios.Built.class, Map.of("jcx.aidPrefix", "F012345678")))).isEmpty();

        assertThat(seen("Built").getFirst()).startsWith("F012345678");
    }

    @Test
    void livecardFromAConfigurationParameterIsRefused() {
        List<Throwable> failures = failures(run(ModelScenarios.PerTest.class, Map.of("jcx.backend", "livecard")));

        assertThat(seen("PerTest")).isEmpty();
        assertThat(failures).isNotEmpty().allSatisfy(failure -> assertThat(failure)
                .hasMessageContaining("only with the JVM system property -Djcx.backend=livecard"));
    }

    @Test
    void anUnknownBackendIsAConfigurationError() {
        List<Throwable> failures = failures(run(ModelScenarios.PerTest.class, Map.of("jcx.backend", "cloud")));

        assertThat(failures).isNotEmpty().allSatisfy(failure -> assertThat(failure)
                .hasMessageContaining("Unknown backend 'cloud'"));
    }

    @Test
    void theContainerBackendIsNotAvailableYetAndSaysWhatToUse() {
        List<Throwable> failures = failures(run(ModelScenarios.PerTest.class, Map.of("jcx.backend", "container")));

        assertThat(failures).isNotEmpty().allSatisfy(failure -> assertThat(failure)
                .hasMessageContaining("not available for @JavaCardTest classes yet")
                .hasMessageContaining("@SmartCard(mode = Mode.CONTAINER)"));
    }

    @Test
    void aBackendWhoseModuleIsMissingNamesTheDependency() {
        List<Throwable> failures = failures(run(ModelScenarios.PerTest.class,
                Map.of("jcx.backend", "simulated-gp")));

        assertThat(failures).isNotEmpty().allSatisfy(failure -> assertThat(failure)
                .hasMessageContaining("javacard-express-livecard"));
    }
}
