package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Maps the {@code javaCardVersion} parameter to the converter's target platform. The target
 * selects the CAP format version of the Header component (JCVM 3.1 &sect;6.4) and the versions of
 * the imported API packages (&sect;6.7).
 */
final class JavaCardVersions {

    /** The user property of the {@code javaCardVersion} parameter. */
    static final String PROPERTY = "javacard.version";

    private JavaCardVersions() {
    }

    /**
     * Parses a Java Card platform version.
     *
     * @param text a version such as {@code 3.0.5}; the short forms {@code 2.1}, {@code 2.2},
     *             {@code 3.0}, {@code 3.1} and {@code 3.2} select the latest release of that line
     * @return the target platform
     * @throws MojoExecutionException for any other value
     */
    static JavaCardVersion parse(String text) throws MojoExecutionException {
        String version = text == null ? "" : text.trim();
        return switch (version) {
            case "2.1.2", "2.1" -> JavaCardVersion.V2_1_2;
            case "2.2.1" -> JavaCardVersion.V2_2_1;
            case "2.2.2", "2.2" -> JavaCardVersion.V2_2_2;
            case "3.0.3" -> JavaCardVersion.V3_0_3;
            case "3.0.4" -> JavaCardVersion.V3_0_4;
            case "3.0.5", "3.0" -> JavaCardVersion.V3_0_5;
            case "3.1.0", "3.1" -> JavaCardVersion.V3_1_0;
            case "3.2.0", "3.2" -> JavaCardVersion.V3_2_0;
            default -> throw new MojoExecutionException("Unknown <javaCardVersion> '" + text + "'. Supported: "
                    + supported() + ".");
        };
    }

    /**
     * Warns when {@code -Djavacard.version} on the command line differs from the version the
     * execution uses. Maven gives a parameter configured in the POM precedence over a user property,
     * so {@code <javaCardVersion>} in the POM silently wins over the command line.
     *
     * @param userProperties the {@code -D} properties of the command line (may be {@code null})
     * @param configured     the {@code javaCardVersion} parameter as configured
     * @param effective      the target platform the execution uses
     * @param log            receives the warning
     */
    static void reportIgnoredCommandLine(Properties userProperties, String configured, JavaCardVersion effective,
                                         Log log) {
        String commandLine = userProperties == null ? null : userProperties.getProperty(PROPERTY);
        if (commandLine == null || isVersion(commandLine, effective)) {
            return;
        }
        log.warn("-D" + PROPERTY + "=" + commandLine + " has no effect: the POM sets <javaCardVersion>" + configured
                + "</javaCardVersion> for this execution, and Maven gives configuration in the POM precedence over the"
                + " command line. To choose the version on the command line, replace <javaCardVersion> with the"
                + " property " + PROPERTY + " in <properties>, or write <javaCardVersion>${" + PROPERTY
                + "}</javaCardVersion>.");
    }

    private static boolean isVersion(String text, JavaCardVersion version) {
        try {
            return parse(text) == version;
        } catch (MojoExecutionException e) {
            return false;
        }
    }

    /**
     * @param version a target platform
     * @return its version number, e.g. {@code 3.0.5}
     */
    static String display(JavaCardVersion version) {
        return version.name().substring(1).replace('_', '.');
    }

    private static String supported() {
        return Arrays.stream(JavaCardVersion.values()).map(JavaCardVersions::display).collect(Collectors.joining(", "));
    }
}
