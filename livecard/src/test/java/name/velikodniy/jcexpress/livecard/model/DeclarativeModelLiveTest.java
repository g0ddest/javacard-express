package name.velikodniy.jcexpress.livecard.model;

import name.velikodniy.jcexpress.backend.AidScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Execution;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * LC-MODEL: the declarative model ({@code @JavaCardTest}, {@code @InstallApplet}) on the real card. The scenario
 * classes of {@link BackendScenarios} run unchanged, with the system property {@code jcx.backend=livecard} that
 * the {@code livecard} profile sets, and must observe exactly what they observe on jCardSim and on the simulated
 * GlobalPlatform card ({@link SameTestOnEveryBackendTest}): a fresh instance per test, one instance per class,
 * several instances with their install parameters, a method-level applet of the same package, a nested class,
 * parameterized invocations, deselection, the instance listed by the Issuer Security Domain, and a package with a
 * build descriptor of the Maven plugin (converted with the build's settings under the project's own AID prefix).
 * Every install and delete goes through the APDU guard; the run's cleanup deletes the package and checks it with
 * GET STATUS.
 *
 * <p>This class holds no card itself: each scenario class opens and closes the card of its run. Outside the
 * profile it runs the scenarios on the simulated card.</p>
 */
@Tag("livecard")
@Order(9)
class DeclarativeModelLiveTest {

    @BeforeEach
    void clear() {
        BackendScenarios.SEEN.clear();
    }

    private static List<String> run(Class<?> scenario) {
        Map<String, String> parameters = new HashMap<>(Map.of(OnlyInTestKit.PARAMETER, "true"));
        String backend = System.getProperty("jcx.backend", "");
        if (!backend.toLowerCase(Locale.ROOT).replace("-", "").equals("livecard")) {
            parameters.put("jcx.backend", "simulated-gp");
        }
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter").configurationParameters(parameters)
                .selectors(selectClass(scenario)).execute();
        List<Throwable> failures = results.allEvents().executions().failed().stream()
                .map(Execution::getTerminationInfo)
                .map(info -> info.getExecutionResult().getThrowable().orElseThrow()).toList();
        assertThat(failures).as(scenario.getSimpleName() + " on " + backend).isEmpty();
        return List.copyOf(BackendScenarios.SEEN.getOrDefault(scenario.getSimpleName(), List.of()));
    }

    @Test
    void freshInstancePerTest() {
        assertThat(run(BackendScenarios.PerTest.class))
                .containsExactly("first 0001 0002 created 0001", "second 0001 created 0002");
    }

    @Test
    void oneInstancePerClass() {
        assertThat(run(BackendScenarios.PerClass.class)).containsExactly("first 0001", "second 0002", "afterAll 0002");
    }

    @Test
    void instancesParametersMethodLevelAppletNestedClassAndDeselect() {
        assertThat(run(BackendScenarios.Scopes.class)).containsExactly("first AABBCC", "second 112233", "aid true",
                "other 0F", "transient 00", "inner other 0F", "inner per-class 112233");
    }

    @Test
    void parameterizedInvocations() {
        assertThat(run(BackendScenarios.Parameterized.class)).containsExactly("1->0001", "3->0003");
    }

    @Test
    void theIssuerSecurityDomainListsTheInstance() {
        assertThat(run(BackendScenarios.CardContent.class)).containsExactly("listed true");
    }

    @Test
    void aPackageTheMavenPluginBuiltRunsUnderTheProjectsPrefix() {
        assertThat(run(BackendScenarios.Built.class)).satisfiesExactly(
                aid -> assertThat(aid).startsWith(AidScheme.forProject("com.example:built-applets").prefix()),
                answer -> assertThat(answer).isEqualTo("42"));
    }
}
