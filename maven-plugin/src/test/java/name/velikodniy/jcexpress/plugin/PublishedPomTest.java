package name.velikodniy.jcexpress.plugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BOM and the applet parent are published without the root POM (their flattened POMs have no parent from this
 * build): the version comes from {@code -Drevision} ({@code .mvn/maven.config} or the release workflow), values they
 * repeat from the root POM must stay equal to the originals, and the BOM must manage every artifact this build
 * publishes.
 */
class PublishedPomTest {

    /** Surefire runs with the module directory as working directory. */
    private static final Path ROOT = Path.of("..");
    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");
    private static final Pattern ARTIFACT_ID = Pattern.compile("</parent>\\s*(?:<groupId>[^<]+</groupId>\\s*)?"
            + "<artifactId>([^<]+)</artifactId>");
    /** Modules whose artifact is not managed by the BOM: the BOM itself and the parent that inherits it. */
    private static final List<String> NOT_MANAGED = List.of("bom", "applet-parent");

    @Test
    void noPomOfTheBuildRepeatsTheVersion() throws IOException {
        // flatten-maven-plugin resolves ${revision} of the published POMs from -Drevision, so neither the root POM
        // nor the BOM needs a copy of the version as a property
        String development = ReadmeVersionTest.developmentVersion();
        List<String> poms = new ArrayList<>(List.of("pom.xml"));
        MODULE.matcher(read("pom.xml")).results().map(r -> r.group(1) + "/pom.xml").forEach(poms::add);

        assertThat(poms).hasSizeGreaterThan(5).allSatisfy(pom -> assertThat(read(pom)).as(pom)
                .doesNotContain("<revision>").doesNotContain(development));
    }

    @Test
    void theAppletParentUsesTheJUnitVersionTheToolkitIsBuiltWith() throws IOException {
        Matcher params = Pattern.compile("<artifactId>junit-jupiter-params</artifactId>\\s*<version>([^<]+)</version>")
                .matcher(read("applet-parent/pom.xml"));

        assertThat(params.find()).as("applet-parent declares junit-jupiter-params with a version").isTrue();
        assertThat(params.group(1)).isEqualTo(property("pom.xml", "junit.version"));
    }

    @Test
    void theBomStatesItsVersionAsTheRootPomDoes() throws IOException {
        // javacard-express-applet-parent names the BOM as its parent with the text ${revision}; IntelliJ IDEA matches
        // a parent found through relativePath by these coordinates and reported the parent as non-resolvable while
        // the BOM inherited its version
        Matcher version = Pattern.compile("</artifactId>\\s*<!--.*?-->\\s*<version>([^<]+)</version>", Pattern.DOTALL)
                .matcher(read("bom/pom.xml"));

        assertThat(version.find()).as("bom/pom.xml states <version>").isTrue();
        assertThat(version.group(1)).isEqualTo("${revision}");
    }

    @Test
    void theAppletParentManagesThePluginWithoutUsingIt() throws IOException {
        // A project names the plugin (groupId, artifactId); the parent manages its executions (the version comes from
        // the BOM). No module of this build declares the plugin, so neither Maven (validate, compile, test of the
        // whole tree) nor an IDE (IntelliJ IDEA resolves plugins only from repositories) needs it before it is
        // installed
        String parent = read("applet-parent/pom.xml");
        String management = parent.substring(parent.indexOf("<pluginManagement>"),
                parent.indexOf("</pluginManagement>"));
        String rest = parent.replace(management, "").substring(0, parent.replace(management, "").indexOf("<profiles>"));

        assertThat(management).containsPattern("<artifactId>javacard-express-maven-plugin</artifactId>\\s*<executions>"
                + "\\s*<execution>\\s*<goals>\\s*<goal>build</goal>");
        assertThat(rest).doesNotContain("<artifactId>javacard-express-maven-plugin</artifactId>");
        assertThat(parent).doesNotContain("<phase>none</phase>").contains("<profiles>remove</profiles>");
    }

    @Test
    void theBomManagesEveryPublishedArtifact() throws IOException {
        String bom = read("bom/pom.xml");
        int dependencies = bom.indexOf("<dependencyManagement>");
        int plugins = bom.indexOf("<pluginManagement>");
        List<String> modules = MODULE.matcher(read("pom.xml")).results().map(r -> r.group(1))
                .filter(module -> !NOT_MANAGED.contains(module)).toList();

        assertThat(modules).isNotEmpty();
        for (String module : modules) {
            String artifactId = artifactId(module);
            Matcher managed = Pattern.compile("<artifactId>" + Pattern.quote(artifactId)
                    + "</artifactId>\\s*<version>\\$\\{revision}</version>").matcher(bom);
            assertThat(managed.find()).as("the BOM manages %s with ${revision}", artifactId).isTrue();
            int at = managed.start();
            boolean plugin = "maven-plugin".equals(module);
            assertThat(at > plugins).as("%s is managed as a %s", artifactId, plugin ? "plugin" : "dependency")
                    .isEqualTo(plugin && plugins > dependencies);
        }
    }

    private static String artifactId(String module) throws IOException {
        Matcher m = ARTIFACT_ID.matcher(read(module + "/pom.xml"));
        assertThat(m.find()).as("%s/pom.xml names its artifactId after its parent", module).isTrue();
        return m.group(1);
    }

    private static String property(String pom, String name) throws IOException {
        Matcher m = Pattern.compile("<" + Pattern.quote(name) + ">([^<]+)</" + Pattern.quote(name) + ">")
                .matcher(read(pom));
        assertThat(m.find()).as("%s defines <%s>", pom, name).isTrue();
        return m.group(1);
    }

    private static String read(String path) {
        try {
            return Files.readString(ROOT.resolve(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
