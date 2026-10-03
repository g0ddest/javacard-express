package name.velikodniy.jcexpress.livecard;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The root of the project this module is built in, for the build checks that read the whole project
 * ({@link NoLiveCardTestsInCiTest}, {@link PcscAccessGuardTest}).
 *
 * <p>Maven passes the root as the system property {@code maven.multiModuleProjectDirectory} (the Surefire
 * configuration of this module forwards it); without it (an IDE run in the module directory) the parent
 * directory is used.</p>
 */
final class ProjectRoot {

    private ProjectRoot() {
    }

    /**
     * Finds the project root.
     *
     * @return the absolute project root
     * @throws IllegalStateException if the directory found is not this project's root
     */
    static Path find() {
        String configured = System.getProperty("maven.multiModuleProjectDirectory");
        Path root = (configured != null ? Path.of(configured) : Path.of("..")).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("livecard").resolve("pom.xml"))) {
            throw new IllegalStateException("not the javacard-express project root (no livecard/pom.xml): " + root);
        }
        return root;
    }

    /**
     * Returns the modules of the build: the {@code <modules>} of the root POM, in their order.
     *
     * @param root the project root
     * @return the module directory names, e.g. {@code core}
     * @throws Exception if the root POM cannot be read
     */
    static List<String> modules(Path root) throws Exception {
        List<String> modules = new ArrayList<>();
        for (Element list : children(parse(root.resolve("pom.xml")).getDocumentElement(), "modules")) {
            for (Element module : children(list, "module")) {
                modules.add(module.getTextContent().strip());
            }
        }
        return modules;
    }

    /**
     * Parses a POM without resolving external entities or a DOCTYPE.
     *
     * @param pom the file
     * @return the document
     * @throws Exception if the file cannot be read or parsed
     */
    static Document parse(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(pom.toFile());
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getTagName().equals(tag)) {
                result.add(element);
            }
        }
        return result;
    }
}
