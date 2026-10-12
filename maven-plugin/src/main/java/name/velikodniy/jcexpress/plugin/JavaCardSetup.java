package name.velikodniy.jcexpress.plugin;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * The plugin as a build extension: a project that declares {@code javacard-express-maven-plugin} with
 * {@code <extensions>true</extensions>} needs nothing else. After Maven has read the projects, this sets each such
 * project up with what it has not configured itself (the work is done by {@code ProjectsSetup}).
 *
 * <p>Maven finds this class through the index of the plugin's jar ({@code META-INF/sisu/javax.inject.Named}) and
 * reads it with a class scanner before it loads it; the scanner of Maven 3.9.0 to 3.9.9 knows class files only up
 * to Java 11, and Maven 3.9 itself runs on JDK 8, which cannot load newer class files. Either way a newer class
 * would be skipped without a word. So this class alone is compiled for Java 8 and uses nothing newer: unless the
 * setup is turned off ({@code javacard.setup=false}), it checks that Maven runs on JDK 25, which the rest of the
 * plugin is compiled for, and hands over to the setup by name. On an older JDK it stops the build with that reason
 * instead of letting the project build without its setup.</p>
 */
@Named("javacard-express-setup")
@Singleton
public class JavaCardSetup extends AbstractMavenLifecycleParticipant {

    /** The JDK the plugin's classes and the toolkit need. */
    static final int REQUIRED_JDK = 25;

    private static final String SETUP = "name.velikodniy.jcexpress.plugin.ProjectsSetup";
    /** The plugin's key; {@code ProjectSetup.PLUGIN_KEY} (a Java 25 class) has the same. */
    private static final String PLUGIN_KEY = "name.velikodniy:javacard-express-maven-plugin";
    /** The switch of the setup; {@code BuildSettings} (a Java 25 class) reads the same parameter and property. */
    private static final String SETUP_PARAMETER = "setup";
    private static final String SETUP_PROPERTY = "javacard.setup";
    private static final String GOAL = "build";

    /**
     * Creates the extension; Maven does, when a project declares the plugin with {@code <extensions>true</extensions>}.
     */
    public JavaCardSetup() {
        super();
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        run(session, System.getProperty("java.specification.version"));
    }

    /**
     * Sets the projects up, unless the setup is turned off, on a JDK the plugin can run on.
     *
     * @param session              the session with the projects Maven has read
     * @param specificationVersion the running JDK's {@code java.specification.version}
     * @throws MavenExecutionException if Maven runs on a JDK older than {@value #REQUIRED_JDK}, or the setup fails
     */
    void run(MavenSession session, String specificationVersion) throws MavenExecutionException {
        if (setupOff(session)) {
            return;
        }
        requireJdk(feature(specificationVersion));
        start(session);
    }

    /**
     * Stops the build when Maven runs on a JDK older than the plugin needs.
     *
     * @param feature the feature release of the running JDK, e.g. 21
     * @throws MavenExecutionException if it is older than {@value #REQUIRED_JDK}
     */
    static void requireJdk(int feature) throws MavenExecutionException {
        if (feature < REQUIRED_JDK) {
            throw new MavenExecutionException("JavaCard Express needs JDK " + REQUIRED_JDK + " or newer, and Maven"
                    + " runs on " + (feature < 0 ? "a JDK of unknown version" : "JDK " + feature) + " ("
                    + System.getProperty("java.home") + "). Run Maven with JDK " + REQUIRED_JDK + " (JAVA_HOME, or"
                    + " the JDK of the IDE's Maven import); -D" + SETUP_PROPERTY + "=false turns the setup off.",
                    (File) null);
        }
    }

    /**
     * The feature release of a JDK from its {@code java.specification.version}: {@code 1.8} on JDK 8, the number
     * itself from JDK 9 on.
     *
     * @param specificationVersion the property's value
     * @return the feature release, or {@code -1} if the value is not a version
     */
    static int feature(String specificationVersion) {
        String version = specificationVersion == null ? "" : specificationVersion.trim();
        if (version.startsWith("1.")) {
            version = version.substring(2);
        }
        int dot = version.indexOf('.');
        try {
            return Integer.parseInt(dot < 0 ? version : version.substring(0, dot));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Whether the setup is off for every project that declares the plugin as a build extension, resolved as the
     * {@code build} goal resolves its {@code setup} parameter: configured in the POM, else the user, system and
     * project property {@value #SETUP_PROPERTY}. Without such a project, the user or system property decides.
     */
    static boolean setupOff(MavenSession session) {
        boolean declared = false;
        for (MavenProject project : session.getProjects()) {
            Plugin plugin = project.getPlugin(PLUGIN_KEY);
            if (plugin == null || !plugin.isExtensions()) {
                continue;
            }
            declared = true;
            if (!"false".equalsIgnoreCase(setting(session, project, plugin))) {
                return false;
            }
        }
        return declared || "false".equalsIgnoreCase(property(session, null));
    }

    private static String setting(MavenSession session, MavenProject project, Plugin plugin) {
        String configured = configured(plugin.getConfiguration());
        for (PluginExecution execution : plugin.getExecutions()) {
            if (configured == null && execution.getGoals().contains(GOAL)) {
                configured = configured(execution.getConfiguration());
            }
        }
        return configured != null ? configured : property(session, project);
    }

    private static String property(MavenSession session, MavenProject project) {
        String value = session.getUserProperties().getProperty(SETUP_PROPERTY);
        if (value == null) {
            value = session.getSystemProperties().getProperty(SETUP_PROPERTY);
        }
        if (value == null && project != null) {
            value = project.getProperties().getProperty(SETUP_PROPERTY);
        }
        return value == null ? null : value.trim();
    }

    private static String configured(Object configuration) {
        if (!(configuration instanceof Xpp3Dom)) {
            return null;
        }
        Xpp3Dom setup = ((Xpp3Dom) configuration).getChild(SETUP_PARAMETER);
        String value = setup == null ? null : setup.getValue();
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** Hands over to the setup, which is Java 25 code, by name. */
    private static void start(MavenSession session) throws MavenExecutionException {
        try {
            Class<?> type = Class.forName(SETUP, true, JavaCardSetup.class.getClassLoader());
            Object setup = type.getDeclaredConstructor().newInstance();
            Method run = type.getDeclaredMethod("afterProjectsRead", MavenSession.class);
            run.invoke(setup, session);
        } catch (InvocationTargetException e) {
            throw rethrown(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new MavenExecutionException("JavaCard Express cannot start its build extension: " + e, e);
        }
    }

    private static MavenExecutionException rethrown(Throwable cause) {
        if (cause instanceof MavenExecutionException) {
            return (MavenExecutionException) cause;
        }
        if (cause instanceof RuntimeException) {
            throw (RuntimeException) cause;
        }
        if (cause instanceof Error) {
            throw (Error) cause;
        }
        return new MavenExecutionException("JavaCard Express could not set the projects up: " + cause, cause);
    }
}
