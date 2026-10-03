package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.plugin.testing.JavaSources;
import name.velikodniy.jcexpress.plugin.testing.MojoRunner;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog;
import name.velikodniy.jcexpress.plugin.testing.RecordingLog.Level;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Conversion errors point at the sources. Java Card does not support {@code long} (JCVM 3.1
 * &sect;2.2.1.3); each such use is reported once, as {@code <source file>:[<line>] <class>.<method>()},
 * and the failure message does not repeat the list.
 */
class ErrorReportingTest {

    /** {@code counter = counter + 1L;} is on this line of {@link #SOURCE}. */
    private static final int LONG_ARITHMETIC_LINE = 14;
    private static final String SOURCE = """
            package com.example.bad;

            import javacard.framework.APDU;
            import javacard.framework.Applet;

            public class BadApplet extends Applet {
                private long counter;

                public static void install(byte[] bArray, short bOffset, byte bLength) {
                    new BadApplet().register();
                }

                public void process(APDU apdu) {
                    counter = counter + 1L;
                }
            }
            """;

    @TempDir
    Path dir;

    @Test
    void violationsAreReportedOnceWithSourceFileAndLine() {
        MojoRunner runner = MojoRunner.forProject(dir,
                JavaSources.create().add("com/example/bad/BadApplet.java", SOURCE).compile(dir));

        MojoFailureException failure = catchThrowableOfType(MojoFailureException.class, runner::execute);

        List<String> errors = runner.log().messages(Level.ERROR);
        Path source = dir.resolve("src/com/example/bad/BadApplet.java");
        assertThat(errors).anyMatch(e -> e.startsWith(source + ":[" + LONG_ARITHMETIC_LINE + "] "
                + "com.example.bad.BadApplet.process(): "));
        assertThat(errors).anyMatch(e -> e.startsWith(source + " com.example.bad.BadApplet (field counter): "));
        assertThat(errors).noneMatch(e -> e.contains("bci"));
        assertThat(failure.getMessage()).contains("com.example.bad").contains("listed above")
                .doesNotContain("BadApplet.java").doesNotContain("long");
        int reported = count(String.join("\n", errors), "long");
        assertThat(reported).isEqualTo(errors.size() - 1);
    }

    /** The applet creates an instance of its inner class on this line of {@link #INNER_SOURCE}. */
    private static final int INNER_CREATION_LINE = 15;
    private static final String INNER_SOURCE = """
            package com.example.inner;

            import javacard.framework.APDU;
            import javacard.framework.Applet;

            public class InnerApplet extends Applet {
                short count;

                class Counter {
                    void increment() { count++; }
                }

                public static void install(byte[] bArray, short bOffset, byte bLength) {
                    InnerApplet applet = new InnerApplet();
                    applet.new Counter().increment();
                    applet.register();
                }

                public void process(APDU apdu) {
                }
            }
            """;

    @Test
    void nullChecksThatJavacInsertsAreExplained() {
        // javac null-checks the outer instance with java.util.Objects.requireNonNull, which the Java Card
        // java.lang/java.util API does not have (JCVM 3.1 2.2): for a qualified creation outer.new Inner()
        // (every --release) and, with --release 25, in the constructor of every inner class.
        MojoRunner runner = MojoRunner.forProject(dir, JavaSources.create()
                .add("com/example/inner/InnerApplet.java", INNER_SOURCE).compile(dir));

        MojoFailureException failure = catchThrowableOfType(MojoFailureException.class, runner::execute);

        Path source = dir.resolve("src/com/example/inner/InnerApplet.java");
        assertThat(failure.getMessage())
                .contains("java.util.Objects is used at " + source + ":[" + INNER_CREATION_LINE + "]")
                .contains("javac inserts java.util.Objects.requireNonNull")
                .contains("static nested class");
    }

    @Test
    void linkErrorsThatNameAPackageGetTheSameHints() throws Exception {
        // The converter's link check names the package, not the class: "no export file was supplied for
        // package java.util". The plugin still says which class of it is used where, and why.
        Path classes = JavaSources.create().add("com/example/inner/InnerApplet.java", INNER_SOURCE).compile(dir);
        ConverterException linkError = new ConverterException("Cannot link 1 reference(s) against the export files"
                + " for Java Card 3.0.5:\n  - com.example.inner.InnerApplet.install([BSB)V (InnerApplet.java:15):"
                + " no export file was supplied for package java.util (use importExportFile or exportPath)");

        MojoFailureException failure;
        try (ClassIndex index = ClassIndex.scan(classes, List.of())) {
            failure = new ConversionErrors(new SourceLocations(index, List.of(dir.resolve("src"))),
                    new PackageOrigins(index, List.of()), "com.example.inner", new RecordingLog()).report(linkError);
        }

        Path source = dir.resolve("src/com/example/inner/InnerApplet.java");
        assertThat(failure.getMessage())
                .contains("java.util.Objects is used at " + source + ":[" + INNER_CREATION_LINE + "]")
                .contains("javac inserts java.util.Objects.requireNonNull").contains("<exportPath>");
    }

    /** {@code int total = ...} is on this line of {@link #INT_SOURCE}. */
    private static final int INT_LOCAL_LINE = 13;
    private static final String INT_SOURCE = """
            package com.example.counter;

            import javacard.framework.APDU;
            import javacard.framework.Applet;

            public class CounterApplet extends Applet {
                public static void install(byte[] bArray, short bOffset, byte bLength) {
                    new CounterApplet().register();
                }

                public void process(APDU apdu) {
                    byte[] buffer = apdu.getBuffer();
                    int total = buffer[0] * 1000;
                    buffer[1] = (byte) (total >> 8);
                }
            }
            """;

    @Test
    void intErrorsNameTheSupportInt32ParameterAndTheLine() {
        MojoRunner runner = MojoRunner.forProject(dir, JavaSources.create()
                .add("com/example/counter/CounterApplet.java", INT_SOURCE).compile(dir));

        MojoFailureException failure = catchThrowableOfType(MojoFailureException.class, runner::execute);

        Path source = dir.resolve("src/com/example/counter/CounterApplet.java");
        assertThat(runner.log().messages(Level.ERROR))
                .anyMatch(e -> e.startsWith(source + ":[" + INT_LOCAL_LINE + "] com.example.counter.CounterApplet.process()")
                        && e.contains("local variable 'total'"))
                .noneMatch(e -> e.startsWith(source + " "));
        assertThat(failure.getMessage()).contains("<supportInt32>true</supportInt32>");
    }

    @Test
    void codeWithoutIntDoesNotMentionSupportInt32() {
        MojoRunner runner = MojoRunner.forProject(dir,
                JavaSources.create().add("com/example/bad/BadApplet.java", SOURCE).compile(dir));

        MojoFailureException failure = catchThrowableOfType(MojoFailureException.class, runner::execute);

        assertThat(failure.getMessage()).doesNotContain("supportInt32");
    }

    private static int count(String text, String word) {
        Matcher m = Pattern.compile("\\b" + word + "\\b").matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
