package name.velikodniy.jcexpress.gp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps gp/README.md compilable: every line of every {@code ```java} block must be a line of
 * {@link ReadmeSnippets}, which the test compilation compiles against the current API.
 */
class ReadmeSnippetsTest {

    /** Surefire runs with the module directory as working directory. */
    private static final Path README = Path.of("README.md");
    private static final Path SNIPPETS = Path.of("src/test/java/name/velikodniy/jcexpress/gp/ReadmeSnippets.java");

    @Test
    void everyReadmeCodeLineIsCompiledInReadmeSnippets() throws IOException {
        Set<String> compiled = Files.readAllLines(SNIPPETS).stream().map(String::strip).collect(Collectors.toSet());

        List<String> readmeLines = javaBlockLines(Files.readAllLines(README));

        assertThat(readmeLines).hasSizeGreaterThan(60);
        assertThat(readmeLines).allSatisfy(line -> assertThat(compiled).as("README line in ReadmeSnippets")
                .contains(line));
    }

    private static List<String> javaBlockLines(List<String> readme) {
        List<String> lines = new ArrayList<>();
        boolean inJava = false;
        for (String line : readme) {
            if (line.startsWith("```")) {
                inJava = !inJava && line.strip().equals("```java");
            } else if (inJava && !line.isBlank()) {
                lines.add(line.strip());
            }
        }
        return lines;
    }
}
