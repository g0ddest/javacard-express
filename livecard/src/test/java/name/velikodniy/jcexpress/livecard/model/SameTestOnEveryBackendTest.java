package name.velikodniy.jcexpress.livecard.model;

import name.velikodniy.jcexpress.backend.AidScheme;
import name.velikodniy.jcexpress.gp.CAPFile;
import name.velikodniy.jcexpress.livecard.model.built.BuiltApplet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Owner requirement 3: the same {@code @JavaCardTest} classes run unchanged on jCardSim ({@code embedded}) and on
 * the simulated GlobalPlatform card ({@code simulated-gp}, the real-card code path without a reader), with the same
 * results: installs before each scope, deletes after it, per-test and per-class isolation, several instances with
 * install parameters, a method-level applet of the same package, nested classes, parameterized tests, deselection.
 */
class SameTestOnEveryBackendTest {

    @BeforeEach
    void clear() {
        BackendScenarios.SEEN.clear();
    }

    private static List<String> run(Class<?> scenario, String backend) {
        return run(scenario, backend, Map.of());
    }

    private static List<String> run(Class<?> scenario, String backend, Map<String, String> settings) {
        BackendScenarios.SEEN.clear();
        Map<String, String> parameters = new HashMap<>(settings);
        parameters.putAll(Map.of(OnlyInTestKit.PARAMETER, "true", "jcx.backend", backend));
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .configurationParameters(parameters)
                .selectors(selectClass(scenario)).execute();
        List<String> failures = results.allEvents().executions().failed().stream().map(Execution::getTerminationInfo)
                .map(info -> info.getExecutionResult().getThrowable().map(SameTestOnEveryBackendTest::describe)
                        .orElse("?"))
                .toList();
        assertThat(failures).as("failures on " + backend).isEmpty();
        return List.copyOf(BackendScenarios.SEEN.getOrDefault(scenario.getSimpleName(), List.of()));
    }

    private static String describe(Throwable throwable) {
        StringBuilder text = new StringBuilder(throwable.toString());
        for (Throwable cause = throwable.getCause(); cause != null; cause = cause.getCause()) {
            text.append(" <- ").append(cause);
        }
        return text.toString();
    }

    /**
     * {@code jcx.log=true} prints the card's exchanges on every backend: on the simulated GlobalPlatform card also the
     * commands of the card content management (INSTALL [for install] with the C-MAC of the secure channel).
     */
    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void theLogParameterPrintsTheExchangesOfTheCard(String backend) {
        Logger logger = Logger.getLogger("name.velikodniy.jcexpress");
        long thread = Thread.currentThread().threadId();
        List<String> lines = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                if (logRecord.getLongThreadID() == thread && logger.getName().equals(logRecord.getLoggerName())) {
                    lines.add(logRecord.getMessage());
                }
            }

            @Override
            public void flush() {
                // nothing buffered
            }

            @Override
            public void close() {
                // nothing to release
            }
        };
        logger.addHandler(handler);
        try {
            run(BackendScenarios.PerTest.class, backend, Map.of("jcx.log", "true"));
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(lines).contains("[JCX] ## PerTest", "[JCX] ## PerTest > first()", "[JCX] ## PerTest > second()",
                "[JCX] C: 8030000002", "[JCX] R: 00019000");
        if (backend.equals("simulated-gp")) {
            assertThat(lines).anyMatch(line -> line.startsWith("[JCX] C: 84E60C"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void perTestIsolation(String backend) {
        assertThat(run(BackendScenarios.PerTest.class, backend))
                .containsExactly("first 0001 0002 created 0001", "second 0001 created 0002");
    }

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void perClassIsolation(String backend) {
        assertThat(run(BackendScenarios.PerClass.class, backend))
                .containsExactly("first 0001", "second 0002", "afterAll 0002");
    }

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void scopesInstancesParametersNestedAndDeselect(String backend) {
        assertThat(run(BackendScenarios.Scopes.class, backend)).containsExactly("first AABBCC", "second 112233",
                "aid true", "other 0F", "transient 00", "inner other 0F", "inner per-class 112233");
    }

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void parameterizedInvocationsAreIsolated(String backend) {
        assertThat(run(BackendScenarios.Parameterized.class, backend)).containsExactly("1->0001", "3->0003");
    }

    @Test
    void theSameClassGivesTheSameObservationsOnBothBackends() {
        for (Class<?> scenario : List.of(BackendScenarios.PerTest.class, BackendScenarios.PerClass.class,
                BackendScenarios.Scopes.class, BackendScenarios.Parameterized.class)) {
            assertThat(run(scenario, "simulated-gp")).as(scenario.getSimpleName())
                    .isEqualTo(run(scenario, "embedded"));
        }
    }

    @Test
    void aPackageTheMavenPluginBuiltRunsUnderTheProjectsPrefixWithTheBuildsSettings(@TempDir Path transcripts)
            throws IOException {
        String prefix = AidScheme.forProject("com.example:built-applets").prefix();

        List<String> embedded = run(BackendScenarios.Built.class, "embedded");
        List<String> simulated = run(BackendScenarios.Built.class, "simulated-gp",
                Map.of("jcx.livecard.transcriptDir", transcripts.toString()));

        assertThat(simulated).isEqualTo(embedded).satisfiesExactly(
                aid -> assertThat(aid).startsWith(prefix), answer -> assertThat(answer).isEqualTo("42"));
        String pkg = BuiltApplet.class.getPackageName();
        CAPFile cap = CAPFile.fromFile(transcripts.resolve("cap").resolve(pkg + ".cap"));
        assertThat(cap.majorVersion() + "." + cap.minorVersion()).isEqualTo("1.2");
        assertThat(cap.packageAidHex()).startsWith(prefix);
        assertThat(Files.readString(transcripts.resolve(BackendScenarios.Built.class.getName()).resolve("card.txt")))
                .contains("package " + pkg + " built by com.example:built-applets: converted for Java Card 3.0.4 as"
                        + " the build, package version 1.2; AIDs of the build -> this run: package A00000006299 -> "
                        + prefix)
                .contains("BuiltApplet A0000000629901 -> " + prefix);
    }

    @Test
    void gpBackendsProvideTheHarnessAndJCardSimSkipsSuchTests() {
        assertThat(run(BackendScenarios.CardContent.class, "simulated-gp")).containsExactly("listed true");
        assertThat(run(BackendScenarios.CardContent.class, "embedded")).isEmpty();
    }
}
