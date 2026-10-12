package name.velikodniy.jcexpress.plugin;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Optional;

/**
 * The work of the build extension ({@link JavaCardSetup} is its entry point): after Maven has read the projects, and
 * before it plans the builds, each project that declares {@code javacard-express-maven-plugin} with
 * {@code <extensions>true</extensions>} gets what it has not configured itself ({@link ProjectSetup}): the binding of
 * the {@code build} goal, the toolkit's dependencies, the API stubs kept off the test class path, Java 8 for the
 * applet code and Java 25 for the tests, and the javac check of the Java Card subset with the settings of the
 * {@code build} goal. Configuration in the POM always wins; nothing configured is changed or removed.
 * {@code <setup>false</setup>} or {@code -Djavacard.setup=false} turns the setup off, {@code javacard.check=false}
 * only the javac check.
 */
final class ProjectsSetup {

    /**
     * Set on every project this extension has set up, for the {@code build} goal. (A name, not a class reference:
     * the entry point {@link JavaCardSetup} is compiled separately, for Java 8.)
     */
    static final String CONTEXT_KEY = "name.velikodniy.jcexpress.plugin.JavaCardSetup";

    /** The JDK the plugin needs; the entry point checks it ({@code JavaCardSetup.REQUIRED_JDK}). */
    static final int REQUIRED_JDK = 25;

    private static final Logger LOG = LoggerFactory.getLogger(ProjectsSetup.class);

    private final String version;

    /**
     * Creates the setup in the version of the plugin's own descriptor; {@link JavaCardSetup} does.
     */
    ProjectsSetup() {
        this(ownVersion().orElse(null));
    }

    /**
     * Creates the setup in a version.
     *
     * @param version the plugin version, or {@code null} to use the version each project declares
     */
    ProjectsSetup(String version) {
        this.version = version;
    }

    /**
     * Sets up every project of the session that declares the plugin as a build extension of this version.
     *
     * @param session the session with the projects Maven has read
     * @throws MavenExecutionException if the setup of a project fails; it names the project and the switch that
     *                                 turns the setup off
     */
    void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        for (MavenProject project : session.getProjects()) {
            Optional<Plugin> declared = declaration(project);
            if (declared.isPresent()) {
                try {
                    setUp(project, session, declared.get());
                } catch (RuntimeException e) {
                    throw new MavenExecutionException("JavaCard Express could not set up "
                            + project.getGroupId() + ":" + project.getArtifactId() + ": " + e
                            + ". -Djavacard.setup=false turns the setup off.", e);
                }
            }
        }
    }

    private void setUp(MavenProject project, MavenSession session, Plugin plugin) {
        if (!new BuildSettings(project, session, plugin).setup()) {
            LOG.debug("JavaCard Express: setup of {} turned off (javacard.setup=false)", project.getId());
            return;
        }
        String pluginVersion = version != null ? version : plugin.getVersion();
        ProjectSetup setup = new ProjectSetup(project, session, plugin, pluginVersion);
        List<String> done = setup.apply();
        project.setContextValue(CONTEXT_KEY, Boolean.TRUE);
        LOG.info("JavaCard Express {} set up {}: {} (javacard.setup=false turns the setup off)", pluginVersion,
                project.getGroupId() + ":" + project.getArtifactId(),
                done.isEmpty() ? "the project configures everything itself" : String.join("; ", done));
        setup.warnings().forEach(warning -> LOG.warn("JavaCard Express {}", warning));
    }

    /**
     * The plugin as this project declares it, when it declares it as a build extension of this version (a reactor
     * may hold projects that use another version, which its own extension sets up) and the project has classes to
     * build ({@code pom} packaging has none).
     */
    private Optional<Plugin> declaration(MavenProject project) {
        Plugin plugin = project.getPlugin(ProjectSetup.PLUGIN_KEY);
        if (plugin == null || !plugin.isExtensions() || "pom".equals(project.getPackaging())) {
            return Optional.empty();
        }
        return version == null || version.equals(plugin.getVersion()) ? Optional.of(plugin) : Optional.empty();
    }

    /**
     * The version in the plugin descriptor of the jar this class comes from.
     *
     * @return the version, empty when the descriptor cannot be read
     */
    static Optional<String> ownVersion() {
        URL location = ProjectsSetup.class.getProtectionDomain().getCodeSource().getLocation();
        String descriptor = location.toExternalForm().endsWith(".jar")
                ? "jar:" + location.toExternalForm() + "!/META-INF/maven/plugin.xml"
                : location.toExternalForm() + "META-INF/maven/plugin.xml";
        try (InputStream in = URI.create(descriptor).toURL().openStream()) {
            Element plugin = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in).getDocumentElement();
            for (Node child = plugin.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element element && element.getTagName().equals("version")) {
                    return Optional.of(element.getTextContent().strip());
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
