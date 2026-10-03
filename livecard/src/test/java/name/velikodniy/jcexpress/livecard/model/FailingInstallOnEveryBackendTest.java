package name.velikodniy.jcexpress.livecard.model;

import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.livecard.model.failing.ParametersRequiredApplet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Execution;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * An applet whose install method fails because the test forgot its install parameters: every backend reports an
 * {@link InstallException} that names the applet, the instance AID, the parameters and how a test passes them. On
 * the simulated GlobalPlatform card the card answers INSTALL [for install] with '6A80' like a card (GPCS v2.3.1
 * 11.5.3.2: incorrect parameters in the data field) instead of failing the transmission.
 */
class FailingInstallOnEveryBackendTest {

    @BeforeEach
    void clear() {
        BackendScenarios.SEEN.clear();
    }

    private static List<Throwable> failures(String backend) {
        EngineExecutionResults results = EngineTestKit.engine("junit-jupiter")
                .configurationParameters(Map.of(OnlyInTestKit.PARAMETER, "true", "jcx.backend", backend))
                .selectors(selectClass(BackendScenarios.ForgottenParameters.class)).execute();
        return results.allEvents().executions().failed().stream().map(Execution::getTerminationInfo)
                .map(info -> info.getExecutionResult().getThrowable().orElseThrow()).toList();
    }

    @Test
    void onJCardSimTheInstallMethodsIsoExceptionIsNamed() {
        assertThat(failures("embedded")).singleElement()
                .isInstanceOfSatisfying(InstallException.class, failure -> {
                    assertThat(failure).hasMessageContaining(ParametersRequiredApplet.class.getName())
                            .hasMessageContaining("with install parameters (none) failed: its install method threw"
                                    + " ISOException with reason 6A80")
                            .hasMessageContaining("@InstallApplet(params = ");
                    assertThat(failure.sw()).isZero();
                });
        assertThat(BackendScenarios.SEEN).isEmpty();
    }

    @Test
    void onTheSimulatedGlobalPlatformCardTheRefusedInstallIsNamedWithItsStatusWord_gpcs231_11_5_3_2() {
        assertThat(failures("simulated-gp")).singleElement()
                .isInstanceOfSatisfying(InstallException.class, failure -> {
                    assertThat(failure).hasMessageContaining(ParametersRequiredApplet.class.getName())
                            .hasMessageContaining("with install parameters (none) failed: the card refused INSTALL"
                                    + " [for install] with SW 6A80")
                            .hasMessageContaining("[Li][instance AID][Lc][control info][La][install parameters]")
                            .hasMessageContaining("@InstallApplet(params = ");
                    assertThat(failure.sw()).isEqualTo(0x6A80);
                    assertThat(failure.aid().toHex()).startsWith("F04A4358");
                });
        assertThat(BackendScenarios.SEEN).isEmpty();
    }
}
