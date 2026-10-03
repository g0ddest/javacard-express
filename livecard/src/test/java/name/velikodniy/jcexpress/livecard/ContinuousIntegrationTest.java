package name.velikodniy.jcexpress.livecard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CI/CD must never run tests against a live card: live-card mode is refused when the environment looks like CI,
 * unless the command line overrides it with {@code -Djcx.livecard.allowCi=true}.
 */
class ContinuousIntegrationTest {

    private static final Map<String, String> ENABLED = Map.of("jcx.livecard.enabled", "true");

    @TempDir
    Path tmp;

    private static LiveCardConfig load(Map<String, String> properties, Map<String, String> environment) {
        return LiveCardConfig.load(new ConfigSources(properties, environment, List.of()));
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
        "CI, true",
        "CI, TRUE",
        "CI, 1",
        "GITHUB_ACTIONS, true",
        "GITLAB_CI, true",
        "TF_BUILD, True",
        "BUILDKITE, true",
        "JENKINS_URL, https://jenkins.example/",
        "TEAMCITY_VERSION, 2024.12",
    })
    void liveModeIsRefusedOnCi(String variable, String value) {
        assertThatThrownBy(() -> load(ENABLED, Map.of(variable, value)))
                .isInstanceOf(LiveCardException.class)
                .hasMessageContaining("Live-card tests are refused")
                .hasMessageContaining(variable + "=" + value)
                .hasMessageContaining("CI/CD must never run tests against a live card")
                .hasMessageContaining("-Djcx.livecard.allowCi=true");
    }

    /** Only the JVM system property switches live-card mode on; the environment cannot, on CI or elsewhere. */
    @Test
    void theEnvironmentCannotEnableLiveMode() {
        assertThatThrownBy(() -> load(Map.of(), Map.of("JCX_LIVECARD_ENABLED", "true", "GITHUB_ACTIONS", "true")))
                .isInstanceOf(LiveCardException.class).hasMessageContaining("JCX_LIVECARD_ENABLED=true is refused");
        assertThatThrownBy(() -> load(Map.of(), Map.of("JCX_LIVECARD_ENABLED", "true")))
                .isInstanceOf(LiveCardException.class).hasMessageContaining("JCX_LIVECARD_ENABLED=true is refused");
    }

    /** Any value of CI but empty, false or 0 (Woodpecker sets CI=woodpecker), and more platforms' own variables. */
    @ParameterizedTest(name = "{0}={1}")
    @CsvSource({
        "CI, woodpecker",
        "CI, yes",
        "CODEBUILD_BUILD_ID, jcx:1234",
        "BITBUCKET_BUILD_NUMBER, 17",
        "CIRCLECI, true",
        "TRAVIS, true",
        "bamboo_buildKey, JCX-PLAN-JOB1",
        "GO_PIPELINE_NAME, jcx",
        "DRONE, true",
        "APPVEYOR, True",
        "SEMAPHORE, true",
    })
    void otherCiEnvironmentsAreRefusedToo(String variable, String value) {
        assertThatThrownBy(() -> load(ENABLED, Map.of(variable, value)))
                .isInstanceOf(LiveCardException.class)
                .hasMessageContaining(variable + "=" + value);
    }

    /** The override is for a development machine that happens to set CI, never for a CI platform's runner. */
    @ParameterizedTest(name = "{0}")
    @CsvSource({"GITHUB_ACTIONS", "GITLAB_CI", "JENKINS_URL", "CODEBUILD_BUILD_ID"})
    void overrideDoesNotOpenLiveModeOnACiPlatform(String variable) {
        Map<String, String> properties = Map.of("jcx.livecard.enabled", "true", LiveCardConfig.ALLOW_CI_PROPERTY,
                "true");

        assertThatThrownBy(() -> load(properties, Map.of("CI", "true", variable, "true")))
                .isInstanceOf(LiveCardException.class)
                .hasMessageContaining("No override applies to a CI platform's own variables");
    }

    @Test
    void explicitSystemPropertyAllowsLiveModeOnCi() {
        Map<String, String> properties = Map.of("jcx.livecard.enabled", "true",
                LiveCardConfig.ALLOW_CI_PROPERTY, "true");

        assertThat(load(properties, Map.of("CI", "true")).enabled()).isTrue();
    }

    @Test
    void overrideIsNotTakenFromTheEnvironment() {
        Map<String, String> environment = Map.of("CI", "true", "JCX_LIVECARD_ALLOW_CI", "true");

        assertThatThrownBy(() -> load(ENABLED, environment)).isInstanceOf(LiveCardException.class);
    }

    @Test
    void overrideInASettingsFileIsAnError() throws IOException {
        Path file = Files.writeString(tmp.resolve("livecard.properties"), "enabled=true\nallowCi=true\n");

        assertThatThrownBy(() -> LiveCardConfig.load(new ConfigSources(Map.of(), Map.of("CI", "true"), List.of(file))))
                .isInstanceOf(LiveCardException.class)
                .hasMessageContaining("only as a system property");
    }

    /**
     * Live mode is switched on per run, with the JVM system property only (-Djcx.livecard.enabled=true, which
     * -Plivecard sets), never by a settings file, which would make every IDE run of the tests
     * reach the card in the reader.
     */
    @Test
    void enabledInASettingsFileIsRefused() throws IOException {
        Path file = Files.writeString(tmp.resolve("livecard.properties"), "enabled=true\n");
        Path off = Files.writeString(tmp.resolve("off.properties"), "enabled=false\n");

        assertThatThrownBy(() -> LiveCardConfig.load(new ConfigSources(Map.of(), Map.of(), List.of(file))))
                .isInstanceOf(LiveCardException.class)
                .hasMessageContaining("enabled=true in " + file)
                .hasMessageContaining("-Djcx.livecard.enabled=true")
                .hasMessageNotContaining("JCX_LIVECARD_ENABLED");
        assertThat(LiveCardConfig.load(new ConfigSources(Map.of(), Map.of(), List.of(off))).enabled()).isFalse();
    }

    @Test
    void disabledSettingsLoadOnCi() {
        Map<String, String> ci = Map.of("CI", "true", "GITHUB_ACTIONS", "true");

        assertThat(load(Map.of("jcx.livecard.enabled", "false"), ci).enabled()).isFalse();
        assertThat(load(Map.of(), ci).enabled()).isFalse();
    }

    @Test
    void ordinaryEnvironmentsAreNotCi() {
        assertThat(load(ENABLED, Map.of("CI", "false", "HOME", "/home/dev")).enabled()).isTrue();
        assertThat(load(ENABLED, Map.of("CI", "0", "GITHUB_ACTIONS", "")).enabled()).isTrue();
        assertThat(ContinuousIntegration.detect(Map.of("JENKINS_URL", " "))).isEmpty();
    }
}
