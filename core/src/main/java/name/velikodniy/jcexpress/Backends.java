package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.backend.CardBackend;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Chooses the backend of {@link JavaCardTest} classes: the {@value #SETTING} system property, then the JUnit
 * configuration parameter, then the {@value #ENVIRONMENT} environment variable, else {@link Mode#EMBEDDED}.
 * {@code livecard} counts only as a JVM system property.
 */
final class Backends {

    /** The setting that names the backend. */
    static final String SETTING = "jcx.backend";

    /** The environment variable that names the backend (not {@code livecard}). */
    static final String ENVIRONMENT = "JCX_BACKEND";

    private Backends() {
    }

    /**
     * Returns the backend of the run.
     *
     * @param context any context of the run
     * @return the mode
     * @throws ExtensionConfigurationException if the value is unknown, or {@code livecard} does not come from a
     *                                         system property
     */
    static Mode configured(ExtensionContext context) {
        String system = System.getProperty(SETTING);
        if (system != null && !system.isBlank()) {
            return parse(system, "the system property " + SETTING);
        }
        Optional<String> parameter = context.getConfigurationParameter(SETTING).filter(v -> !v.isBlank());
        String environment = System.getenv(ENVIRONMENT);
        if (parameter.isEmpty() && (environment == null || environment.isBlank())) {
            return Mode.EMBEDDED;
        }
        String source = parameter.isPresent() ? "the JUnit configuration parameter " + SETTING
                : "the environment variable " + ENVIRONMENT;
        Mode mode = parse(parameter.orElse(environment), source);
        if (mode == Mode.LIVECARD) {
            throw new ExtensionConfigurationException(SETTING + "=livecard was set by " + source + ", but the real"
                    + " card is selected only with the JVM system property -D" + SETTING + "=livecard on the command"
                    + " line, so that no file or environment can switch a build to the card");
        }
        return mode;
    }

    /**
     * Returns the backend named by the system property alone, for callers without an extension context.
     *
     * @return the mode, {@link Mode#EMBEDDED} when the property is absent or unknown
     */
    static Mode fromSystemProperty() {
        String system = System.getProperty(SETTING);
        if (system == null || system.isBlank()) {
            return Mode.EMBEDDED;
        }
        try {
            return parse(system, SETTING);
        } catch (ExtensionConfigurationException e) {
            return Mode.EMBEDDED;
        }
    }

    /**
     * Parses a backend name: {@code embedded}, {@code container}, {@code simulated-gp}, {@code livecard} (the
     * {@link Mode} constant names are accepted too).
     *
     * @param value  the value
     * @param source where it came from, for the message
     * @return the mode
     * @throws ExtensionConfigurationException if the value names no backend
     */
    static Mode parse(String value, String source) {
        String name = value.strip().toLowerCase(Locale.ROOT).replace('_', '-');
        return switch (name) {
            case "embedded", "jcardsim" -> Mode.EMBEDDED;
            case "container" -> Mode.CONTAINER;
            case "simulated-gp" -> Mode.SIMULATED_GP;
            case "livecard", "live-card" -> Mode.LIVECARD;
            default -> throw new ExtensionConfigurationException("Unknown backend '" + value + "' in " + source
                    + "; use embedded, container, simulated-gp or livecard");
        };
    }

    /**
     * Returns the name of a backend as {@value #SETTING} spells it.
     *
     * @param mode the mode
     * @return the name
     */
    static String name(Mode mode) {
        return mode.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Finds the implementation of a backend on the test class path.
     *
     * @param mode the mode
     * @return the backend
     * @throws ExtensionConfigurationException if the module that provides it is missing
     */
    static CardBackend backend(Mode mode) {
        if (mode == Mode.CONTAINER) {
            throw new ExtensionConfigurationException(SETTING + "=container is not available for @JavaCardTest"
                    + " classes yet (the container protocol cannot delete applets, which per-test isolation needs):"
                    + " use @SmartCard(mode = Mode.CONTAINER) fields, or the backends embedded and simulated-gp");
        }
        for (CardBackend backend : ServiceLoader.load(CardBackend.class, Backends.class.getClassLoader())) {
            if (backend.mode() == mode) {
                return backend;
            }
        }
        String module = switch (mode) {
            case CONTAINER -> "javacard-express-container";
            case SIMULATED_GP, LIVECARD -> "javacard-express-livecard";
            case EMBEDDED -> "javacard-express-core";
        };
        throw new ExtensionConfigurationException(SETTING + "=" + name(mode) + " needs " + module + " on the test"
                + " class path: add the dependency name.velikodniy:" + module + " with scope test");
    }
}
