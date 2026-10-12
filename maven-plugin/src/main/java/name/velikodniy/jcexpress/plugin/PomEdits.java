package name.velikodniy.jcexpress.plugin;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Small edits of a project's model for the build extension. Every edit adds; nothing that is configured is changed or
 * removed.
 */
final class PomEdits {

    private PomEdits() {
    }

    /**
     * Returns the configuration of a plugin, creating an empty one.
     *
     * @param plugin the plugin
     * @return its configuration
     */
    static Xpp3Dom configuration(Plugin plugin) {
        if (plugin.getConfiguration() == null) {
            plugin.setConfiguration(new Xpp3Dom("configuration"));
        }
        return (Xpp3Dom) plugin.getConfiguration();
    }

    /**
     * Returns the configuration of an execution, creating an empty one.
     *
     * @param execution the execution
     * @return its configuration
     */
    static Xpp3Dom configuration(PluginExecution execution) {
        if (execution.getConfiguration() == null) {
            execution.setConfiguration(new Xpp3Dom("configuration"));
        }
        return (Xpp3Dom) execution.getConfiguration();
    }

    /**
     * Returns a child element, appending an empty one when there is none.
     *
     * @param parent the element
     * @param name   the child's name
     * @return the child
     */
    static Xpp3Dom child(Xpp3Dom parent, String name) {
        Xpp3Dom child = parent.getChild(name);
        if (child == null) {
            child = new Xpp3Dom(name);
            parent.addChild(child);
        }
        return child;
    }

    /**
     * Appends an item with a text value to a list element, unless an item has that value.
     *
     * @param list the list element, e.g. {@code <compilerArgs>}
     * @param item the name of a new item, e.g. {@code arg}
     * @param value the value
     * @return {@code true} if it was appended
     */
    static boolean append(Xpp3Dom list, String item, String value) {
        if (Arrays.stream(list.getChildren()).anyMatch(c -> value.equals(text(c)))) {
            return false;
        }
        list.addChild(element(item, value));
        return true;
    }

    /**
     * Creates an element with a text value.
     *
     * @param name  its name
     * @param value its text
     * @return the element
     */
    static Xpp3Dom element(String name, String value) {
        Xpp3Dom element = new Xpp3Dom(name);
        element.setValue(value);
        return element;
    }

    /**
     * Returns the stripped text of an element.
     *
     * @param element the element, or {@code null}
     * @return its text, or {@code null} for none or blank text
     */
    static String text(Xpp3Dom element) {
        String value = element == null ? null : element.getValue();
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * Whether one of the configurations sets one of the parameters.
     *
     * @param configurations configurations, {@code null} entries allowed
     * @param parameters     parameter names
     * @return {@code true} if one has a non-blank value
     */
    static boolean configures(List<Xpp3Dom> configurations, String... parameters) {
        return configurations.stream().filter(Objects::nonNull)
                .anyMatch(c -> Stream.of(parameters).anyMatch(p -> text(c.getChild(p)) != null));
    }

    /**
     * Returns a property as Maven resolves it for a plugin parameter: from the command line, the system properties
     * or the project.
     *
     * @param project  the project
     * @param session  the session
     * @param property the property name
     * @return its stripped value, or {@code null} when it is not set or blank
     */
    static String property(MavenProject project, MavenSession session, String property) {
        String value = session.getUserProperties().getProperty(property);
        value = value != null ? value : session.getSystemProperties().getProperty(property);
        value = value != null ? value : project.getProperties().getProperty(property);
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * Whether one of the properties is set on the command line, in the system properties or in the project.
     *
     * @param project    the project
     * @param session    the session
     * @param properties property names
     * @return {@code true} if one is set
     */
    static boolean hasProperty(MavenProject project, MavenSession session, String... properties) {
        return Stream.of(properties).anyMatch(p -> session.getUserProperties().getProperty(p) != null
                || session.getSystemProperties().getProperty(p) != null
                || project.getProperties().getProperty(p) != null);
    }
}
