package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.PackageCheck.Problem;
import name.velikodniy.jcexpress.converter.PackageCheck.Settings;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Where the problems of {@link PackageCheck} are: in code or in a declaration, with or without the line numbers of a
 * class file, and each catch clause at its own handler.
 */
class PackageCheckSitesTest {

    private static final Settings DEFAULT = new Settings(JavaCardVersion.defaultVersion(), false, null);

    @TempDir
    Path classes;

    /** javac -g:none writes no LineNumberTable (JVMS §4.7.12): the problem is still in the code of its method. */
    @Test
    void aLinkProblemInCodeWithoutLineNumbersIsInTheCode_jcvm31_4_3_5() throws Exception {
        JavaSources.compile(classes, Map.of("p.NoLines", """
                package p;
                public class NoLines {
                    void work(byte[] buffer) {
                        java.util.Arrays.fill(buffer, (byte) 0);
                    }
                }
                """), "-g:none");

        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT)).singleElement().satisfies(problem -> {
            assertThat(problem.member()).isEqualTo("work([B)V");
            assertThat(problem.line()).isEqualTo(-1);
            assertThat(problem.declaration()).isFalse();
        });
    }

    /** A declaration stays a declaration without line numbers: the field's type, not code. */
    @Test
    void aSubsetProblemOfADeclarationWithoutLineNumbersIsInTheDeclaration_jcvm31_2_2_1_3() throws Exception {
        JavaSources.compile(classes, Map.of("p.Field", """
                package p;
                public class Field {
                    long total;
                }
                """), "-g:none");

        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT)).singleElement()
                .extracting(Problem::member, Problem::line, Problem::declaration)
                .containsExactly("field total", -1, true);
    }

    /**
     * The conversion names a reference's method once (JCVM 3.1 §4.3.5); two catch clauses of the same class in one
     * method are two places in the source, each at the line of its handler.
     */
    @Test
    void eachCatchClauseOfTheSameClassKeepsItsLine_jcvm31_4_3_5() throws Exception {
        JavaSources.compile(classes, Map.of("p.TwoCatches", """
                package p;
                public class TwoCatches {
                    short work(byte[] buffer) {
                        short s = 0;
                        try {
                            s = buffer[0];
                        } catch (IllegalStateException e) {
                            s = 1;
                        }
                        try {
                            s = buffer[1];
                        } catch (IllegalStateException e) {
                            s = 2;
                        }
                        return s;
                    }
                }
                """));

        assertThat(PackageCheck.check("p", classFiles("p"), DEFAULT))
                .extracting(Problem::member, Problem::line)
                .containsExactly(tuple("work([B)S", 7), tuple("work([B)S", 12));
    }

    private List<byte[]> classFiles(String packageName) throws Exception {
        List<byte[]> files = new ArrayList<>();
        try (Stream<Path> list = Files.list(classes.resolve(packageName.replace('.', '/')))) {
            for (Path file : list.filter(f -> f.toString().endsWith(".class")).sorted().toList()) {
                files.add(Files.readAllBytes(file));
            }
        }
        return files;
    }
}
