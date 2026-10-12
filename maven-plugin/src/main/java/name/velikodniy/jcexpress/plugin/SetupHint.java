package name.velikodniy.jcexpress.plugin;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * What the {@code build} goal says about the build extension when the extension has not set the project up: a hint
 * when the plugin is not declared as a build extension, a warning when it is but the extension did not run (an IDE's
 * Maven import or a Maven that does not run build extensions), naming the Maven and JDK versions.
 */
final class SetupHint {

    private SetupHint() {
    }

    /**
     * Logs the hint or the warning, unless the extension set the project up or the setup is turned off.
     *
     * @param project the project
     * @param setup   the resolved {@code setup} parameter
     * @param check   the resolved {@code check} parameter
     * @param log     the goal's log
     */
    static void log(MavenProject project, boolean setup, boolean check, Log log) {
        if (!setup || project.getContextValue(ProjectsSetup.CONTEXT_KEY) != null) {
            return;
        }
        Plugin declared = project.getPlugin(ProjectSetup.PLUGIN_KEY);
        if (declared != null && declared.isExtensions()) {
            log.warn("javacard-express-maven-plugin is declared with <extensions>true</extensions>, but its build"
                    + " extension did not set this project up (Maven " + mavenVersion() + ", JDK "
                    + Runtime.version() + "): the compiler settings, the dependencies and the javac check it adds"
                    + " are missing. It runs on Maven 3.9.0 or newer with JDK " + ProjectsSetup.REQUIRED_JDK + ";"
                    + " see the plugin README. -Djavacard.setup=false hides this line.");
            return;
        }
        log.info("Declared with <extensions>true</extensions>, this plugin also sets the project up (compiler,"
                + " dependencies" + (check ? ", the javac check of the Java Card subset" : "") + "); see its"
                + " README. -Djavacard.setup=false hides this line.");
    }

    /** The version of the running Maven, from its core's descriptor (as Maven's own runtime information reads it). */
    private static String mavenVersion() {
        try (InputStream in = MavenProject.class.getResourceAsStream(
                "/META-INF/maven/org.apache.maven/maven-core/pom.properties")) {
            if (in == null) {
                return "of unknown version";
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty("version", "of unknown version");
        } catch (IOException e) {
            return "of unknown version";
        }
    }
}
