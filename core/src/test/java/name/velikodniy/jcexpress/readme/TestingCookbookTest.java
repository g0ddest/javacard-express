package name.velikodniy.jcexpress.readme;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps TESTING.md (the testing cookbook) honest: every {@code ```java} block must be a contiguous run of lines of ONE
 * source file below the package {@code name.velikodniy.jcexpress.readme.cookbook}, whose classes run as tests of this
 * module (the recipes on the sample {@code WalletApplet}). Blank lines and lines that are only a comment do not count;
 * the imports a block shows must be imports of that file (which may import more). A block of statements must start a
 * method body there, so that a recipe cannot depend on a line the page does not show. A snippet that no longer
 * compiles, or whose assertion no longer holds, fails the build in the recipe classes; a snippet edited only in the
 * document fails here.
 */
class TestingCookbookTest {

    /** Surefire runs with the module directory as working directory. */
    private static final Path COOKBOOK = Path.of("../TESTING.md");
    private static final Path MIRRORS = Path.of("src/test/java/name/velikodniy/jcexpress/readme/cookbook");
    /** First lines of declarations: a block starting with one of them is part of a type, not of a method body. */
    private static final Pattern DECLARATION = Pattern.compile(
            "^(@|import |package |public |protected |private |static |final |abstract |class |interface |enum |record "
                    + "|void |@interface ).*");

    @Test
    void everyJavaBlockOfTheCookbookIsARunOfOneRecipeClass() throws IOException {
        List<Source> sources = sources();
        List<List<String>> blocks = javaBlocks(Files.readAllLines(COOKBOOK));

        assertThat(blocks).hasSizeGreaterThan(12);
        assertThat(blocks).allSatisfy(block -> assertThat(sources.stream().anyMatch(source -> source.contains(block)))
                .as("TESTING.md block starting with '%s' is a contiguous run of one file in %s%s", block.getFirst(),
                        MIRRORS, statementHint(block))
                .isTrue());
    }

    @Test
    void aBlockThatLeavesOutALineOfItsMethodIsNotFound() {
        Source source = new Source(List.of("void recipe(SmartCardSession card) {", "card.send(CREDIT);",
                "assertThat(card.send(GET_BALANCE)).isSuccess();", "}"), Set.of());

        assertThat(source.contains(List.of("assertThat(card.send(GET_BALANCE)).isSuccess();"))).isFalse();
        assertThat(source.contains(List.of("card.send(CREDIT);", "assertThat(card.send(GET_BALANCE)).isSuccess();")))
                .isTrue();
        assertThat(source.contains(List.of("@Test", "card.send(CREDIT);"))).isFalse();
    }

    private static String statementHint(List<String> block) {
        return isDeclaration(block.getFirst()) ? "" : " (a block of statements must start a method body there)";
    }

    private static List<Source> sources() throws IOException {
        List<Source> sources = new ArrayList<>();
        try (Stream<Path> files = Files.walk(MIRRORS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                List<String> lines = normalized(Files.readAllLines(file));
                List<String> body = lines.stream().filter(line -> !isImport(line)).toList();
                Set<String> imports = new HashSet<>(lines.stream().filter(TestingCookbookTest::isImport).toList());
                sources.add(new Source(body, imports));
            }
        }
        return sources;
    }

    private static List<List<String>> javaBlocks(List<String> document) {
        List<List<String>> blocks = new ArrayList<>();
        List<String> current = null;
        for (String line : document) {
            if (line.startsWith("```")) {
                if (current != null) {
                    blocks.add(normalized(current));
                    current = null;
                } else if (line.strip().equals("```java")) {
                    current = new ArrayList<>();
                }
            } else if (current != null) {
                current.add(line);
            }
        }
        return blocks;
    }

    /** Stripped lines without blank lines and lines that are only a comment. */
    private static List<String> normalized(List<String> lines) {
        return lines.stream().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("//") && !line.startsWith("/*")
                        && !line.startsWith("*"))
                .toList();
    }

    private static boolean isImport(String line) {
        return line.startsWith("import ");
    }

    private static boolean isDeclaration(String line) {
        return DECLARATION.matcher(line).matches();
    }

    /** A recipe file: its lines without imports, and its imports. */
    private record Source(List<String> body, Set<String> imports) {

        boolean contains(List<String> block) {
            List<String> blockImports = block.stream().filter(TestingCookbookTest::isImport).toList();
            List<String> blockBody = block.stream().filter(line -> !isImport(line)).toList();
            if (!imports.containsAll(blockImports)) {
                return false;
            }
            if (blockBody.isEmpty()) {
                return true;
            }
            int from = 0;
            while (true) {
                int at = indexOf(blockBody, from);
                if (at < 0) {
                    return false;
                }
                if (isDeclaration(blockBody.getFirst()) || (at > 0 && body.get(at - 1).endsWith("{"))) {
                    return true;
                }
                from = at + 1;
            }
        }

        private int indexOf(List<String> run, int from) {
            if (from >= body.size()) {
                return -1;
            }
            int at = Collections.indexOfSubList(body.subList(from, body.size()), run);
            return at < 0 ? -1 : from + at;
        }
    }
}
