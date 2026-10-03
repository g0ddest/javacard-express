package name.velikodniy.jcexpress.livecard;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recognizes CI/CD environments, where tests must never run against a live card.
 *
 * <p>A variable counts when it is set to anything but empty, {@code false} or {@code 0}. The variables of CI
 * platforms ({@link #PLATFORM_MARKERS}: GitHub Actions, GitLab CI, Azure Pipelines, Buildkite, Jenkins, TeamCity,
 * CircleCI, Travis CI, AppVeyor, Drone, Semaphore, AWS CodeBuild, Bitbucket Pipelines, Bamboo, GoCD) refuse
 * live-card mode unconditionally. The generic {@code CI} variable alone, which a development machine may set for
 * other tools, is refused unless the command line overrides it with the system property
 * {@value #ALLOW_PROPERTY}{@code =true}.</p>
 */
final class ContinuousIntegration {

    /** System property that allows live-card tests where only the generic {@code CI} variable is set. */
    static final String ALLOW_PROPERTY = "jcx.livecard.allowCi";

    /** Variables that CI platforms set on their runners; no override applies when one is present. */
    static final List<String> PLATFORM_MARKERS = List.of("GITHUB_ACTIONS", "GITLAB_CI", "TF_BUILD", "BUILDKITE",
            "JENKINS_URL", "TEAMCITY_VERSION", "CIRCLECI", "TRAVIS", "APPVEYOR", "DRONE", "SEMAPHORE",
            "CODEBUILD_BUILD_ID", "BITBUCKET_BUILD_NUMBER", "bamboo_buildKey", "GO_PIPELINE_NAME");
    private static final String GENERIC = "CI";

    /**
     * A variable that marks a CI environment.
     *
     * @param variable the variable, as {@code NAME=value}
     * @param platform true for a CI platform's own variable, false for the generic {@code CI}
     */
    record Marker(String variable, boolean platform) {
    }

    private ContinuousIntegration() {
    }

    /**
     * Finds the variable that marks a CI environment, a platform's own variable first.
     *
     * @param environment the environment variables
     * @return the marker, or empty outside CI
     */
    static Optional<Marker> detect(Map<String, String> environment) {
        for (String name : PLATFORM_MARKERS) {
            if (set(environment.get(name))) {
                return Optional.of(new Marker(name + "=" + environment.get(name), true));
            }
        }
        return set(environment.get(GENERIC)) ? Optional.of(new Marker(GENERIC + "=" + environment.get(GENERIC), false))
                : Optional.empty();
    }

    private static boolean set(String value) {
        if (value == null) {
            return false;
        }
        String text = value.strip().toLowerCase(Locale.ROOT);
        return !text.isEmpty() && !text.equals("false") && !text.equals("0");
    }

    /**
     * Refuses live-card mode in a CI environment: always on a CI platform, and with only the generic {@code CI}
     * variable unless {@value #ALLOW_PROPERTY}{@code =true} is a system property.
     *
     * @param sources the settings sources (their environment and system properties)
     * @throws LiveCardException if the environment looks like CI and no override applies
     */
    static void requireAllowed(ConfigSources sources) {
        Optional<Marker> marker = detect(sources.environment());
        if (marker.isEmpty()) {
            return;
        }
        boolean allowed = "true".equals(sources.systemProperties().getOrDefault(ALLOW_PROPERTY, "")
                .strip().toLowerCase(Locale.ROOT));
        if (marker.get().platform() || !allowed) {
            throw new LiveCardException("Live-card tests are refused: this looks like a CI/CD environment ("
                    + marker.get().variable() + "), and CI/CD must never run tests against a live card. "
                    + (marker.get().platform() ? "No override applies to a CI platform's own variables;"
                    + " -D" + ALLOW_PROPERTY + "=true only covers a development machine that sets the generic CI"
                    + " variable." : "On a machine with a development card that sets CI anyway, add -D"
                    + ALLOW_PROPERTY + "=true to the command line."));
        }
    }
}
