package name.velikodniy.jcexpress.livecard.model;

import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.livecard.guard.GuardViolationException;
import name.velikodniy.jcexpress.livecard.model.failing.ParametersRequiredApplet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a failure carries on every backend: a declared install that fails gets the APDU transcript like a failed test
 * (on the simulated GlobalPlatform card with the refused INSTALL [for install] and its '6A80', GPCS v2.3.1
 * 11.5.3.2), and a class that installs no applet at all starts its transcript with a hint, also when the APDU guard
 * blocked the command.
 */
class FailureTranscriptsOnEveryBackendTest {

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void aFailedDeclaredInstallCarriesTheTranscript(String backend) {
        assertThat(BackendRuns.failures(LifecycleBackendScenarios.DeclaredInstallFails.class, backend))
                .singleElement().isInstanceOf(InstallException.class)
                .satisfies(failure -> assertThat(BackendRuns.transcripts(failure)).singleElement()
                        .satisfies(transcript -> assertThat(transcript).contains(backend.equals("embedded")
                                ? "# install " + ParametersRequiredApplet.class.getName() : "R: 6A80")));
        assertThat(LifecycleBackendScenarios.SEEN).doesNotContainKey("DeclaredInstallFails");
    }

    @Test
    void onTheSimulatedCardTheTranscriptShowsTheRefusedInstall_gpcs231_11_5_3_2() {
        assertThat(BackendRuns.failures(LifecycleBackendScenarios.DeclaredInstallFails.class, "simulated-gp"))
                .singleElement().satisfies(failure -> assertThat(BackendRuns.transcripts(failure)).singleElement()
                        .satisfies(transcript -> assertThat(transcript)
                                .containsPattern("C: 84E60C00[0-9A-F]+\nR: 6A80")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"embedded", "simulated-gp"})
    void withoutAnyAppletTheTranscriptStartsWithAHint(String backend) {
        assertThat(BackendRuns.failures(LifecycleBackendScenarios.NothingInstalled.class, backend)).singleElement()
                .satisfies(failure -> assertThat(BackendRuns.transcripts(failure)).singleElement()
                        .satisfies(transcript -> assertThat(transcript.lines().skip(1).findFirst().orElseThrow())
                                .startsWith("# no applet is installed or selected: NothingInstalled declares no"
                                        + " @InstallApplet")));
    }

    @Test
    void theGuardStillBlocksTheCommandOfAClassWithoutApplet() {
        assertThat(BackendRuns.failures(LifecycleBackendScenarios.NothingInstalled.class, "simulated-gp"))
                .singleElement().isInstanceOf(GuardViolationException.class);
    }
}
