package name.velikodniy.jcexpress.livecard.junit;

import name.velikodniy.jcexpress.livecard.LiveCardConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Without {@code -Plivecard} no live-card test runs and nothing looks for a reader: the build sets
 * {@code jcx.livecard.enabled=false}, the extension skips every {@link LiveCardTest} class before connecting, with a
 * reason that tells any project how to run them, and the PC/SC gate stays closed.
 */
class LiveTestsSkippedWithoutProfileTest {

    private static final List<String> SUITE = List.of(
            "name.velikodniy.jcexpress.livecard.live.AppletLifecycleLiveTest",
            "name.velikodniy.jcexpress.livecard.live.CardIdentificationLiveTest",
            "name.velikodniy.jcexpress.livecard.live.ConverterFeaturesLiveTest",
            "name.velikodniy.jcexpress.livecard.live.CryptoApiLiveTest",
            "name.velikodniy.jcexpress.livecard.live.InstallAndStatusLiveTest",
            "name.velikodniy.jcexpress.livecard.live.OpenSourceAppletsLiveTest",
            "name.velikodniy.jcexpress.livecard.live.PcscSessionLiveTest",
            "name.velikodniy.jcexpress.livecard.live.SecureChannelLiveTest");

    /** Runs in the Maven build of this module, whose surefire configuration sets the property. */
    @Test
    @EnabledIfSystemProperty(named = "jcx.livecard.test.appletClasses", matches = ".+")
    void mavenBuildDisablesLiveCardTests() {
        assertThat(System.getProperty("jcx.livecard.enabled")).as("surefire system property").isEqualTo("false");
        assertThat(LiveCardConfig.load().enabled()).isFalse();
    }

    @Test
    void liveCardTestAddsTheTagAndTheExtension() {
        assertThat(LiveCardTest.class.getAnnotation(Tag.class).value()).isEqualTo("livecard");
        assertThat(LiveCardTest.class.getAnnotation(ExtendWith.class).value()).containsExactly(LiveCardExtension.class);
    }

    @Test
    void everyLiveTestClassIsSkippedWithoutTouchingAReader() throws IOException {
        List<String> classes = LiveTestClasses.annotated();
        assertThat(classes).as("live-card test classes found in %s", LiveTestClasses.testClassesRoot())
                .containsAll(SUITE);
        TripwireConnector.reset();

        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .configurationParameter(LiveCardExtension.ENABLED_PARAMETER, "false")
                .configurationParameter(LiveCardExtension.CONNECTOR_PARAMETER, TripwireConnector.class.getName())
                .selectors(classes.stream().map(DiscoverySelectors::selectClass).toArray(DiscoverySelector[]::new))
                .execute();

        assertThat(results.testEvents().started().count()).isZero();
        assertThat(results.containerEvents().skipped().count()).isEqualTo(classes.size());
        assertThat(results.containerEvents().skipped().stream().map(LiveTestsSkippedWithoutProfileTest::reason))
                .allSatisfy(reason -> assertThat(reason).contains("live-card tests are disabled")
                        .contains("-Djcx.livecard.enabled=true").doesNotContain("-pl livecard", "-Plivecard"));
        assertThat(TripwireConnector.CALLS).hasValue(0);
    }

    private static String reason(Event event) {
        return event.getPayload(String.class).orElse("");
    }
}
