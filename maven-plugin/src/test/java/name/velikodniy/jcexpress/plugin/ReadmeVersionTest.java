package name.velikodniy.jcexpress.plugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The version is set in one place, {@code .mvn/maven.config} ({@code -Drevision=...}); the snippets of the
 * documentation show the release that the source tree becomes.
 *
 * <p>Between releases the version is the SNAPSHOT of the next release: with a release number there, a local
 * {@code mvn install} of the {@code main} branch would replace the published artifacts of that release in the local
 * repository. The commit a release is tagged from carries the release itself, and the next commit sets the next
 * SNAPSHOT. The release workflow passes the release with {@code -Drevision}. The documentation names the release, the
 * version without {@code -SNAPSHOT}, and never a SNAPSHOT of it, which is not published anywhere.</p>
 */
class ReadmeVersionTest {

    /** Surefire runs with the module directory as working directory. */
    private static final Path ROOT = Path.of("..");
    private static final Pattern REVISION = Pattern.compile("^-Drevision=(\\S+)$", Pattern.MULTILINE);
    private static final Pattern PROPERTY = Pattern.compile("<jcexpress\\.version>([^<]+)</jcexpress\\.version>");
    /** A dependency, plugin or parent ({@code javacard-express-applet-parent}) with a literal version. */
    private static final Pattern DEPENDENCY_VERSION = Pattern.compile(
            "<artifactId>javacard-express-[a-z-]+</artifactId>\\s*<version>([^<$]+)</version>");

    @Test
    void theVersionIsAReleaseOrTheSnapshotOfOneSetInMavenConfig() throws IOException {
        assertThat(developmentVersion()).matches("\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?");
        assertThat(Files.readString(ROOT.resolve("pom.xml"))).contains("<version>${revision}</version>")
                .doesNotContain("<revision>");
    }

    @Test
    void quickStartsShowTheReleaseVersion() throws IOException {
        for (String readme : List.of("README.md", "maven-plugin/README.md")) {
            String text = Files.readString(ROOT.resolve(readme));
            List<String> versions = new ArrayList<>(all(PROPERTY, text));
            versions.addAll(all(DEPENDENCY_VERSION, text));
            assertThat(versions).as(readme).isNotEmpty().containsOnly(releaseVersion());
        }
    }

    @Test
    void installationSnippetsShowTheReleaseVersion() throws IOException {
        for (String document : List.of("core/README.md", "gp/README.md", "sm/README.md", "pace/README.md",
                "container/README.md", "LIVE_CARD_TESTING.md")) {
            assertThat(all(DEPENDENCY_VERSION, Files.readString(ROOT.resolve(document)))).as(document)
                    .isNotEmpty().containsOnly(releaseVersion());
        }
    }

    @Test
    void noDocumentNamesTheSnapshotVersion() throws IOException {
        List<Path> documents;
        try (Stream<Path> files = Files.walk(ROOT, 2)) {
            documents = files.filter(file -> file.toString().endsWith(".md"))
                    .filter(file -> !file.startsWith(ROOT.resolve("specs")) && !file.startsWith(ROOT.resolve("docs")))
                    .toList();
        }

        assertThat(documents).isNotEmpty();
        for (Path document : documents) {
            assertThat(Files.readString(document)).as(document.toString())
                    .doesNotContain(releaseVersion() + "-SNAPSHOT");
        }
    }

    /** Tests run on JDK 25 with the toolkit (Java 25 class files); applets stay at Java 8. */
    @Test
    void quickStartCompilesTestsForJava25AndAppletsForJava8() throws IOException {
        String pom = ReadmeQuickStartTest.quickStartPom(Files.readString(ROOT.resolve("maven-plugin/README.md")));

        assertThat(pom).contains("<maven.compiler.release>8</maven.compiler.release>")
                .contains("<maven.compiler.testRelease>25</maven.compiler.testRelease>");
    }

    /**
     * Returns the version of the source tree.
     *
     * @return the {@code -Drevision} of {@code .mvn/maven.config}, e.g. {@code 1.2.0-SNAPSHOT} or {@code 1.2.0}
     * @throws IOException if the file cannot be read
     */
    static String developmentVersion() throws IOException {
        Matcher m = REVISION.matcher(Files.readString(ROOT.resolve(".mvn/maven.config")));
        assertThat(m.find()).as(".mvn/maven.config sets -Drevision").isTrue();
        return m.group(1);
    }

    /**
     * Returns the release the source tree becomes, which the documentation shows.
     *
     * @return the version of the source tree without {@code -SNAPSHOT}, e.g. {@code 1.2.0}
     * @throws IOException if the file cannot be read
     */
    static String releaseVersion() throws IOException {
        return developmentVersion().replace("-SNAPSHOT", "");
    }

    private static List<String> all(Pattern pattern, String text) {
        return pattern.matcher(text).results().map(r -> r.group(1)).toList();
    }
}
