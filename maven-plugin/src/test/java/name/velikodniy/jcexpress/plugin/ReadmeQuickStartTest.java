package name.velikodniy.jcexpress.plugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the README Quick Start honest: the integration test {@code src/it/readme-quickstart}
 * builds exactly the {@code pom.xml}, applet and test shown in the README (only the plugin version
 * differs, it is the version under test), so a snippet that does not produce a CAP file or whose
 * jCardSim test does not pass fails the build; {@code src/it/readme-explicit-pom} builds the POM of
 * the section for tools that do not run build extensions, with the same sources, and
 * {@code src/it/readme-explicit-pom-check} the same POM with an applet outside the Java Card subset, which its
 * javac check must reject.
 */
class ReadmeQuickStartTest {

    private static final Path README = Path.of("README.md");
    private static final Path IT_PROJECT = Path.of("src/it/readme-quickstart");
    private static final Path IT_POM = IT_PROJECT.resolve("pom.xml");
    private static final Path EXPLICIT_PROJECT = Path.of("src/it/readme-explicit-pom");
    private static final Path EXPLICIT_CHECK_PROJECT = Path.of("src/it/readme-explicit-pom-check");
    private static final String EXPLICIT_SECTION = "### Without the build extension";
    private static final Pattern VERSION_PROPERTY =
            Pattern.compile("<jcexpress\\.version>[^<]*</jcexpress\\.version>");

    @Test
    void integrationTestBuildsTheReadmeQuickStartPomVerbatim() throws IOException {
        String readmePom = normalize(quickStartPom(Files.readString(README)));
        String itPom = normalize(Files.readString(IT_POM).replace("@project.version@",
                ReadmeVersionTest.releaseVersion()));

        assertThat(itPom).isEqualTo(readmePom);
    }

    @Test
    void quickStartDeclaresOnlyThePluginAsABuildExtension() throws IOException {
        // the extension binds the goal and adds the dependencies; the POM has nothing else to say
        String pom = quickStartPom(Files.readString(README));

        assertThat(pom).contains("<extensions>true</extensions>")
                .doesNotContain("<executions>", "<dependencies>", "<properties>");
    }

    @Test
    void integrationTestBuildsTheExplicitPomOfTheReadmeVerbatim() throws IOException {
        String readmePom = normalize(explicitPom(Files.readString(README)));
        String itPom = normalize(Files.readString(EXPLICIT_PROJECT.resolve("pom.xml")));

        assertThat(itPom).isEqualTo(readmePom);
    }

    @Test
    void integrationTestChecksAnAppletWithTheExplicitPomOfTheReadmeVerbatim() throws IOException {
        String readmePom = normalize(explicitPom(Files.readString(README)));
        String itPom = normalize(Files.readString(EXPLICIT_CHECK_PROJECT.resolve("pom.xml")));

        assertThat(itPom).isEqualTo(readmePom);
    }

    @Test
    void theExplicitPomWritesOutWhatTheExtensionAdds() throws IOException {
        // the same project as the Quick Start, for tools that do not run build extensions
        String pom = explicitPom(Files.readString(README));

        assertThat(pom).contains("<artifactId>javacard-express-core</artifactId>",
                "<artifactId>javacard-express-livecard</artifactId>", "<artifactId>javacard-express-api</artifactId>",
                "<maven.compiler.release>" + CompilerSetup.APPLET_RELEASE + "</maven.compiler.release>",
                "<maven.compiler.testRelease>" + CompilerSetup.TEST_RELEASE + "</maven.compiler.testRelease>",
                "<arg>" + CompilerSetup.NO_OPTIONS_WARNING + "</arg>",
                "<artifactId>" + CompilerSetup.CHECK_ARTIFACT + "</artifactId>",
                "<classpathDependencyExclude>" + ProjectSetup.STUBS + "</classpathDependencyExclude>");
        assertThat(pom.indexOf("<id>default-compile</id>")).as("the check is in the main compilation only")
                .isNotNegative().isLessThan(pom.indexOf(CompilerSetup.CHECK_ARTIFACT));
    }

    @Test
    void theExplicitPomBindsTheBuildGoal() throws IOException {
        // A defaultPhase only applies to a declared execution (no lifecycle mapping is shipped).
        String pom = explicitPom(Files.readString(README));

        assertThat(pom).contains("<goal>build</goal>").contains("<executions>").doesNotContain("<extensions>");
    }

    @Test
    void integrationTestCompilesAndTestsTheReadmeSourcesVerbatim() throws IOException {
        List<String> sources = quickStartJavaSources(Files.readString(README));

        assertThat(sources).as("README Quick Start shows the applet and its test").hasSize(2);
        for (Path project : List.of(IT_PROJECT, EXPLICIT_PROJECT)) {
            assertThat(normalize(Files.readString(project.resolve(
                    "src/main/java/com/example/hello/HelloWorldApplet.java")))).isEqualTo(normalize(sources.get(0)));
            assertThat(normalize(Files.readString(project.resolve(
                    "src/test/java/com/example/hello/HelloWorldAppletTest.java")))).isEqualTo(normalize(sources.get(1)));
        }
    }

    @Test
    void theExplicitPomKeepsTheApiStubsBehindJCardSim() throws IOException {
        // The stubs and jCardSim define the same javacard.* classes; the stubs must never be what
        // the simulator executes (jCardSim then fails with 'Internal reflection error').
        String pom = explicitPom(Files.readString(README));

        assertThat(pom.indexOf("<artifactId>javacard-express-core</artifactId>"))
                .as("javacard-express-core is declared before the API stubs").isNotNegative()
                .isLessThan(pom.indexOf("<artifactId>javacard-express-api</artifactId>"));
        assertThat(pom).contains("<classpathDependencyExclude>name.velikodniy:javacard-express-api"
                + "</classpathDependencyExclude>");
    }

    @Test
    void theParentSectionShowsThePomTheParentIntegrationTestBuilds() throws IOException {
        String readme = Files.readString(README);
        int section = readme.indexOf("### The shortest POM: the applet parent");
        assertThat(section).as("README shows the applet parent").isNotNegative();
        Matcher block = Pattern.compile("```xml\\n(<project.*?</project>)\\n```", Pattern.DOTALL).matcher(readme);
        assertThat(block.find(section)).as("the section shows a complete pom.xml").isTrue();
        String itPom = Files.readString(Path.of("../applet-parent/src/it/parent-quickstart/pom.xml"));
        itPom = itPom.substring(itPom.indexOf("<project")).replace("@project.version@",
                ReadmeVersionTest.releaseVersion());

        assertThat(normalize(block.group(1))).isEqualTo(normalize(itPom));
        assertThat(block.group(1)).doesNotContain("<executions>", "<dependencies>", "maven-surefire-plugin");
    }

    /** Returns the first {@code xml} code block of the Quick Start section that is a whole POM. */
    static String quickStartPom(String readme) {
        int section = readme.indexOf("## Quick Start");
        assertThat(section).as("README has a Quick Start section").isNotNegative();
        Matcher block = Pattern.compile("```xml\\n(<project.*?</project>)\\n```", Pattern.DOTALL)
                .matcher(readme);
        assertThat(block.find(section)).as("Quick Start shows a complete pom.xml").isTrue();
        return block.group(1);
    }

    /** Returns the whole POM of the section for tools that do not run build extensions. */
    static String explicitPom(String readme) {
        int section = readme.indexOf(EXPLICIT_SECTION);
        assertThat(section).as("README has the section %s", EXPLICIT_SECTION).isNotNegative();
        Matcher block = Pattern.compile("```xml\\n(<project.*?</project>)\\n```", Pattern.DOTALL).matcher(readme);
        assertThat(block.find(section)).as("the section shows a complete pom.xml").isTrue();
        return block.group(1);
    }

    /** Returns the {@code java} code blocks of the Quick Start section, in README order. */
    static List<String> quickStartJavaSources(String readme) {
        int section = readme.indexOf("## Quick Start");
        int end = readme.indexOf("\n## ", section + 1);
        Matcher block = Pattern.compile("```java\\n(.*?)\\n```", Pattern.DOTALL)
                .matcher(readme.substring(section, end < 0 ? readme.length() : end));
        List<String> sources = new ArrayList<>();
        while (block.find()) {
            sources.add(block.group(1));
        }
        return sources;
    }

    private static String normalize(String pom) {
        String withoutVersion = VERSION_PROPERTY.matcher(pom.strip())
                .replaceAll("<jcexpress.version>VERSION</jcexpress.version>");
        return withoutVersion.replace("\r\n", "\n").lines().map(String::stripTrailing)
                .reduce((a, b) -> a + "\n" + b).orElse("");
    }
}
