package name.velikodniy.jcexpress.livecard.junit;

import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.live.TestApplet;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCard;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCardConnector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The extension's lifecycle against a simulated card: injection, transcripts per test, cleanup after failures,
 * shared connections for nested classes, and the run-wide abort after an authentication failure. The sample
 * classes below are nested (Surefire does not run them directly) and are run through the JUnit test kit.
 */
class LiveCardExtensionTest {

    @TempDir
    Path tmp;

    private SimulatedCard card;

    @BeforeEach
    void insertCard() {
        card = SimulatedCardConnector.insertNewCard();
    }

    private EngineExecutionResults run(Class<?>... classes) throws IOException {
        Path settings = Files.writeString(tmp.resolve("livecard.properties"), "transcriptDir=" + tmp.resolve("t")
                .toString().replace("\\", "\\\\") + "\nverifierSdk=none\n");
        return EngineTestKit.engine("junit-jupiter")
                .configurationParameter(LiveCardExtension.ENABLED_PARAMETER, "true")
                .configurationParameter(LiveCardExtension.CONNECTOR_PARAMETER, SimulatedCardConnector.class.getName())
                .configurationParameter(LiveCardExtension.ISOLATED_RUN_PARAMETER, "true")
                .configurationParameter(LiveCardExtension.SETTINGS_FILE_PARAMETER, settings.toString())
                .configurationParameter("junit.jupiter.testclass.order.default", ClassOrderer.ClassName.class.getName())
                .selectors(Stream.of(classes).map(DiscoverySelectors::selectClass).toArray(DiscoverySelector[]::new))
                .execute();
    }

    @Test
    void cleanupRunsAfterAFailingTest() throws IOException {
        EngineExecutionResults results = run(FailsAfterDeploying.class);

        results.testEvents().assertStatistics(stats -> stats.started(1).failed(1));
        results.containerEvents().assertStatistics(stats -> stats.failed(0));
        assertThat(card.applications()).isEmpty();
        Path transcripts = tmp.resolve("t").resolve(FailsAfterDeploying.class.getName());
        assertThat(transcripts.resolve("before-all.txt")).content().contains("C: 84E602");
        assertThat(transcripts.resolve("failsAfterUsingTheApplet.txt")).content().contains("C: 8001000000");
        assertThat(transcripts.resolve("after-all.txt")).content().contains("cleanup verified with GET STATUS");
    }

    @Test
    void cleanupRunsWhenBeforeAllFailsAfterDeploying() throws IOException {
        EngineExecutionResults results = run(BeforeAllFailsAfterDeploying.class);

        results.testEvents().assertStatistics(stats -> stats.started(0));
        results.containerEvents().assertStatistics(stats -> stats.failed(1));
        assertThat(card.applications()).isEmpty();
    }

    @Test
    void nestedClassesShareTheConnection() throws IOException {
        EngineExecutionResults results = run(WithNestedClass.class);

        results.testEvents().assertStatistics(stats -> stats.succeeded(2).failed(0));
        assertThat(SimulatedCardConnector.connections()).isEqualTo(1);
        assertThat(card.applications()).isEmpty();
    }

    /**
     * With the per-class lifecycle JUnit creates the instance before the before-all callbacks: the constructor's card
     * is connected when it is resolved (it failed with "the connection failed in @BeforeAll").
     */
    @Test
    void perClassLifecycleWithConstructorInjectionGetsTheCard() throws IOException {
        EngineExecutionResults results = run(PerClassConstructorInjection.class);

        results.allEvents().assertStatistics(stats -> stats.failed(0));
        results.testEvents().assertStatistics(stats -> stats.succeeded(1));
        assertThat(SimulatedCardConnector.connections()).isEqualTo(1);
        assertThat(card.applications()).isEmpty();
    }

    /** When the after-all callbacks do not run (a failing constructor), closing JUnit's store still cleans up. */
    @Test
    void cleanupRunsWhenTheConstructorFailsAfterDeploying() throws IOException {
        EngineExecutionResults results = run(ConstructorFailsAfterDeploying.class);

        results.testEvents().assertStatistics(stats -> stats.started(0));
        results.containerEvents().assertStatistics(stats -> stats.failed(1));
        assertThat(card.applications()).isEmpty();
        assertThat(card.received()).as("the card was cleaned up").anyMatch(command -> command.startsWith("84E4"));
    }

    @Test
    void authenticationFailureAbortsTheRestOfTheRun() throws IOException {
        card = SimulatedCardConnector.insertNewCard(HexFormat.of().parseHex("0F".repeat(16)));

        EngineExecutionResults results = run(AuthenticatesWithWrongKeys.class, BAfterTheAbort.class);

        results.testEvents().assertStatistics(stats -> stats.started(1).failed(1));
        assertThat(results.containerEvents().skipped().stream()
                .map(event -> event.getPayload(String.class).orElse("")))
                .singleElement().asString().contains("live-card run aborted")
                .contains("does not match the configured keys");
        assertThat(card.externalAuthentications()).isZero();
    }

    /** Deploys in {@code @BeforeAll}, then a test fails. */
    @LiveCardTest
    static class FailsAfterDeploying {
        @BeforeAll
        static void deploy(LiveCard card) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
        }

        @Test
        void failsAfterUsingTheApplet(LiveCard card) {
            card.session().select(TestApplet.HELLO.moduleAid(card.config()));
            card.session().send(0x80, 0x01, 0x00, 0x00, null, 256);
            fail("deliberate failure");
        }
    }

    /** {@code @BeforeAll} fails after the deployment. */
    @LiveCardTest
    static class BeforeAllFailsAfterDeploying {
        @BeforeAll
        static void deployAndFail(LiveCard card) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
            fail("deliberate failure in @BeforeAll");
        }

        @Test
        void neverRuns() {
            fail("must not run");
        }
    }

    /** A nested class uses the enclosing class's connection; constructor injection works. */
    @LiveCardTest
    static class WithNestedClass {
        private final LiveCard card;

        WithNestedClass(LiveCard card) {
            this.card = card;
        }

        @Test
        void deploys() {
            card.deploy(TestApplet.PARAMS.pkg(card.config()));
        }

        @Nested
        class Inner {
            @Test
            void seesTheSameCard(LiveCard inner) {
                assertThat(inner).isSameAs(card);
            }
        }
    }

    /** Per-class lifecycle: the constructor takes the card before any before-all callback. */
    @LiveCardTest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class PerClassConstructorInjection {
        private final LiveCard card;

        PerClassConstructorInjection(LiveCard card) {
            this.card = card;
        }

        @BeforeAll
        void deploy() {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
        }

        @Test
        void usesTheSameCard(LiveCard parameter) {
            assertThat(parameter).isSameAs(card);
            card.session().select(TestApplet.HELLO.moduleAid(card.config()));
            assertThat(card.session().send(0x80, 0x01, 0x00, 0x00, null, 256).dataAsString()).isEqualTo("Hello, card");
        }
    }

    /** Deploys in the constructor, then fails there: JUnit runs no before-all or after-all callback. */
    @LiveCardTest
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class ConstructorFailsAfterDeploying {
        ConstructorFailsAfterDeploying(LiveCard card) {
            card.deploy(TestApplet.HELLO.pkg(card.config()));
            throw new IllegalStateException("deliberate failure in the constructor");
        }

        @Test
        void neverRuns() {
            fail("must not run");
        }
    }

    /** Opens a secure channel on a card with other keys. */
    @LiveCardTest
    static class AuthenticatesWithWrongKeys {
        @Test
        void opensSecureChannel(LiveCard card) {
            card.gp();
        }
    }

    /** Runs after {@link AuthenticatesWithWrongKeys} (class name order) and must be skipped. */
    @LiveCardTest
    static class BAfterTheAbort {
        @Test
        void mustBeSkipped() {
            fail("must not run after the abort");
        }
    }
}
