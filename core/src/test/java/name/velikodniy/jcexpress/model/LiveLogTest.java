package name.velikodniy.jcexpress.model;

import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardExtension;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCard;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Execution;

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
 * {@code jcx.log=true} prints the exchanges of the card of a {@link JavaCardTest} class while the tests run: the
 * transcript lines of the card's history (installs, SELECT commands, the test's commands, deletions) through the
 * channel of {@code LoggingSession.logged(true)}, java.util.logging logger {@code name.velikodniy.jcexpress} with the
 * prefix {@code [JCX]}, preceded by a title line per class and test. The card stays the card of the declarative
 * model.
 */
class LiveLogTest {

    private static final Logger LOGGER = Logger.getLogger("name.velikodniy.jcexpress");

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

    private static List<Throwable> failures(EngineExecutionResults results) {
        return results.allEvents().executions().failed().stream().map(Execution::getTerminationInfo)
                .map(info -> info.getExecutionResult().getThrowable().orElseThrow()).toList();
    }

    /** The lines this thread logs to the logger of JavaCard Express while {@code run} runs. */
    private static List<String> printedWhile(Runnable run) {
        long thread = Thread.currentThread().threadId();
        List<String> lines = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                if (logRecord.getLongThreadID() == thread && LOGGER.getName().equals(logRecord.getLoggerName())) {
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
        LOGGER.addHandler(handler);
        try {
            run.run();
        } finally {
            LOGGER.removeHandler(handler);
        }
        return lines;
    }

    @Test
    void theLogParameterPrintsTheExchangesOfTheCardWhileTheTestsRun() {
        List<String> lines = printedWhile(() -> assertThat(failures(run(Logged.class,
                Map.of(JavaCardExtension.LOG_PARAMETER, "true")))).isEmpty());

        assertThat(lines).allSatisfy(line -> assertThat(line).startsWith("[JCX] "))
                .contains("[JCX] ## Logged", "[JCX] ## Logged > increments()", "[JCX] C: 8010000002",
                        "[JCX] R: 00019000");
        int title = lines.indexOf("[JCX] ## Logged > increments()");
        int install = indexOfFirst(lines, "[JCX] # install " + ModelApplet.class.getName() + " as ");
        int command = lines.indexOf("[JCX] C: 8010000002");
        int delete = indexOfFirst(lines, "[JCX] # delete ");
        assertThat(List.of(title, install, command, lines.indexOf("[JCX] R: 00019000"), delete))
                .as("title, install, command, response and deletion in the order they happened: %s", lines)
                .doesNotContain(-1).isSorted();
    }

    @Test
    void withoutTheLogParameterNothingIsPrinted() {
        List<String> lines = printedWhile(() -> assertThat(failures(run(Logged.class, Map.of()))).isEmpty());

        assertThat(lines).isEmpty();
    }

    @Test
    void aLoggedCardIsStillTheCardOfTheDeclarativeModel() {
        printedWhile(() -> assertThat(failures(run(Logged.class,
                Map.of(JavaCardExtension.LOG_PARAMETER, "true")))).isEmpty());

        assertThat(ModelScenarios.SEEN.get("Logged")).satisfiesExactly(
                counter -> assertThat(counter).isEqualTo("0001"),
                aid -> assertThat(aid).startsWith("F04A4358"),
                history -> assertThat(history).isEqualTo("history kept"));
    }

    @Test
    void aSmartCardParameterOfAJavaCardTestCannotAskForItsOwnLog() {
        List<Throwable> failures = failures(run(LogParameter.class, Map.of()));

        assertThat(ModelScenarios.SEEN).doesNotContainKey("LogParameter");
        assertThat(failures).singleElement().satisfies(failure -> assertThat(failure)
                .hasStackTraceContaining("@SmartCard on parameter")
                .hasStackTraceContaining("cannot set mode, image or log")
                .hasStackTraceContaining("-Djcx.log=true"));
    }

    private static int indexOfFirst(List<String> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    /** A declarative test class whose card is logged when the run sets jcx.log. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    static class Logged {
        @Test
        void increments(SmartCardSession card) {
            ModelScenarios.seen("Logged", ModelScenarios.increment(card));
            ModelScenarios.seen("Logged", card.aid(ModelApplet.class).toHex());
            card.deselect();
            ModelScenarios.seen("Logged", card.history().entries().isEmpty() ? "no history" : "history kept");
        }
    }

    /** A parameter of a declarative test class that asks for logging the card. */
    @OnlyInTestKit
    @JavaCardTest
    @InstallApplet(ModelApplet.class)
    static class LogParameter {
        @Test
        void neverRuns(@SmartCard(log = true) SmartCardSession card) {
            ModelScenarios.seen("LogParameter", "ran " + card);
        }
    }
}
