package name.velikodniy.jcexpress;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The POM that consumers download (the flattened POM written by the build) must give a test that
 * depends only on javacard-express-core everything the README example uses: JUnit Jupiter
 * ({@code @Test}, {@code @ExtendWith}) and AssertJ ({@code JCXAssertions} extends AssertJ types).
 * Dependencies with scope {@code provided} or {@code test} are not transitive in Maven.
 */
class PublishedPomTest {

    private static final Path FLATTENED_POM = Path.of(".flattened-pom.xml");

    private static Map<String, String> dependencyScopes() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document pom = factory.newDocumentBuilder().parse(FLATTENED_POM.toFile());
        NodeList dependencies = pom.getElementsByTagName("dependency");
        Map<String, String> scopes = new HashMap<>();
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            String artifact = text(dependency, "groupId") + ":" + text(dependency, "artifactId");
            String scope = text(dependency, "scope");
            scopes.put(artifact, scope.isEmpty() ? "compile" : scope);
        }
        return scopes;
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    @Test
    void junitJupiterAndAssertjAreTransitiveForConsumers() throws Exception {
        assumeTrue(Files.isRegularFile(FLATTENED_POM), "flattened POM is produced by the Maven build");
        Map<String, String> scopes = dependencyScopes();
        assertThat(scopes)
                .containsEntry("org.junit.jupiter:junit-jupiter-api", "compile")
                .containsEntry("org.assertj:assertj-core", "compile")
                .containsEntry("com.klinec:jcardsim", "compile");
    }
}
