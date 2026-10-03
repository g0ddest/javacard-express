package name.velikodniy.jcexpress.livecard.model;

import name.velikodniy.jcexpress.livecard.LiveCardException;
import name.velikodniy.jcexpress.livecard.guard.GuardViolationException;
import name.velikodniy.jcexpress.livecard.model.applet.ModelApplet;
import name.velikodniy.jcexpress.livecard.model.samepackage.SamePackageScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Execution;

import java.util.Arrays;
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
 * The GlobalPlatform backends run the same {@code @JavaCardTest} classes as jCardSim in the cases where they used to
 * differ: test-source applets next to their test class, an applet installed imperatively from a package that a
 * declared applet already loaded, and the messages of typical mistakes. Where a card really differs (an applet that
 * imports another package, an AID outside the run's prefix, the APDU guard), the failure says why and what to do.
 */
class GpBackendParityTest {

    @BeforeEach
    void clear() {
        BackendScenarios.SEEN.clear();
        SamePackageScenario.SEEN.clear();
    }

    /** Runs a scenario class on a backend; returns the failures. */
    static List<Throwable> failures(Class<?> scenario, String backend, Map<String, String> settings) {
        BackendScenarios.SEEN.clear();
        SamePackageScenario.SEEN.clear();
        Map<String, String> parameters = new HashMap<>(settings);
        parameters.putAll(Map.of(OnlyInTestKit.PARAMETER, "true", "jcx.backend", backend));
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter").configurationParameters(parameters)
                .selectors(selectClass(scenario)).execute();
        return results.allEvents().executions().failed().stream().map(Execution::getTerminationInfo)
                .map(info -> info.getExecutionResult().getThrowable().orElseThrow()).toList();
    }

    private static List<String> seen(Class<?> scenario, String backend) {
        assertThat(failures(scenario, backend, Map.of())).as("failures on " + backend).isEmpty();
        return List.copyOf(BackendScenarios.SEEN.getOrDefault(scenario.getSimpleName(), List.of()));
    }

    /**
     * A test-source applet in the package of its test class: the GlobalPlatform backends convert the applet and the
     * classes it uses (JCVM 3.1 §6.6), not the test class, whose strings and lambda are outside the subset.
     */
    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void aTestSourceAppletNextToItsTestClassRuns_jcvm31_6_6(String backend) {
        assertThat(failures(SamePackageScenario.class, backend, Map.of())).as("failures on " + backend).isEmpty();

        assertThat(SamePackageScenario.SEEN).containsExactly("answered SP");
    }

    /**
     * The core README's recipe for computed install parameters: {@code card.install(...)} of an applet whose package
     * a declared applet already loaded. The CAP file holds every applet of the package, so the GlobalPlatform
     * backends install it from the loaded package (INSTALL [for install], GPCS v2.3.1 9.3.6).
     */
    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void anImperativeInstallOfAnotherAppletOfALoadedPackageWorks_gpcs231_9_3_6(String backend) {
        assertThat(seen(GpParityScenarios.ImperativePeer.class, backend)).containsExactly("other 0F", "model 0001");
    }

    /** jCardSim runs an applet that uses another package; the GlobalPlatform backends refuse it before converting. */
    @Test
    void anAppletThatImportsAnotherPackageIsRefusedWithTheWayOut_jcvm31_4_3_3() {
        assertThat(seen(GpParityScenarios.Importer.class, "embedded")).containsExactly("count 0001");

        assertThat(failures(GpParityScenarios.Importer.class, "simulated-gp", Map.of())).singleElement()
                .isInstanceOfSatisfying(LiveCardException.class, failure -> assertThat(failure)
                        .hasMessageContaining("name.velikodniy.jcexpress.livecard.model.importer")
                        .hasMessageContaining("name.velikodniy.jcexpress.livecard.model.library (Tally")
                        .hasMessageContaining("cannot load a package that depends on other packages yet")
                        .hasMessageContaining("@EnabledOnBackend(Mode.EMBEDDED)")
                        .hasMessageContaining("without -Djcx.backend"));
        assertThat(BackendScenarios.SEEN).isEmpty();
    }

    /**
     * An applet's own command with INS 70 in a proprietary class answered 9000: the guard keeps its rule (it may be
     * MANAGE CHANNEL, ISO/IEC 7816-4:2005 7.1.2) and the next block names that command and the way out.
     */
    @Test
    void aBlockAfterTheAppletsOwnInsSeventyNamesThatCommand_iso7816_4_7_1_2() {
        assertThat(seen(GpParityScenarios.OwnInsSeventy.class, "embedded")).containsExactly("70 -> 70", "10 -> 10");

        assertThat(seen(GpParityScenarios.OwnInsSeventy.class, "simulated-gp")).satisfiesExactly(
                first -> assertThat(first).isEqualTo("70 -> 70"),
                second -> assertThat(second).startsWith("10 -> APDU guard blocked 8010000001")
                        .contains("the channels became unknown after 80 70 04 00 (answered 9000)")
                        .contains("in a proprietary class the guard has to treat as MANAGE CHANNEL")
                        .contains("give it another INS"));
    }

    /**
     * The simulated card runs applets on the basic channel only (jCardSim has one selection): a SELECT of a test
     * applet on a logical channel is answered '6881' (logical channel not supported, ISO/IEC 7816-4:2005 5.1.3
     * Table 6) with a note that names the limit; the ISD stays selectable there.
     */
    @Test
    void aTestAppletOnALogicalChannelOfTheSimulatedCardIsAnsweredWithItsLimit_iso7816_4_5_1_3() {
        assertThat(seen(GpParityScenarios.Channels.class, "simulated-gp")).satisfiesExactly(
                applet -> assertThat(applet).isEqualTo("applet " + 0x6881),
                isd -> assertThat(isd).isEqualTo("isd " + 0x9000),
                basic -> assertThat(basic).isEqualTo("basic 0001"),
                note -> assertThat(note).startsWith("# simulated card: applets run on the basic channel only")
                        .contains("6881"));
    }

    /** requireSuccess() shows the command on every backend; a failed SELECT says what the status word means. */
    @Test
    void requireSuccessAndAFailedSelectExplainThemselves() {
        assertThat(seen(GpParityScenarios.Mistakes.class, "simulated-gp")).satisfiesExactlyInAnyOrder(
                requireSuccess -> assertThat(requireSuccess).contains("C: 807F0102").contains("R: 6D00"),
                select -> assertThat(select).startsWith("SELECT A0000000629901 failed: SW=6A82 (file or application"
                        + " not found)")
                        .contains("instances of this run: " + "name.velikodniy.jcexpress.livecard.model.built.BuiltApplet as F0")
                        .contains("A0000000629901 is the AID the build gave"
                                + " name.velikodniy.jcexpress.livecard.model.built.BuiltApplet")
                        .contains("card.select(BuiltApplet.class)"));
    }

    /** An AID outside the run's prefix: the GlobalPlatform backends refuse it and name the @JavaCardTest way. */
    @Test
    void anAidOutsideThePrefixIsRefusedWithTheJavaCardTestWay() {
        assertThat(seen(GpParityScenarios.FixedAid.class, "embedded")).containsExactly("installed A0000000624002");

        assertThat(failures(GpParityScenarios.FixedAid.class, "simulated-gp", Map.of())).singleElement()
                .isInstanceOfSatisfying(LiveCardException.class, failure -> assertThat(failure)
                        .hasMessageContaining("A0000000624002 is outside the run's AID prefix F04A4358")
                        .hasMessageContaining("card.aid(\"<suffix>\")")
                        .hasMessageContaining("@InstallApplet(aid = \"<suffix>\")")
                        .hasMessageContaining("jcx.aidPrefix")
                        .hasMessageContaining("jcx.livecard.registeredRid")
                        .satisfies(e -> assertThat(e.getMessage()).doesNotContain("LiveCard.aid")));
    }

    /** simulated-gp takes the registered RID of a prefix outside the proprietary category from the run's settings. */
    @Test
    void simulatedGpTakesARegisteredRidPrefix() {
        Map<String, String> settings = Map.of("jcx.aidPrefix", "D27600099901", "jcx.livecard.registeredRid",
                "D276000999");

        assertThat(failures(BackendScenarios.PerTest.class, "simulated-gp", settings)).isEmpty();
        assertThat(BackendScenarios.SEEN.get("PerTest")).containsExactly("first 0001 0002 created 0001",
                "second 0001 created 0002");
        assertThat(failures(BackendScenarios.PerTest.class, "simulated-gp", Map.of("jcx.aidPrefix", "D27600099901")))
                .singleElement().satisfies(failure -> assertThat(failure)
                        .hasMessageContaining("jcx.livecard.registeredRid"));
    }

    /**
     * The transcript of a failed test on a GlobalPlatform backend labels the card content management, leaves the
     * LOAD blocks to the class transcript file, marks where the test body starts and shows a blocked command.
     */
    @Test
    void theFailureTranscriptLabelsTheCardContentManagement() {
        List<Throwable> failures = failures(GpParityScenarios.Failing.class, "simulated-gp", Map.of());

        assertThat(failures).hasSize(2);
        String failing = transcript(failures.stream().filter(e -> e instanceof AssertionError).findFirst()
                .orElseThrow());
        assertThat(failing).contains("# install " + "name.velikodniy.jcexpress.livecard.model.applet.ModelApplet as F04A4358")
                .contains("# load name.velikodniy.jcexpress.livecard.model.applet as F04A4358")
                .contains("LOAD blocks").contains("card.txt")
                .contains("# secure channel to the Issuer Security Domain A000000151000000")
                .contains("# test body")
                .doesNotContain("C: 84E8").doesNotContain("C: 80E8");
        String blocked = transcript(failures.stream().filter(e -> e instanceof GuardViolationException).findFirst()
                .orElseThrow());
        assertThat(blocked).contains("# blocked by the APDU guard, not sent: 8050000002");
    }

    /**
     * {@code -Djcx.log=true} prints the same labelled history: the card content management with its notes, one note
     * in place of the LOAD blocks, and where each test body starts.
     */
    @Test
    void theLogLabelsTheCardContentManagementWithoutLoadBlocks() {
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
            assertThat(failures(BackendScenarios.PerTest.class, "simulated-gp", Map.of("jcx.log", "true"))).isEmpty();
        } finally {
            logger.removeHandler(handler);
        }

        assertThat(lines).contains("[JCX] ## PerTest > first()", "[JCX] # test body: first()",
                        "[JCX] # test body: second()")
                .anyMatch(line -> line.startsWith("[JCX] # install " + ModelApplet.class.getName() + " as F04A4358"))
                .anyMatch(line -> line.startsWith("[JCX] # load " + ModelApplet.class.getPackageName() + " as "))
                .anyMatch(line -> line.matches("\\[JCX] # \\d+ LOAD blocks .*"))
                .anyMatch(line -> line.startsWith("[JCX] # secure channel to the Issuer Security Domain"))
                .anyMatch(line -> line.startsWith("[JCX] # delete F04A4358"))
                .noneMatch(line -> line.startsWith("[JCX] C: 84E8"));
    }

    private static String transcript(Throwable failure) {
        return Arrays.stream(failure.getSuppressed()).map(Throwable::getMessage)
                .filter(message -> message.startsWith("APDU exchanges of the test")).findFirst().orElseThrow();
    }
}
