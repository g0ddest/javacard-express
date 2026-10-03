package name.velikodniy.jcexpress.extension;

import name.velikodniy.jcexpress.CounterApplet;
import name.velikodniy.jcexpress.HelloWorldApplet;
import name.velikodniy.jcexpress.JavaCardExtension;
import name.velikodniy.jcexpress.LoggingSession;
import name.velikodniy.jcexpress.Mode;
import name.velikodniy.jcexpress.SmartCard;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * User-style test classes that exercise {@link JavaCardExtension} the way the README documents it.
 * They are executed by {@link JavaCardExtensionTest} through the JUnit Platform test kit; each records
 * the sessions it was given in {@link #SEEN} so the test can check their lifetime afterwards.
 */
final class ExtensionScenarios {

    /** Sessions observed by each scenario, keyed by scenario name. */
    static final Map<String, List<SmartCardSession>> SEEN = new ConcurrentHashMap<>();

    private ExtensionScenarios() {
    }

    static void seen(String scenario, SmartCardSession session) {
        SEEN.computeIfAbsent(scenario, k -> new CopyOnWriteArrayList<>()).add(session);
    }

    static int hello(SmartCardSession card) {
        return card.send(0x80, 0x01).sw();
    }

    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class PerMethod {
        @SmartCard
        SmartCardSession card;

        @Test
        void first() {
            seen("PerMethod", card);
            card.install(HelloWorldApplet.class); // fresh card: installing again must work
            assertEquals(0x9000, hello(card));
        }

        @Test
        void second() {
            seen("PerMethod", card);
            card.install(HelloWorldApplet.class);
            assertEquals(0x9000, hello(card));
        }
    }

    /** PER_CLASS: one card for the test instance, installed once in @BeforeAll, state shared by tests. */
    @Scenario
    @ExtendWith(JavaCardExtension.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    static class PerClass {
        @SmartCard
        SmartCardSession card;

        @BeforeAll
        void installOnce() {
            seen("PerClass", card);
            card.install(CounterApplet.class);
        }

        @Test
        @Order(1)
        void first() {
            seen("PerClass", card);
            assertEquals(1, card.send(0x80, 0x01).data()[3]);
        }

        @Test
        @Order(2)
        void second() {
            seen("PerClass", card);
            assertEquals(2, card.send(0x80, 0x01).data()[3], "the card of the class survives between tests");
        }
    }

    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class StaticField {
        @SmartCard
        static SmartCardSession card;

        @BeforeAll
        static void installOnce() {
            seen("StaticField", card);
            card.install(HelloWorldApplet.class);
        }

        @Test
        void first() {
            seen("StaticField", card);
            assertEquals(0x9000, hello(card));
        }

        @Test
        void second() {
            seen("StaticField", card);
            assertEquals(0x9000, hello(card));
        }

        @AfterAll
        static void stillUsableInAfterAll() {
            assertEquals(0x9000, hello(card));
        }
    }

    @ExtendWith(JavaCardExtension.class)
    abstract static class AbstractCardTest {
        @SmartCard
        SmartCardSession card;
    }

    @Scenario
    static class InheritedField extends AbstractCardTest {
        @Test
        void usesInheritedField() {
            seen("InheritedField", card);
            card.install(HelloWorldApplet.class);
            assertEquals(0x9000, hello(card));
        }
    }

    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class NestedClasses {
        @SmartCard
        SmartCardSession outerCard;

        @Test
        void outerTest() {
            seen("Nested", outerCard);
            outerCard.install(HelloWorldApplet.class);
        }

        @Nested
        class Inner {
            @SmartCard
            SmartCardSession innerCard;

            @Test
            void innerA() {
                seen("Nested", outerCard);
                seen("Nested", innerCard);
                assertNotSame(outerCard, innerCard);
                outerCard.install(HelloWorldApplet.class);
                innerCard.install(HelloWorldApplet.class);
            }

            @Test
            void innerB() {
                seen("Nested", outerCard);
                seen("Nested", innerCard);
                outerCard.install(HelloWorldApplet.class);
            }
        }
    }

    /** Two tests run concurrently; the slow one checks its own card after the fast one finished. */
    @Scenario
    @ExtendWith(JavaCardExtension.class)
    @Execution(ExecutionMode.CONCURRENT)
    static class Parallel {
        static final CountDownLatch SLOW_READY = new CountDownLatch(1);
        static final CountDownLatch FAST_DONE = new CountDownLatch(1);

        @SmartCard
        SmartCardSession card;

        @Test
        void fast() throws InterruptedException {
            seen("Parallel", card);
            card.install(HelloWorldApplet.class);
            assertTrue(SLOW_READY.await(10, TimeUnit.SECONDS));
        }

        @AfterEach
        void signal() {
            FAST_DONE.countDown();
        }

        @Test
        void slow() throws InterruptedException {
            seen("Parallel", card);
            card.install(HelloWorldApplet.class);
            SLOW_READY.countDown();
            assertTrue(FAST_DONE.await(10, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertEquals(0x9000, hello(card), "the slow test's own card after the fast test finished");
        }
    }

    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class Parameterized {
        @SmartCard
        SmartCardSession card;

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3})
        void freshCardPerInvocation(int ignored) {
            seen("Parameterized", card);
            card.install(HelloWorldApplet.class);
            assertEquals(0x9000, hello(card));
        }
    }

    /** The card is still open in @AfterEach and closed only after it. */
    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class AfterEachUsesCard {
        @SmartCard
        SmartCardSession card;

        @Test
        void installs() {
            seen("AfterEachUsesCard", card);
            card.install(HelloWorldApplet.class);
        }

        @AfterEach
        void cleanup() {
            assertEquals(0x9000, hello(card));
        }
    }

    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class LogTrue {
        @SmartCard(log = true)
        SmartCardSession card;

        @Test
        void logsAndWorks() {
            seen("LogTrue", card);
            card.install(HelloWorldApplet.class);
            assertEquals(0x9000, hello(card));
            LoggingSession logged = assertInstanceOf(LoggingSession.class, card);
            assertEquals("80 01 00 00", logged.lastEntry().commandHex());
        }
    }

    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class FieldTypes {
        @SmartCard
        LoggingSession logged;

        @SmartCard
        EmbeddedSession embedded;

        @Test
        void subtypesAreInjected() {
            seen("FieldTypes", logged);
            seen("FieldTypes", embedded);
            logged.install(HelloWorldApplet.class);
            embedded.install(HelloWorldApplet.class);
            assertEquals(0x9000, hello(logged));
            assertEquals(0x9000, hello(embedded));
        }
    }

    /** jcx.log=true wraps fields that can hold a LoggingSession and leaves the others alone. */
    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class GlobalLog {
        @SmartCard
        SmartCardSession card;

        @SmartCard
        EmbeddedSession embedded;

        @Test
        void wrapsWhatFits() {
            seen("GlobalLog", card);
            seen("GlobalLog", embedded);
            assertInstanceOf(LoggingSession.class, card);
            assertSame(EmbeddedSession.class, embedded.getClass());
        }
    }

    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class LogOnEmbeddedField {
        @SmartCard(log = true)
        EmbeddedSession card;

        @Test
        void neverRuns() {
            seen("LogOnEmbeddedField", card);
        }
    }

    /** @SmartCard(log = true) on parameters, which receive embedded sessions. */
    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class LogParameters {
        @Test
        void logged(@SmartCard(log = true) SmartCardSession card) {
            seen("LogParameters", card);
            card.install(HelloWorldApplet.class);
            assertEquals(0x9000, hello(card));
            LoggingSession logged = assertInstanceOf(LoggingSession.class, card);
            assertEquals("80 01 00 00", logged.lastEntry().commandHex());
        }

        @Test
        void embeddedSessionsCannotBeLogged(@SmartCard(log = true) EmbeddedSession card) {
            seen("LogParametersEmbedded", card);
        }
    }

    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class WrongFieldType {
        @SmartCard
        String card;

        @Test
        void neverRuns() {
            // the extension rejects the field before the test runs
        }
    }

    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class PersistentMemory {
        @SuppressWarnings("deprecation")
        @SmartCard(persistentMemory = 2048)
        SmartCardSession card;

        @Test
        void neverRuns() {
            seen("PersistentMemory", card);
        }
    }

    /** Mode.CONTAINER in a project without the container module (core's own test class path has none). */
    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class ContainerModeWithoutTheModule {
        @SmartCard(mode = Mode.CONTAINER)
        SmartCardSession card;

        @Test
        void neverRuns() {
            seen("ContainerModeWithoutTheModule", card);
        }
    }

    /** The simulated GlobalPlatform card on a field of a class without @JavaCardTest. */
    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class SimulatedGpField {
        @SmartCard(mode = Mode.SIMULATED_GP)
        SmartCardSession card;

        @Test
        void neverRuns() {
            seen("SimulatedGpField", card);
        }
    }

    /** The real card on a field of a class without @JavaCardTest. */
    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class LiveCardField {
        @SmartCard(mode = Mode.LIVECARD)
        SmartCardSession card;

        @Test
        void neverRuns() {
            seen("LiveCardField", card);
        }
    }

    /** Backends on parameters of a class without @JavaCardTest, which receive embedded sessions. */
    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class BackendParameters {
        @Test
        void simulatedGp(@SmartCard(mode = Mode.SIMULATED_GP) SmartCardSession card) {
            seen("BackendParameters", card);
        }

        @Test
        void container(@SmartCard(mode = Mode.CONTAINER) SmartCardSession card) {
            seen("BackendParameters", card);
        }

        @Test
        void embedded(@SmartCard SmartCardSession card) {
            seen("BackendParametersEmbedded", card);
        }
    }

    /** core/README.md "Complete Test Lifecycle", with HelloWorldApplet as MyApplet. */
    @Scenario
    @ExtendWith(JavaCardExtension.class)
    static class ReadmeLifecycle {
        @SmartCard
        SmartCardSession card;

        @Test
        void shouldProcessCommand() {
            card.install(HelloWorldApplet.class);
            byte[] data = card.send(0x80, 0x01).requireSuccess().data();
            assertEquals("Hello", new String(data, java.nio.charset.StandardCharsets.US_ASCII));
        }

        @Test
        void shouldSurviveReset() {
            card.install(HelloWorldApplet.class);
            card.send(0x80, 0x01).requireSuccess();

            card.reset();
            card.select(HelloWorldApplet.class);
            card.send(0x80, 0x01).requireSuccess();
        }
    }
}
