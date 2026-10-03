package name.velikodniy.jcexpress.converter.check;

import name.velikodniy.jcexpress.converter.input.ClassFileReader;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.translate.FixtureCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Java Card language subset (JCVM 3.1 §2.2.1): each case is a small class compiled with javac
 * at test time; the checker must reject it with a message naming the rule, and report the source
 * line where the construct is used. Before these rules the converter accepted all of these
 * constructs and produced CAP files that the off-card verifier rejects or that behave differently
 * from the Java source (synchronized ignored, transient fields persistent, ...).
 */
class LanguageSubsetRulesTest {

    @TempDir
    Path out;

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            char local (§2.2.1.3)          | static void f(byte[] b) { char c = (char) b[0]; b[1] = (byte) c; }        | char
            char array (§2.2.1.3)          | static void f() { char[] c = new char[2]; c[0] = 1; }                      | char
            multi-dim array (§2.2.1.3)     | static byte f() { byte[][] m = new byte[2][2]; return m[1][1]; }           | more than one dimension
            native (§2.2.1.2)              | static native void f();                                                    | native
            synchronized method (§2.2.1.2) | static synchronized void f() { }                                           | synchronized
            synchronized block (§2.2.1.2)  | static void f(Object o) { synchronized (o) { o.equals(o); } }              | threading
            volatile field (§2.2.1.2)      | volatile short v;                                                          | volatile
            transient field (§2.2.1.2)     | transient short t;                                                         | transient
            varargs (§2.2.1.1.9)           | static short sum(short... v) { return v[0]; }                              | variable-length argument
            assert (§2.2.1.1.11)           | static void f(short s) { assert s > 0; }                                   | assert
            String literal (§2.2.1.4)      | static Object f() { return "abc"; }                                        | String
            class literal (§2.2.1.4)       | static Object f() { return Probe.class; }                                  | class literals
            string switch (§2.2.1.4)       | static short f(String s) { switch (s) { case "a": return 1; default: return 0; } } | java/lang/String
            System (§2.2.1.4.1)            | static void f(byte[] a, byte[] b) { System.arraycopy(a, 0, b, 0, 2); }     | JCSystem
            exception message (§2.2.1.4)   | static void f() { throw new RuntimeException(); } static void g() { throw new ArithmeticException(null); } | java/lang/String
            catch AssertionError (§2.2.1.4) | static void f(Object o) { try { o.equals(o); } catch (AssertionError e) { } } | catch clause
            array clone (§2.2.1.1.5)       | static byte[] f(byte[] b) { return b.clone(); }                            | cloning
            lambda                         | static Object f() { Runnable r = () -> { }; return r; }                    | invokedynamic
            Integer boxing (§2.2.1.4)      | static Object f(short s) { return Integer.valueOf(s); }                    | wrapper classes
            string concatenation (§2.2.1.4) | static Object f(Object o) { return "v=" + o; }                            | String
            """)
    void constructOutsideTheSubsetIsRejected(String name, String members, String expected) throws Exception {
        List<Violation> violations = check(members, 8);
        assertThat(violations).as(name).isNotEmpty();
        assertThat(violations).as(name).anyMatch(v -> v.message().contains(expected));
        assertThat(violations).as(name).allMatch(v -> v.className().equals("probe/Probe"));
    }

    @Test
    void violationsNameTheSourceLine() throws Exception {
        List<Violation> violations = check("static void f(byte[] a, byte[] b) {\n"
                + "  System.arraycopy(a, 0, b, 0, 2);\n}", 8);
        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.line()).isEqualTo(5);
            assertThat(v.sourceFile()).isEqualTo("Probe.java");
            assertThat(v.toString()).contains("f([B[B)V", "(Probe.java:5)", "java/lang/System");
        });
    }

    /** JCVM 3.1 §2.2.4.6: static final fields of primitive types are compile-time constants. */
    @Test
    void blankStaticFinalPrimitiveIsRejectedAtItsAssignment_2_2_4_6() throws Exception {
        List<Violation> violations = check("""
                private static final short BLANK;
                static {
                    BLANK = 0x0BEE;
                }""", 8);

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.className()).isEqualTo("probe/Probe");
            assertThat(v.context()).isEqualTo("<clinit>()V");
            assertThat(v.line()).isEqualTo(6);
            assertThat(v.message()).contains("static final short field BLANK", "compile-time constants");
        });
    }

    @Test
    void staticFinalConstantsAndOtherStaticInitializersAreAccepted_2_2_4_6() throws Exception {
        assertThat(check("""
                static final short CONSTANT = 0x0BEE;
                static final boolean FLAG = true;
                static final byte[] TABLE = {1, 2, 3};
                static short counter = 5;""", 8)).isEmpty();
    }

    @Test
    void interfaceMethodWithBodyIsRejected_6_10() throws Exception {
        List<Violation> violations = checkUnit("probe.Probe", "package probe;\n\npublic interface Probe {\n"
                + "  default short twice(short s) { return (short) (s * 2); }\n}\n", 8);
        assertThat(violations).anyMatch(v -> v.message().contains("interface method with a body"));
    }

    @Test
    void enumIsRejectedWithOneMessage_2_2_1_1_7() throws Exception {
        List<Violation> violations = checkUnit("probe.Probe",
                "package probe;\n\npublic enum Probe { A, B }\n", 8);
        assertThat(violations).singleElement()
                .satisfies(v -> assertThat(v.message()).contains("enum types are not supported"));
    }

    @Test
    void annotationTypeIsRejected_2_2_1_1_10() throws Exception {
        List<Violation> violations = checkUnit("probe.Probe",
                "package probe;\n\npublic @interface Probe { }\n", 8);
        assertThat(violations).singleElement()
                .satisfies(v -> assertThat(v.message()).contains("annotation types"));
    }

    @Test
    void recordIsRejected() throws Exception {
        List<Violation> violations = checkUnit("probe.Probe",
                "package probe;\n\npublic record Probe(short a) { }\n", 17);
        assertThat(violations).singleElement()
                .satisfies(v -> assertThat(v.message()).contains("records are not supported"));
    }

    /**
     * Other Java SE API references (here {@code java.util.Arrays} and the
     * {@code java.util.Objects.requireNonNull} null check that javac inserts for
     * {@code outer.new Inner()}) are not language-subset questions: the link check reports them
     * against the export files of the target platform (with the Java Card version that provides an
     * API element), and the Maven plugin explains the javac-inserted null check. The subset check
     * must leave them alone so that each problem is reported once, by the stage that owns it.
     */
    @Test
    void javaSeApiReferencesAreLeftToTheLinkCheck() throws Exception {
        String source = "package probe;\n\npublic class Probe {\n  short v;\n"
                + "  class Inner { short get() { return v; } }\n"
                + "  static Inner make(Probe p) { return p.new Inner(); }\n"
                + "  static void clear(byte[] b) { java.util.Arrays.fill(b, (byte) 0); }\n}\n";
        assertThat(checkUnit("probe.Probe", source, 8)).isEmpty();
    }

    @Test
    void javaCardPlatformMembersOfJavaLangAreAccepted_2_2_2_4() throws Exception {
        String members = """
                static boolean f(Object a, Object b) { return a.equals(b); }
                static void g() { throw new SecurityException(); }
                static short h(Object o) {
                    try { o.equals(o); } catch (NullPointerException e) { return 1; }
                    catch (RuntimeException e) { return 2; }
                    return 0;
                }
                static Object k() { return new java.io.IOException(); }
                """;
        assertThat(check(members, 8)).isEmpty();
    }

    @Test
    void cleanAppletCodeHasNoViolations() throws Exception {
        String members = """
                private final byte[] state = new byte[4];
                private short counter;
                void process(byte[] buf, short off, short len) {
                    for (short i = 0; i < len; i++) {
                        state[(short) (i & 3)] ^= buf[(short) (off + i)];
                    }
                    counter = (short) (counter + len);
                    if ((buf[0] & 0x80) != 0 && len > (short) 2) {
                        buf[1] = (byte) (counter >> 8);
                        buf[2] = (byte) counter;
                    }
                }
                """;
        assertThat(check(members, 8)).isEmpty();
        assertThat(check(members, 21)).isEmpty();
    }

    private List<Violation> check(String members, int release) throws Exception {
        String source = "package probe;\n\npublic class Probe {\n" + members + "\n}\n";
        return checkUnit("probe.Probe", source, release);
    }

    private List<Violation> checkUnit(String fqcn, String source, int release) throws Exception {
        Path classes = out.resolve("r" + release + "-" + Math.abs(source.hashCode()));
        FixtureCompiler.compileSource(fqcn, source, release, classes);
        List<ClassInfo> infos = new ArrayList<>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".class")).sorted().toList()) {
                infos.add(read(p));
            }
        }
        return SubsetChecker.check(infos);
    }

    private static ClassInfo read(Path classFile) throws IOException {
        return ClassFileReader.readFile(classFile);
    }
}
