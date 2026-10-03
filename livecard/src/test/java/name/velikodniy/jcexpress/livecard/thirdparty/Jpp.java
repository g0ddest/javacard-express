package name.velikodniy.jcexpress.livecard.thirdparty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The conditional compilation PivApplet's build runs before javac (its {@code jpp} step), written for these
 * tests from the directives the sources use:
 * <ul>
 *   <li>{@code //#if NAME}, {@code //#if !NAME}, {@code /*#if NAME} start a block that is kept when NAME is (not)
 *       defined;</li>
 *   <li>{@code #else} and {@code #endif}, each optionally preceded by {@code //} or {@code /*} or followed by
 *       the end of a block comment, switch and end it; the comment markers only keep the unprocessed sources
 *       compilable.</li>
 * </ul>
 * Directive lines and the lines of dropped blocks become empty lines, so line numbers stay those of the
 * upstream sources.
 */
final class Jpp {

    private static final Pattern IF = Pattern.compile("(?://|/\\*)#if\\s+(!?)(\\w+)\\s*");
    private static final Pattern ELSE = Pattern.compile("(?://|/\\*)?#else(?:\\*/)?\\s*");
    private static final Pattern ENDIF = Pattern.compile("(?://|/\\*)?#endif(?:\\*/)?\\s*");

    private Jpp() {
    }

    /**
     * Preprocesses every {@code .java} file below a directory into another directory (same relative paths).
     *
     * @param source  the source root
     * @param target  the output root
     * @param defines the defined names
     * @throws IOException           if a file cannot be read or written
     * @throws IllegalStateException if a file has unbalanced directives
     */
    static void processTree(Path source, Path target, Set<String> defines) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(source)) {
            files = walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        for (Path file : files) {
            List<String> lines = process(Files.readAllLines(file, StandardCharsets.UTF_8), defines, file.toString());
            Path out = target.resolve(source.relativize(file).toString());
            Files.createDirectories(out.getParent());
            Files.writeString(out, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
        }
    }

    /**
     * Preprocesses the lines of one file.
     *
     * @param lines   the source lines
     * @param defines the defined names
     * @param file    the file name, for error messages
     * @return the lines with directives and dropped lines empty
     * @throws IllegalStateException if the directives are unbalanced
     */
    static List<String> process(List<String> lines, Set<String> defines, String file) {
        Deque<Boolean> stack = new ArrayDeque<>();
        List<String> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            Matcher condition = IF.matcher(line);
            if (condition.matches()) {
                boolean defined = defines.contains(condition.group(2));
                stack.push(condition.group(1).isEmpty() == defined);
            } else if (ELSE.matcher(line).matches()) {
                stack.push(!pop(stack, file, i, "#else"));
            } else if (ENDIF.matcher(line).matches()) {
                pop(stack, file, i, "#endif");
            } else {
                out.add(stack.contains(Boolean.FALSE) ? "" : lines.get(i));
                continue;
            }
            out.add("");
        }
        if (!stack.isEmpty()) {
            throw new IllegalStateException(file + ": " + stack.size() + " #if without #endif");
        }
        return out;
    }

    private static boolean pop(Deque<Boolean> stack, String file, int index, String directive) {
        if (stack.isEmpty()) {
            throw new IllegalStateException(file + ":" + (index + 1) + ": " + directive + " without #if");
        }
        return stack.pop();
    }
}
