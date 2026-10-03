package name.velikodniy.jcexpress.livecard.junit;

import name.velikodniy.jcexpress.livecard.LiveCardConfig.Setting;
import name.velikodniy.jcexpress.livecard.PcscConnector;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Locale;
import java.util.Optional;

/**
 * Decides whether the classes of {@link LiveCardTest} run, before anything looks for a reader.
 *
 * <p>With the reader ({@link PcscConnector}, the default connector) live-card tests run only when the JVM system
 * property {@code jcx.livecard.enabled} is {@code true}: given for one run on the command line
 * ({@code -Djcx.livecard.enabled=true}, Maven passes it to the test JVM) or as a VM option of an IDE run. Nothing
 * else switches the reader on: not the JUnit configuration parameter of the same name (it may come from
 * {@code junit-platform.properties}, a file), not the environment variable {@code JCX_LIVECARD_ENABLED} and not a
 * settings file ({@link name.velikodniy.jcexpress.livecard.ConfigSources} refuses both when it reads the
 * settings). The configuration parameter only switches a test connector (a simulated card) named by
 * {@value LiveCardExtension#CONNECTOR_PARAMETER}, for the tests of this module.</p>
 */
final class LiveMode {

    private static final String SWITCH = "-D" + LiveCardExtension.ENABLED_PARAMETER + "=true";
    private static final String DISABLED = "live-card tests are disabled: to run them on the machine with the card"
            + " reader, set the JVM system property " + SWITCH + " for that run (e.g. mvn test -Dtest=MyAppletLiveTest "
            + SWITCH + ", or a VM option of the IDE run); never on CI";

    private LiveMode() {
    }

    /**
     * Returns why live-card tests do not run in this context.
     *
     * @param context the context of a test class or method
     * @return the reason, or empty when they may run
     */
    static Optional<String> disabledReason(ExtensionContext context) {
        return disabledReason(System.getProperty(LiveCardExtension.ENABLED_PARAMETER),
                context.getConfigurationParameter(LiveCardExtension.ENABLED_PARAMETER),
                context.getConfigurationParameter(LiveCardExtension.CONNECTOR_PARAMETER),
                System.getenv(Setting.ENABLED.environmentVariable()));
    }

    /**
     * Returns why live-card tests do not run.
     *
     * @param systemProperty the JVM system property {@code jcx.livecard.enabled}, or null
     * @param parameter      the JUnit configuration parameter {@code jcx.livecard.enabled}
     * @param connector      the JUnit configuration parameter {@value LiveCardExtension#CONNECTOR_PARAMETER}
     * @param variable       the environment variable {@code JCX_LIVECARD_ENABLED}, or null
     * @return the reason, or empty when they may run
     */
    static Optional<String> disabledReason(String systemProperty, Optional<String> parameter,
                                           Optional<String> connector, String variable) {
        boolean reader = connector.map(PcscConnector.class.getName()::equals).orElse(true);
        if (!reader && parameter.isPresent()) {
            return isTrue(parameter.get()) ? Optional.empty() : Optional.of(DISABLED);
        }
        if (isTrue(systemProperty)) {
            return Optional.empty();
        }
        return Optional.of(DISABLED + hints(systemProperty, reader ? parameter : Optional.empty(), variable));
    }

    /** What the user may have tried that does not switch live-card tests on. */
    private static String hints(String systemProperty, Optional<String> parameter, String variable) {
        StringBuilder hints = new StringBuilder();
        if (systemProperty != null && !systemProperty.strip().equalsIgnoreCase("false")) {
            hints.append("; ").append(LiveCardExtension.ENABLED_PARAMETER).append('=').append(systemProperty.strip())
                    .append(" is not true");
        }
        if (parameter.filter(LiveMode::isTrue).isPresent() && !isTrue(systemProperty)) {
            hints.append("; the JUnit configuration parameter ").append(LiveCardExtension.ENABLED_PARAMETER)
                    .append("=true (junit-platform.properties, the launcher) does not switch the reader on");
        }
        if (variable != null && !variable.strip().equalsIgnoreCase("false")) {
            hints.append("; the environment variable ").append(Setting.ENABLED.environmentVariable())
                    .append(" does not switch the reader on");
        }
        return hints.toString();
    }

    private static boolean isTrue(String value) {
        return value != null && value.strip().toLowerCase(Locale.ROOT).equals("true");
    }
}
