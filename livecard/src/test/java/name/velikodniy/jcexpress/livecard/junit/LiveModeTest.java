package name.velikodniy.jcexpress.livecard.junit;

import name.velikodniy.jcexpress.livecard.PcscConnector;
import name.velikodniy.jcexpress.livecard.sim.SimulatedCardConnector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When live-card tests run: with the reader (the PC/SC connector, the default) only when the JVM system property
 * {@code jcx.livecard.enabled} is {@code true}. A JUnit configuration parameter (from {@code junit-platform.properties}
 * or the launcher) and the environment do not switch the reader on; the configuration parameter only switches the
 * simulated cards of this module's own tests. The skip reason says what to do in any project.
 */
class LiveModeTest {

    private static final String SIMULATED = SimulatedCardConnector.class.getName();
    private static final Optional<String> NONE = Optional.empty();

    private static Optional<String> reason(String property, Optional<String> parameter, Optional<String> connector,
                                           String variable) {
        return LiveMode.disabledReason(property, parameter, connector, variable);
    }

    @ParameterizedTest(name = "-Djcx.livecard.enabled={0}")
    @CsvSource({"true", "TRUE", "' true '"})
    void theSystemPropertySwitchesTheReaderOn(String property) {
        assertThat(reason(property, NONE, NONE, null)).isEmpty();
        assertThat(reason(property, Optional.of("false"), Optional.of(PcscConnector.class.getName()), null))
                .as("the configuration parameter cannot switch it off either").isEmpty();
    }

    @Test
    void withoutTheSystemPropertyLiveTestsAreSkippedWithAReasonForAnyProject() {
        assertThat(reason(null, NONE, NONE, null)).hasValueSatisfying(reason -> assertThat(reason)
                .startsWith("live-card tests are disabled")
                .contains("-Djcx.livecard.enabled=true", "-Dtest=")
                .doesNotContain("-pl livecard", "-Plivecard", "./mvnw", "livecard-tests.sh"));
        assertThat(reason("false", NONE, NONE, null)).isPresent();
    }

    /** junit-platform.properties is a file: like a settings file, it cannot reach the card in the reader. */
    @Test
    void theConfigurationParameterDoesNotSwitchTheReaderOn() {
        assertThat(reason(null, Optional.of("true"), NONE, null)).hasValueSatisfying(reason -> assertThat(reason)
                .contains("configuration parameter jcx.livecard.enabled=true", "does not switch"));
        assertThat(reason(null, Optional.of("true"), Optional.of(PcscConnector.class.getName()), null))
                .as("the PC/SC connector named explicitly").isPresent();
    }

    @Test
    void theEnvironmentVariableDoesNotSwitchTheReaderOn() {
        assertThat(reason(null, NONE, NONE, "true")).hasValueSatisfying(reason -> assertThat(reason)
                .contains("JCX_LIVECARD_ENABLED", "does not switch"));
        assertThat(reason(null, NONE, NONE, "false")).hasValueSatisfying(reason -> assertThat(reason)
                .doesNotContain("JCX_LIVECARD_ENABLED"));
    }

    @Test
    void aValueOtherThanTrueIsNamed() {
        assertThat(reason("yes", NONE, NONE, null)).hasValueSatisfying(reason -> assertThat(reason)
                .contains("jcx.livecard.enabled=yes"));
    }

    /** This module's own tests switch a simulated card on with the configuration parameter. */
    @Test
    void theConfigurationParameterSwitchesATestConnector() {
        assertThat(reason(null, Optional.of("true"), Optional.of(SIMULATED), null)).isEmpty();
        assertThat(reason(null, Optional.of("false"), Optional.of(SIMULATED), null)).isPresent();
        assertThat(reason(null, NONE, Optional.of(SIMULATED), null)).isPresent();
        assertThat(reason("true", Optional.of("false"), Optional.of(SIMULATED), null))
                .as("a test launch decides for its simulated card").isPresent();
    }
}
