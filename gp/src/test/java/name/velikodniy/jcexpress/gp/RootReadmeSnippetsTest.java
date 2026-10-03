package name.velikodniy.jcexpress.gp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Keeps the root README.md honest. Every line of its {@code ```java} blocks must occur in a source file that this
 * module compiles ({@link RootReadmeSnippets} and the Quick Start applet and test in {@code com.example.hello},
 * which also runs as a test on jCardSim), and its {@code ```xml} block must be the POM of the applet parent's
 * integration test {@code applet-parent/src/it/parent-quickstart}, which builds it verbatim.
 */
class RootReadmeSnippetsTest {

    /** Surefire runs with the module directory as working directory. */
    private static final Path README = Path.of("../README.md");
    private static final Pattern REVISION = Pattern.compile("^-Drevision=(\\S+)$", Pattern.MULTILINE);
    private static final Path PARENT_IT_POM = Path.of("../applet-parent/src/it/parent-quickstart/pom.xml");
    private static final List<Path> MIRRORS = List.of(
            Path.of("src/test/java/name/velikodniy/jcexpress/gp/RootReadmeSnippets.java"),
            Path.of("src/test/java/com/example/hello/HelloWorldApplet.java"),
            Path.of("src/test/java/com/example/hello/HelloWorldAppletTest.java"));

    @Test
    void everyJavaLineOfTheReadmeIsCompiledHere() throws IOException {
        Set<String> compiled = new HashSet<>();
        for (Path mirror : MIRRORS) {
            Files.readAllLines(mirror).stream().map(String::strip).forEach(compiled::add);
        }

        List<String> readmeLines = blockLines(Files.readAllLines(README), "java");

        assertThat(readmeLines).hasSizeGreaterThan(60);
        assertThat(readmeLines).allSatisfy(line -> assertThat(compiled).as("README line compiled in gp tests")
                .contains(line));
    }

    @Test
    void theReadmePomIsTheOneTheParentIntegrationTestBuilds() throws IOException {
        String release = releaseVersion();
        List<String> itPom = Files.readAllLines(PARENT_IT_POM).stream().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("<?xml"))
                .map(line -> line.replace("@project.version@", release)).toList();

        List<String> pomLines = blockLines(Files.readAllLines(README), "xml");

        assertThat(pomLines).contains("<artifactId>javacard-express-applet-parent</artifactId>",
                "<javacard.packageAid>A00000006212</javacard.packageAid>");
        assertThat(pomLines).isEqualTo(itPom);
    }

    /** The release the source tree becomes: the -Drevision of .mvn/maven.config without -SNAPSHOT. */
    private static String releaseVersion() throws IOException {
        Matcher m = REVISION.matcher(Files.readString(Path.of("../.mvn/maven.config")));
        assertThat(m.find()).as(".mvn/maven.config sets -Drevision").isTrue();
        return m.group(1).replace("-SNAPSHOT", "");
    }

    @Test
    void theToolkitExampleRunsOnJCardSim() {
        assertThatCode(RootReadmeSnippets::toolkitWithoutJUnit).doesNotThrowAnyException();
    }

    private static List<String> blockLines(List<String> readme, String language) {
        List<String> lines = new ArrayList<>();
        boolean inBlock = false;
        for (String line : readme) {
            if (line.startsWith("```")) {
                inBlock = !inBlock && line.strip().equals("```" + language);
            } else if (inBlock && !line.isBlank()) {
                lines.add(line.strip());
            }
        }
        return lines;
    }
}
