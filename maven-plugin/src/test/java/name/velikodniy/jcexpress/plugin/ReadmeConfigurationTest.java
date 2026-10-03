package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.plugin.testing.PluginDescriptor;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every configuration element and user property documented in the README must exist in the
 * generated plugin descriptor; Maven silently ignores unknown elements (it only prints a warning),
 * which is how a documented-but-missing parameter goes unnoticed.
 */
class ReadmeConfigurationTest {

    private static final Path README = Path.of("README.md");
    private final PluginDescriptor descriptor = PluginDescriptor.buildGoal();

    @Test
    void everyDocumentedConfigurationElementIsAPluginParameter() throws Exception {
        List<String> documented = configurationElements(section(Files.readString(README), "## Configuration"));

        assertThat(documented).isNotEmpty();
        assertThat(documented).allSatisfy(name -> assertThat(descriptor.parameter(name))
                .as("README documents <%s>, but the build goal has no such parameter", name)
                .hasValueSatisfying(p -> assertThat(p.readonly()).as("<%s> is read-only", name).isFalse()));
    }

    @Test
    void everyDocumentedUserPropertyIsBoundToAParameter() throws Exception {
        Matcher m = Pattern.compile("\\| `(javacard\\.[A-Za-z0-9.]+)` \\|").matcher(Files.readString(README));
        List<String> documented = new ArrayList<>();
        while (m.find()) {
            documented.add(m.group(1));
        }
        List<String> declared = descriptor.parameters().values().stream()
                .flatMap(p -> p.property().stream()).toList();

        assertThat(documented).isNotEmpty();
        assertThat(declared).containsAll(documented);
    }

    private static String section(String readme, String heading) {
        int start = readme.indexOf(heading);
        assertThat(start).as("README section %s", heading).isNotNegative();
        int end = readme.indexOf("\n## ", start + heading.length());
        return end < 0 ? readme.substring(start) : readme.substring(start, end);
    }

    private static List<String> configurationElements(String section) throws Exception {
        Matcher block = Pattern.compile("```xml\\n(<configuration>.*?</configuration>)\\n```", Pattern.DOTALL)
                .matcher(section);
        assertThat(block.find()).as("Configuration section shows a <configuration> block").isTrue();
        Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(block.group(1).getBytes(StandardCharsets.UTF_8)))
                .getDocumentElement();
        List<String> names = new ArrayList<>();
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                names.add(e.getTagName());
            }
        }
        return names;
    }
}
