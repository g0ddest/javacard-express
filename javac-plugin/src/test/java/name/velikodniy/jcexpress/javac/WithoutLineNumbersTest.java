package name.velikodniy.jcexpress.javac;

import name.velikodniy.jcexpress.javac.Javac.Result;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A compilation without line numbers ({@code -g:none}, {@code -g:source,vars}; Maven's {@code debuglevel}): javac
 * keeps no line map of the sources and writes no LineNumberTable (JVMS §4.7.12). The check still reports every
 * problem, at its member.
 */
class WithoutLineNumbersTest {

    private static final String LEDGER = """
            package p;

            public class Ledger {
                long total;

                short add(short amount) {
                    long sum = total + amount;
                    return (short) sum;
                }
            }
            """;

    @TempDir
    Path project;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"-g:none", "-g:source,vars", "-g:vars", "-g:source"})
    void problemsAreErrorsAtTheirMembers_jcvm31_2_2_1_3(String debug) {
        Result result = Javac.compile(project, Map.of("p/Ledger.java", LEDGER), debug);

        assertThat(result.success()).isFalse();
        assertThat(result.diagnostics()).allSatisfy(diagnostic ->
                assertThat(diagnostic.getKind()).isEqualTo(Diagnostic.Kind.ERROR));
        assertThat(result.errors()).isNotEmpty().allSatisfy(error -> assertThat(error).contains("long"))
                .anySatisfy(error -> assertThat(error).startsWith("Ledger.java:4:"))
                .anySatisfy(error -> assertThat(error).startsWith("Ledger.java:6:"));
        assertThat(result.hasClassFile("p.Ledger")).isFalse();
    }
}
