package name.velikodniy.jcexpress.converter;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps converter/README.md compilable: every line of every {@code ```java} block must be a line of
 * {@link ReadmeSnippets}, which the test compilation compiles against the current API.
 */
class ReadmeSnippetsTest {

    /** Surefire runs with the module directory as working directory. */
    private static final Path README = Path.of("README.md");
    private static final Path SNIPPETS =
            Path.of("src/test/java/name/velikodniy/jcexpress/converter/ReadmeSnippets.java");

    @Test
    void everyReadmeCodeLineIsCompiledInReadmeSnippets() throws IOException {
        Set<String> compiled = Files.readAllLines(SNIPPETS).stream().map(String::strip).collect(Collectors.toSet());

        List<String> readmeLines = javaBlockLines(Files.readAllLines(README));

        assertThat(readmeLines).hasSizeGreaterThan(15);
        assertThat(readmeLines).allSatisfy(line -> assertThat(compiled).as("README line in ReadmeSnippets")
                .contains(line));
    }

    /**
     * The README's rejection snippet must show every rejection: errors without violations (an invalid AID here,
     * JCVM 3.1 §4.2.2: the applet AID must differ from the package AID) are reported only in the message.
     */
    @Test
    void rejectionSnippetPrintsErrorsWithoutViolations() {
        Converter invalidAid = Converter.builder().classesDirectory(TestFixtures.CLASSES_DIR)
                .packageName("com.example").packageAid("A000000062010101")
                .applet("com.example.TestApplet", "A000000062010101").build();

        String printed = stderrOf(() -> ReadmeSnippets.rejected(invalidAid));

        assertThat(printed).contains("§4.2.2");
    }

    @Test
    void rejectionSnippetPrintsEveryViolation() {
        Converter rejected = TestFixtures.applet("BlankFinalApplet").builder(JavaCardVersion.V3_0_5).build();

        String printed = stderrOf(() -> ReadmeSnippets.rejected(rejected));

        assertThat(printed).contains("com/example/blankfinal/BlankFinalApplet").contains("BlankFinalApplet.java:");
    }

    private static String stderrOf(Runnable action) {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setErr(original);
        }
        return captured.toString(StandardCharsets.UTF_8);
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
