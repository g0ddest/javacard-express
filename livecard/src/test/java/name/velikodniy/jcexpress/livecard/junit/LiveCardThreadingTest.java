package name.velikodniy.jcexpress.livecard.junit;

import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.LiveCardException;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCard;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCardConnector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * javax.smartcardio binds exclusive access to a card to the thread that took it, so a live card belongs to the
 * thread of its test class. {@link LiveCardTest} declares the resource lock {@value LiveCardTest#CARD_LOCK}
 * (READ_WRITE): in a parallel JUnit run the live classes run one at a time and the methods of a class in the class's
 * thread. Timeouts that run a test in another thread are rejected with an explanation, and the card refuses
 * commands from another thread before the guard sees them.
 */
class LiveCardThreadingTest {

    private static final Map<String, Set<String>> THREADS = new ConcurrentHashMap<>();
    private static final AtomicInteger ACTIVE_CLASSES = new AtomicInteger();
    private static final AtomicInteger MOST_ACTIVE_CLASSES = new AtomicInteger();

    @TempDir
    Path tmp;

    private SimulatedCard card;

    @BeforeEach
    void insertCard() {
        card = SimulatedCardConnector.insertNewCard();
        THREADS.clear();
        ACTIVE_CLASSES.set(0);
        MOST_ACTIVE_CLASSES.set(0);
    }

    private EngineExecutionResults run(Map<String, String> parameters, Class<?>... classes) throws IOException {
        Path settings = Files.writeString(tmp.resolve("livecard.properties"), "transcriptDir=" + tmp.resolve("t")
                .toString().replace("\\", "\\\\") + "\nverifierSdk=none\n");
        return EngineTestKit.engine("junit-jupiter")
                .configurationParameter(LiveCardExtension.ENABLED_PARAMETER, "true")
                .configurationParameter(LiveCardExtension.CONNECTOR_PARAMETER, SimulatedCardConnector.class.getName())
                .configurationParameter(LiveCardExtension.ISOLATED_RUN_PARAMETER, "true")
                .configurationParameter(LiveCardExtension.SETTINGS_FILE_PARAMETER, settings.toString())
                .configurationParameters(parameters)
                .selectors(Stream.of(classes).map(DiscoverySelectors::selectClass).toArray(DiscoverySelector[]::new))
                .execute();
    }

    @Test
    void liveCardTestLocksTheCard() {
        ResourceLock lock = LiveCardTest.class.getAnnotation(ResourceLock.class);

        assertThat(lock.value()).isEqualTo("jcx.card").isEqualTo(LiveCardTest.CARD_LOCK);
        assertThat(lock.mode()).isEqualTo(ResourceAccessMode.READ_WRITE);
    }

    /** JUnit's concurrent mode: without the lock, the methods of a class ran in several threads and failed. */
    @Test
    void inAParallelRunLiveClassesRunOneAtATimeAndTheirMethodsInTheClassThread() throws IOException {
        Map<String, String> parallel = new HashMap<>(Map.of(
                "junit.jupiter.execution.parallel.enabled", "true",
                "junit.jupiter.execution.parallel.mode.default", "concurrent",
                "junit.jupiter.execution.parallel.mode.classes.default", "concurrent",
                "junit.jupiter.execution.parallel.config.strategy", "fixed",
                "junit.jupiter.execution.parallel.config.fixed.parallelism", "4"));

        EngineExecutionResults results = run(parallel, FirstParallelClass.class, SecondParallelClass.class);

        results.testEvents().assertStatistics(stats -> stats.started(8).succeeded(8));
        assertThat(THREADS).containsOnlyKeys(FirstParallelClass.class.getSimpleName(),
                SecondParallelClass.class.getSimpleName());
        assertThat(THREADS.values()).as("each class in one thread").allSatisfy(threads -> assertThat(threads)
                .hasSize(1));
        assertThat(MOST_ACTIVE_CLASSES).as("live classes one at a time").hasValue(1);
    }

    @Test
    void separateThreadTimeoutIsRejectedWithAnExplanation() throws IOException {
        EngineExecutionResults results = run(Map.of(), SeparateThreadTimeout.class);

        assertThat(results.testEvents().failed().stream().map(LiveCardThreadingTest::failure)).singleElement()
                .asString().startsWith(ExtensionConfigurationException.class.getName())
                .contains("SEPARATE_THREAD", "assertTimeoutPreemptively", "thread");
        assertThat(card.received()).as("nothing reached the card from the other thread").isEmpty();
    }

    @Test
    void separateThreadTimeoutsByDefaultAreRejectedToo() throws IOException {
        EngineExecutionResults results = run(Map.of("junit.jupiter.execution.timeout.default", "5 s",
                "junit.jupiter.execution.timeout.thread.mode.default", "SEPARATE_THREAD"), UsesTheCard.class);

        assertThat(results.testEvents().failed().stream().map(LiveCardThreadingTest::failure)).singleElement()
                .asString().startsWith(ExtensionConfigurationException.class.getName()).contains("SEPARATE_THREAD");
        assertThat(card.received()).isEmpty();
    }

    @Test
    void assertTimeoutPreemptivelyCannotUseTheCard() throws IOException {
        EngineExecutionResults results = run(Map.of(), PreemptiveTimeout.class);

        assertThat(results.testEvents().failed().stream().map(LiveCardThreadingTest::failure)).singleElement()
                .asString().startsWith(LiveCardException.class.getName())
                .contains("assertTimeoutPreemptively", "Nothing was sent to the card");
        assertThat(card.received()).isEmpty();
    }

    private static String failure(Event event) {
        return event.getPayload(TestExecutionResult.class).flatMap(TestExecutionResult::getThrowable)
                .map(Throwable::toString).orElse("");
    }

    private static void record(String testClass) {
        THREADS.computeIfAbsent(testClass, key -> ConcurrentHashMap.newKeySet()).add(Thread.currentThread().getName());
    }

    private static void enter() {
        MOST_ACTIVE_CLASSES.accumulateAndGet(ACTIVE_CLASSES.incrementAndGet(), Math::max);
    }

    /** Four tests that read the card's CPLC; with the next class they run in a parallel run. */
    @LiveCardTest
    static class FirstParallelClass {
        @BeforeAll
        static void start() {
            enter();
            record(FirstParallelClass.class.getSimpleName());
        }

        @AfterAll
        static void end() {
            ACTIVE_CLASSES.decrementAndGet();
        }

        @Test
        void first(LiveCard card) {
            readCplc(card, getClass());
        }

        @Test
        void second(LiveCard card) {
            readCplc(card, getClass());
        }

        @Test
        void third(LiveCard card) {
            readCplc(card, getClass());
        }

        @Test
        void fourth(LiveCard card) {
            readCplc(card, getClass());
        }
    }

    /** The same as {@link FirstParallelClass}. */
    @LiveCardTest
    static class SecondParallelClass {
        @BeforeAll
        static void start() {
            enter();
            record(SecondParallelClass.class.getSimpleName());
        }

        @AfterAll
        static void end() {
            ACTIVE_CLASSES.decrementAndGet();
        }

        @Test
        void first(LiveCard card) {
            readCplc(card, getClass());
        }

        @Test
        void second(LiveCard card) {
            readCplc(card, getClass());
        }

        @Test
        void third(LiveCard card) {
            readCplc(card, getClass());
        }

        @Test
        void fourth(LiveCard card) {
            readCplc(card, getClass());
        }
    }

    private static void readCplc(LiveCard card, Class<?> testClass) {
        record(testClass.getSimpleName());
        assertThat(card.session().send(0x80, 0xCA, 0x9F, 0x7F, null, 256).sw()).isEqualTo(0x9000);
    }

    /** A timeout in a separate thread. */
    @LiveCardTest
    static class SeparateThreadTimeout {
        @Test
        @Timeout(value = 5, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
        void readsTheCard(LiveCard card) {
            card.session().send(0x80, 0xCA, 0x9F, 0x7F, null, 256);
        }
    }

    /** Uses the card; run with a default timeout in a separate thread. */
    @LiveCardTest
    static class UsesTheCard {
        @Test
        void readsTheCard(LiveCard card) {
            card.session().send(0x80, 0xCA, 0x9F, 0x7F, null, 256);
        }
    }

    /** Sends a command inside assertTimeoutPreemptively, which runs it in another thread. */
    @LiveCardTest
    static class PreemptiveTimeout {
        @Test
        void readsTheCard(LiveCard card) {
            assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> card.session().send(0x80, 0xCA, 0x9F, 0x7F, null, 256));
        }
    }
}
