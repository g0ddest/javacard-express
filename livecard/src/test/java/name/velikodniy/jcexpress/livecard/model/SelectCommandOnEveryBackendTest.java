package name.velikodniy.jcexpress.livecard.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code APDUCommand.select(AID)} (SELECT by DF name with Le '00', ISO/IEC 7816-4:2005 7.1.1) sent with
 * {@code card.send} returns the applet's answer to SELECT on every backend: on the simulated GlobalPlatform card the
 * APDU guard lets it pass (a SELECT in the inter-industry class) and the applet's commands pass after it.
 */
class SelectCommandOnEveryBackendTest {

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void theAnswerToSelectCanBeAsserted_iso7816_4_7_1_1(String backend) {
        assertThat(BackendRuns.seen(LifecycleBackendScenarios.SelectResponse.class, backend))
                .containsExactly("fci true", "then selects=2 deselects=1");
    }
}
