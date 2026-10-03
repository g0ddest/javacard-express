package name.velikodniy.jcexpress.livecard.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The card of a {@code @JavaCardTest} class completes '61XX' with GET RESPONSE in the class of the command and
 * repeats a command answered '6CXX' with the exact Le (ISO/IEC 7816-4:2005 5.1.3) in {@code send}, {@code send}
 * of an {@code APDUCommand} and {@code sendHex}, on jCardSim and on the simulated GlobalPlatform card alike, as the
 * PC/SC provider of the JDK does on a real reader; {@code transmit} returns the card's answer as it is, and the
 * history shows every GET RESPONSE. The GET RESPONSE commands of the proprietary class pass the APDU guard because
 * the test's applet is selected.
 */
class GetResponseOnEveryBackendTest {

    private static final List<String> EXPECTED = List.of("send 9000 600 in order", "command 9000 600 in order",
            "hex 9000 600 in order", "exact 9000 5 0102030405", "transmit 258 6100",
            "history 00A40400 80400258 80C00000 80C00000");

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void sendCompletes61xxAnd6cxx_iso7816_4_5_1_3(String backend) {
        assertThat(BackendRuns.seen(LifecycleBackendScenarios.GetResponse.class, backend))
                .containsExactlyElementsOf(EXPECTED);
    }

    @Test
    void bothBackendsAnswerTheSame() {
        assertThat(BackendRuns.seen(LifecycleBackendScenarios.GetResponse.class, "simulated-gp"))
                .isEqualTo(BackendRuns.seen(LifecycleBackendScenarios.GetResponse.class, "embedded"));
    }
}
