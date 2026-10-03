package name.velikodniy.jcexpress.livecard.thirdparty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Jpp}: the directives of PivApplet's sources, with dropped and directive lines kept as empty lines.
 */
class JppTest {

    private static final List<String> IF_ELSE = List.of("a", "//#if X", "b", "//#else", "c", "//#endif", "d");

    @Test
    void definedNameKeepsTheIfBranch() {
        assertThat(Jpp.process(IF_ELSE, Set.of("X"), "T.java")).containsExactly("a", "", "b", "", "", "", "d");
    }

    @Test
    void undefinedNameKeepsTheElseBranch() {
        assertThat(Jpp.process(IF_ELSE, Set.of(), "T.java")).containsExactly("a", "", "", "", "c", "", "d");
    }

    @Test
    void negationAndIndentedDirectives() {
        List<String> lines = List.of("    //#if !X", "  kept", "    //#endif");

        assertThat(Jpp.process(lines, Set.of(), "T.java")).containsExactly("", "  kept", "");
        assertThat(Jpp.process(lines, Set.of("X"), "T.java")).containsExactly("", "", "");
    }

    /** Block-comment forms keep the unprocessed sources compilable; the markers disappear with the directives. */
    @Test
    void blockCommentDirectives() {
        List<String> lines = List.of("/*#if X", "code();", "#else*/", "other();", "/*#endif*/");

        assertThat(Jpp.process(lines, Set.of("X"), "T.java")).containsExactly("", "code();", "", "", "");
        assertThat(Jpp.process(lines, Set.of(), "T.java")).containsExactly("", "", "", "other();", "");
    }

    @Test
    void anInnerBlockOfADroppedBlockIsDropped() {
        List<String> lines = List.of("//#if X", "//#if Y", "inner", "//#endif", "//#endif");

        assertThat(Jpp.process(lines, Set.of("Y"), "T.java")).containsOnly("");
        assertThat(Jpp.process(lines, Set.of("X", "Y"), "T.java")).containsExactly("", "", "inner", "", "");
    }

    @Test
    void unbalancedDirectivesAreErrors() {
        assertThatThrownBy(() -> Jpp.process(List.of("//#if X", "a"), Set.of(), "T.java"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("T.java: 1 #if without #endif");
        assertThatThrownBy(() -> Jpp.process(List.of("a", "//#endif"), Set.of(), "T.java"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("T.java:2: #endif without #if");
        assertThatThrownBy(() -> Jpp.process(List.of("//#else"), Set.of(), "T.java"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("#else without #if");
    }

    @Test
    void treesKeepTheirLayoutAndLineNumbers(@TempDir Path tmp) throws IOException {
        Path source = Files.createDirectories(tmp.resolve("src/p"));
        Files.writeString(source.resolve("A.java"), String.join("\n", IF_ELSE) + "\n");
        Files.writeString(source.resolve("notes.txt"), "//#if X\n");

        Jpp.processTree(tmp.resolve("src"), tmp.resolve("out"), Set.of("X"));

        assertThat(Files.readAllLines(tmp.resolve("out/p/A.java"))).hasSize(IF_ELSE.size()).contains("b")
                .doesNotContain("c");
        assertThat(tmp.resolve("out/p/notes.txt")).doesNotExist();
    }
}
