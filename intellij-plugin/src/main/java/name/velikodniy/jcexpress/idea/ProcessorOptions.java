package name.velikodniy.jcexpress.idea;

import java.util.Collection;
import java.util.Map;

/**
 * The processor options of the javac check that the plugin reads itself, and the parsing of {@code -Akey=value}
 * arguments.
 */
final class ProcessorOptions {

    /** {@code false} turns the check off. */
    static final String CHECK = "javacard.check";
    /** The packages the build checks, comma-separated. */
    static final String PACKAGE_NAME = "javacard.packageName";
    /** The jar of the check, by its file name. */
    static final String CHECK_JAR = "javacard-express-javac-plugin";

    private ProcessorOptions() {
    }

    /**
     * Adds the processor options among javac arguments to a map; other arguments are ignored. A later option wins,
     * as in javac.
     *
     * @param arguments javac arguments, e.g. {@code -Ajavacard.version=3.0.4}
     * @param options   the map to add to
     */
    static void addFrom(Collection<String> arguments, Map<String, String> options) {
        for (String argument : arguments) {
            if (argument != null && argument.startsWith("-A") && argument.length() > 2) {
                String option = argument.substring(2);
                int equals = option.indexOf('=');
                options.put(equals < 0 ? option : option.substring(0, equals),
                        equals < 0 ? "" : option.substring(equals + 1));
            }
        }
    }

    /**
     * Returns whether a value is set: a Maven expression nothing resolved counts as not set, as in the check.
     *
     * @param value the option's value, or {@code null}
     * @return whether it is a real value
     */
    static boolean isSet(String value) {
        return value != null && !value.isBlank() && !value.contains("${");
    }
}
