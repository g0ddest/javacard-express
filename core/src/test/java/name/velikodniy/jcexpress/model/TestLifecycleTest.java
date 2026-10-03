package name.velikodniy.jcexpress.model;

import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.fakes.ParametersRequiredApplet;
import name.velikodniy.jcexpress.fakes.RefusingSelectApplet;
import name.velikodniy.jcexpress.fakes.ThrowingApplet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Execution;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * The lifetime of the card of a {@code @JavaCardTest} class on the default jCardSim backend, run through the JUnit
 * Platform the way users run it ({@link LifecycleScenarios}): the card completes '61XX' and '6CXX' like a PC/SC
 * reader (ISO/IEC 7816-4:2005 5.1.3), declared installs select nothing, the first PER_CLASS applet is selected for
 * {@code @BeforeAll}, a test starts with the nearest applet selected without selecting it again, a class with two
 * instances names none, failures in lifecycle methods and declared installs carry the APDU transcript, and
 * {@code history()} holds the current test's exchanges.
 */
class TestLifecycleTest {

    @BeforeEach
    void clear() {
        LifecycleScenarios.SEEN.clear();
    }

    private static EngineExecutionResults run(Class<?> scenario) {
        return EngineTestKit.engine("junit-jupiter").configurationParameters(Map.of(OnlyInTestKit.PARAMETER, "true"))
                .selectors(selectClass(scenario)).execute();
    }

    private static List<Throwable> failures(Class<?> scenario) {
        return run(scenario).allEvents().executions().failed().stream().map(Execution::getTerminationInfo)
                .map(info -> info.getExecutionResult().getThrowable().orElseThrow()).toList();
    }

    private static List<String> seen(String scenario) {
        return LifecycleScenarios.SEEN.getOrDefault(scenario, List.of());
    }

    /** The transcripts attached to a failure. */
    private static List<String> transcripts(Throwable failure) {
        return Arrays.stream(failure.getSuppressed()).map(Throwable::getMessage)
                .filter(message -> message.startsWith("APDU exchanges")).toList();
    }

    @Test
    void sendCompletes61xxWithGetResponseAnd6cxxWithTheExactLeLikeAPcscReader_iso7816_4_5_1_3() {
        assertThat(failures(LifecycleScenarios.GetResponse.class)).isEmpty();

        assertThat(seen("GetResponse")).containsExactly("send 9000 600 in order", "command 9000 600 in order",
                "hex 9000 600 in order", "exact 9000 5 0102030405", "transmit 258 6100",
                "history 00A40400 80400258 80C00000 80C00000");
    }

    @Test
    void installsSelectNothingAndBeforeAllTalksToTheFirstPerClassApplet() {
        assertThat(failures(LifecycleScenarios.PerClassBeforeAll.class)).isEmpty();

        assertThat(seen("PerClassBeforeAll")).containsExactly("beforeAll true selects=1 deselects=0",
                "first selects=1 deselects=0 transient 5A", "second selects=1 deselects=0 transient 6B");
    }

    @Test
    void aFirstPerClassAppletThatRefusesSelectionDoesNotFailTheClass() {
        assertThat(failures(LifecycleScenarios.RefusingPerClass.class)).isEmpty();

        assertThat(seen("RefusingPerClass")).satisfiesExactly(
                beforeAll -> assertThat(beforeAll).contains("# selecting the first PER_CLASS applet, "
                        + RefusingSelectApplet.class.getSimpleName() + " as F04A4358").contains("for @BeforeAll failed")
                        .contains("6999"),
                test -> assertThat(test).isEqualTo("test selects=1 deselects=0"));
    }

    @Test
    void aPerTestInstanceIsSelectedOnceBeforeItsTest() {
        assertThat(failures(LifecycleScenarios.PerTestCounts.class)).isEmpty();

        assertThat(seen("PerTestCounts")).containsExactly("first selects=1 deselects=0",
                "second selects=2 deselects=1");
    }

    @Test
    void theSelectionCarriesOverUntilAResetOrADeselect() {
        assertThat(failures(LifecycleScenarios.Tracking.class)).isEmpty();

        assertThat(seen("Tracking")).containsExactly("kept selects=2 deselects=1 transient 11",
                "after reset selects=3 deselects=1", "after deselect selects=4 deselects=2");
    }

    @Test
    void aClassWithTwoInstancesNamesNoneOfThemAndSaysHowToNameOne() {
        assertThat(failures(LifecycleScenarios.TwoInstances.class)).isEmpty();

        List<String> seen = seen("TwoInstances");
        assertThat(seen).first().isEqualTo("selected true");
        assertThat(seen.subList(1, 3)).allSatisfy(message -> assertThat(message)
                .contains(CountingApplet.class.getSimpleName() + " has 2 instances installed")
                .containsPattern("\"0101\" \\(F04A4358[0-9A-F]*0101\\)")
                .containsPattern("\"0102\" \\(F04A4358[0-9A-F]*0102\\)")
                .contains("card.aid(\"0101\")").contains("card.select(card.aid(\"0101\"))"));
        assertThat(seen.get(1)).startsWith("aid: ");
        assertThat(seen.get(2)).startsWith("select: ");
    }

    @Test
    void anInstanceWithoutAnAidSuffixIsNotNamedByItsDerivedSuffix() {
        assertThat(failures(LifecycleScenarios.UnsuffixedInstance.class)).isEmpty();

        List<String> seen = seen("UnsuffixedInstance");
        assertThat(seen.getFirst()).isEqualTo("selected true");
        String message = seen.get(1);
        assertThat(message).contains(CountingApplet.class.getSimpleName() + " has 2 instances installed")
                .containsPattern("F04A4358[0-9A-F]+ \\(CountingApplet's own AID, declared without an aid suffix\\)")
                .containsPattern("\"0401\" \\(F04A4358[0-9A-F]*0401\\) selected now")
                .contains("card.select(card.aid(\"0401\"))")
                .contains("@InstallApplet(value = CountingApplet.class, aid = \"<suffix>\")");
        assertThat(Pattern.compile("card\\.aid\\(\"([0-9A-F]+)\"\\)").matcher(message).results()
                .map(match -> match.group(1)).distinct()).containsExactly("0401");
    }

    @Test
    void aShortenedFailureTranscriptPointsToTheFileWithTheWholeOne() {
        List<Throwable> failures = failures(LifecycleScenarios.LongTranscript.class);

        assertThat(failures).singleElement().satisfies(failure -> assertThat(transcripts(failure)).singleElement()
                .satisfies(transcript -> assertThat(transcript)
                        .containsPattern("# \\d+ earlier entries not shown\n# the test's whole transcript is the"
                                + " file apdu-transcript.txt")
                        .contains("C: 807F0000")));
    }

    @Test
    void aidOfAPerTestAppletInBeforeAllExplainsTheLifetime() {
        assertThat(failures(LifecycleScenarios.AidInBeforeAll.class)).isEmpty();

        assertThat(seen("AidInBeforeAll")).satisfiesExactly(
                message -> assertThat(message).contains(CountingApplet.class.getName())
                        .contains("PER_TEST").contains("only while a test runs").contains("@BeforeEach")
                        .contains("isolation = Isolation.PER_CLASS").doesNotContain("Declare it with"),
                test -> assertThat(test).isEqualTo("test true"));
    }

    @Test
    void aFailureInBeforeEachCarriesTheTranscript() {
        List<Throwable> failures = failures(LifecycleScenarios.BeforeEachFails.class);

        assertThat(seen("BeforeEachFails")).isEmpty();
        assertThat(failures).singleElement().satisfies(failure -> assertThat(transcripts(failure)).singleElement()
                .satisfies(transcript -> assertThat(transcript).contains("C: 807F0000").contains("R: 6D00")));
    }

    @Test
    void aFailureInBeforeAllCarriesTheTranscript() {
        List<Throwable> failures = failures(LifecycleScenarios.BeforeAllFails.class);

        assertThat(failures).singleElement().satisfies(failure -> assertThat(transcripts(failure)).singleElement()
                .satisfies(transcript -> assertThat(transcript).contains("C: 807E0000").contains("R: 6D00")));
    }

    @Test
    void failuresInAfterEachAndAfterAllCarryTheTranscript() {
        List<Throwable> failures = failures(LifecycleScenarios.AfterMethodsFail.class);

        assertThat(failures).hasSize(2);
        assertThat(transcripts(failures.get(0))).singleElement().satisfies(transcript -> assertThat(transcript)
                .contains("C: 807D0000").contains("R: 6D00"));
        assertThat(transcripts(failures.get(1))).singleElement().satisfies(transcript -> assertThat(transcript)
                .contains("C: 807C0000").contains("R: 6D00"));
    }

    @Test
    void aFailedDeclaredInstallCarriesTheTranscript() {
        List<Throwable> failures = failures(LifecycleScenarios.DeclaredInstallFails.class);

        assertThat(seen("DeclaredInstallFails")).isEmpty();
        assertThat(failures).singleElement().isInstanceOf(InstallException.class).satisfies(failure ->
                assertThat(transcripts(failure)).singleElement().satisfies(transcript -> assertThat(transcript)
                        .contains("# install " + ParametersRequiredApplet.class.getName())
                        .contains("# install failed: its install method threw ISOException with reason 6A80")));
    }

    @Test
    void aFailureWithoutAnyAppletStartsItsTranscriptWithAHint() {
        List<Throwable> failures = failures(LifecycleScenarios.NothingInstalled.class);

        assertThat(failures).singleElement().satisfies(failure -> assertThat(transcripts(failure)).singleElement()
                .satisfies(transcript -> assertThat(transcript.lines().skip(1).findFirst().orElseThrow())
                        .startsWith("# no applet is installed or selected")
                        .contains("NothingInstalled declares no @InstallApplet")
                        .contains("@InstallApplet(")));
    }

    @Test
    void historyHoldsTheExchangesOfTheCurrentTestAndInClassMethodsThoseOfTheClass() {
        assertThat(failures(LifecycleScenarios.HistoryScope.class)).isEmpty();

        assertThat(seen("HistoryScope")).containsExactly("beforeAll []", "first [00A40400 80130000]",
                "second [00A40400 80130000 80130000]", "second transcript true", "afterAll 3");
    }

    @Test
    void anExceptionOfTheAppletIsNotedNextToThe6f00() {
        List<Throwable> failures = failures(LifecycleScenarios.AppletThrows.class);

        assertThat(failures).singleElement().satisfies(failure -> assertThat(transcripts(failure)).singleElement()
                .satisfies(transcript -> assertThat(transcript).contains("C: 80010000\nR: 6F00\n# applet threw"
                        + " java.lang.ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 4 at "
                        + ThrowingApplet.class.getName() + ".process(ThrowingApplet.java:")));
    }
}
