package name.velikodniy.jcexpress.javac;

import name.velikodniy.jcexpress.javac.Javac.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Errors land on the line the class file names, also where javac's code has no tree of its own there: a catch
 * clause, a class javac adds, an expression continued on the next line.
 */
class PlacementTest {

    @TempDir
    Path project;

    @Test
    void aCatchClauseOutsideTheSubsetIsAnErrorAtTheClause_jcvm31_2_2_1_1_11() {
        Result result = Javac.compile(project, Map.of("p/Guard.java", """
                package p;

                public class Guard {
                    short work(short s) {
                        short r = 0;
                        try {
                            r = (short) (s / 2);
                        } catch (AssertionError e) {
                            r = 1;
                        }
                        return r;
                    }
                }
                """));

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e).startsWith("Guard.java:8:")
                .contains("catch clause: java/lang/AssertionError"));
    }

    @Test
    void aCatchClauseTheTargetCannotLinkIsAnErrorAtTheClause_jcvm31_4_3_5() {
        Result result = Javac.compile(project, Map.of("p/Parse.java", """
                package p;

                public class Parse {
                    short work(short s) {
                        short r = 0;
                        try {
                            r = (short) (s / 2);
                        } catch (NumberFormatException e) {
                            r = 1;
                        }
                        return r;
                    }
                }
                """));

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e).startsWith("Parse.java:8:")
                .contains("java.lang.NumberFormatException"));
    }

    @Test
    void eachCatchClauseOfTheSameClassIsAnErrorAtItsOwnLine_jcvm31_4_3_5() {
        Result result = Javac.compile(project, Map.of("p/Twice.java", """
                package p;

                public class Twice {
                    short work(short s) {
                        short r = 0;
                        try {
                            r = (short) (s / 2);
                        } catch (NumberFormatException e) {
                            r = 1;
                        }
                        try {
                            r = (short) (s / 3);
                        } catch (NumberFormatException e) {
                            r = 2;
                        }
                        return r;
                    }
                }
                """));

        assertThat(result.errors()).hasSize(2)
                .anySatisfy(e -> assertThat(e).startsWith("Twice.java:8:").contains("java.lang.NumberFormatException"))
                .anySatisfy(e -> assertThat(e).startsWith("Twice.java:13:").contains("java.lang.NumberFormatException"));
    }

    @Test
    void theClassJavacAddsForASwitchOnAnEnumIsReportedAtTheSwitch_jcvm31_2_2_1_1_7() {
        Result result = Javac.compile(project, Map.of("p/Mode.java", """
                package p;

                enum Mode { ON, OFF }
                """, "p/Lamp.java", """
                package p;

                public class Lamp {
                    short level(Mode mode) {
                        switch (mode) {
                            case ON:
                                return 1;
                            default:
                                return 0;
                        }
                    }
                }
                """));

        // the code of the class javac adds is at the switch; its field, which has no line, at the class it serves
        assertThat(result.errors()).isNotEmpty()
                .allMatch(e -> e.startsWith("Mode.java:3:") || e.startsWith("Lamp.java:3:")
                        || e.startsWith("Lamp.java:5:"), "at the enum, the class or the switch")
                .anyMatch(e -> e.startsWith("Lamp.java:5:") && e.contains("$SwitchMap$"));
    }

    @Test
    void aCallOnAContinuationLineIsAnErrorOnThatLine_jcvm31_2_2_1_4() {
        Result result = Javac.compile(project, Map.of("p/Chain.java", """
                package p;

                public class Chain {
                    Object empty() {
                        return java.util.Collections
                                .emptyList();
                    }
                }
                """));

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e).startsWith("Chain.java:6:")
                .contains("java.util"));
    }
}
