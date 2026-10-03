package name.velikodniy.jcexpress.converter.check;

import name.velikodniy.jcexpress.converter.input.ClassFileReader;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.translate.FixtureCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JCVM 3.1 §2.2.3.1 Integer Data Type: "A Java Card virtual machine that does not support the int
 * data type will reject programs which use the int data type or 32-bit intermediate values ...
 * must reject expressions that could produce a different result." Each case is a small Java
 * method compiled with javac; the expected outcome follows from comparing 32-bit JVM arithmetic
 * with the 16-bit JCVM instruction the converter would emit.
 */
class IntSubsetRulesTest {

    @TempDir
    Path out;

    // ── rejected without int support ──

    @Test
    void sumComparedWithoutCastIsRejected() throws Exception {
        // a = b = 30000: JVM 60000 > 100 is true, JCVM sadd wraps to -5536
        List<Violation> v = check("static short f(short a, short b) {\n"
                + "  if (a + b > 100) return 1;\n  return 0;\n}");
        assertThat(v).singleElement().satisfies(x -> {
            assertThat(x.message()).contains("compared", "2.2.3.1");
            assertThat(x.line()).isEqualTo(5);
            assertThat(x.sourceFile()).isEqualTo("Probe.java");
            assertThat(x.toString()).contains("(Probe.java:5)");
        });
    }

    @Test
    void sumDividedBeforeTheCastIsRejected() throws Exception {
        // (short)((30000 + 30000) / 2) = 30000 in Java, sdiv of the wrapped sum gives -2768
        assertThat(check("static short f(short a, short b) { return (short) ((a + b) / 2); }"))
                .singleElement().satisfies(x -> assertThat(x.message()).contains("divided"));
    }

    @Test
    void sumShiftedRightBeforeTheCastIsRejected() throws Exception {
        assertThat(check("static short f(short a, short b) { return (short) ((a + b) >> 1); }"))
                .singleElement().satisfies(x -> assertThat(x.message()).contains("shifted right"));
    }

    @Test
    void unsignedShiftOfAnIntermediateValueIsRejected() throws Exception {
        assertThat(check("static short f(short a, short b) { return (short) ((a + b) >>> 1); }"))
                .singleElement().satisfies(x -> assertThat(x.message()).contains("shifted right"));
    }

    @Test
    void intermediateArrayIndexIsRejected_2_2_1_1_8() throws Exception {
        assertThat(check("static byte f(byte[] buf, short off) { return buf[off + 1]; }"))
                .singleElement().satisfies(x -> assertThat(x.message()).contains("array index", "2.2.1.1.8"));
    }

    /**
     * §2.2.1.1.8 is a rule about the Java Card type of the index: a local variable stands for any
     * value of its declared type, so {@code off + 1} with a short variable {@code off} is an int
     * expression even when the variable holds the constant 5 here. (The Oracle 3.0.5 converter,
     * run as a black box, rejects these cases too.)
     */
    @Test
    void indexFromAShortVariablePlusOneIsRejectedEvenWhenItsValueFits_2_2_1_1_8() throws Exception {
        assertThat(check("static void f(byte[] buf) { short off = 5; buf[off + 1] = buf[off]; }"))
                .singleElement().satisfies(x -> assertThat(x.message()).contains("array index"));
        assertThat(check("static void f(byte[] buf, short off) { buf[off - 1] = 0; }"))
                .singleElement().satisfies(x -> assertThat(x.message()).contains("array index"));
        assertThat(check("static byte[] f(short n) { return new byte[n * 2]; }"))
                .singleElement().satisfies(x -> assertThat(x.message()).contains("array size"));
    }

    /**
     * An int expression whose value stays in the short range for every value of its operands'
     * types is a short value (§2.2.3.1: it cannot produce a different result) and a valid index:
     * {@code x & 3}, a byte plus one, a nibble of a byte. (The Oracle 3.0.5 converter, run as a
     * black box, accepts these cases too.)
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "static byte f(byte[] b, short x) { return b[x & 3]; }",
            "static void f(byte[] buf, byte a) { buf[a + 1] = 0; }",
            "static byte f(byte[] hex, byte b) { return hex[(b >> 4) & 0x0F]; }",
            "static byte f(byte[] t, byte a, byte b) { return t[a + b]; }",
            "static byte[] f(byte n) { return new byte[n * 2]; }"
    })
    void indexWhoseValueStaysInTheShortRangeIsAccepted_2_2_1_1_8(String method) throws Exception {
        assertThat(check(method)).isEmpty();
        assertThat(check(method, true)).isEmpty();
    }

    @Test
    void intLocalVariableIsRejected() throws Exception {
        List<Violation> v = check("static short f(short a, short b) {\n"
                + "  int sum = a + b;\n  return (short) sum;\n}");
        assertThat(v).extracting(Violation::message)
                .anyMatch(m -> m.contains("local variable 'sum' of type int"))
                .anyMatch(m -> m.contains("stored in local variable"));
    }

    @Test
    void intLoopCounterIsRejected() throws Exception {
        assertThat(check("static void f(byte[] buf) { for (int i = 0; i < 4; i++) { buf[(short) i] = 0; } }"))
                .extracting(Violation::message).anyMatch(m -> m.contains("incremented as an int"));
    }

    @Test
    void intConstantOutsideTheShortRangeIsRejected() throws Exception {
        assertThat(check("static short f(short x) { return (short) ((x & 0xFFFF) >>> 4); }"))
                .extracting(Violation::message).anyMatch(m -> m.contains("int constant 65535"));
    }

    @Test
    void intFieldParameterAndReturnAreRejected() throws Exception {
        assertThat(check("int total;\nstatic int twice(int v) { return v + v; }"))
                .extracting(Violation::message)
                .anyMatch(m -> m.contains("field of type int"))
                .anyMatch(m -> m.contains("int parameter or return type"));
    }

    @Test
    void callOfAnIntMethodOfThePackageIsRejected() throws Exception {
        assertThat(check("static int g() { return 1; }\nstatic short f() { return (short) g(); }"))
                .extracting(Violation::message)
                .anyMatch(m -> m.contains("call of probe/Probe.g()I uses the int type"));
    }

    /**
     * JCVM 3.1 §2.2.1.4: Java SE classes and members such as Math.max or Object.hashCode are not
     * part of the Java Card platform, which the link check against the export files reports. No
     * member of the Java Card java.* API has int in its signature, so reporting such a call as a
     * use of the int type would wrongly suggest that int support helps.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "static short f(short a, short b) { return (short) Math.max(a, b); }",
            "static byte f(Object o) { return (byte) o.hashCode(); }",
            "static byte f(short a) { return (byte) Math.abs(a); }"
    })
    void javaSeMembersWithIntInTheirSignatureAreLeftToTheLinkCheck_2_2_1_4(String method) throws Exception {
        assertThat(check(method)).isEmpty();
    }

    @Test
    void intArrayIsRejected() throws Exception {
        assertThat(check("static void f() { int[] h = new int[4]; h[0] = 1; }"))
                .extracting(Violation::message).anyMatch(m -> m.contains("int[] creation"));
    }

    // ── accepted without int support: the 16-bit result equals the Java result ──

    @ParameterizedTest
    @ValueSource(strings = {
            "static short f(short a, short b) { return (short) (a + b); }",
            "static byte f(short s) { return (byte) (s >> 8); }",
            "static byte f(short s) { return (byte) (s >>> 8); }",
            "static short f(byte[] b) { return (short) ((b[0] << 8) | (b[1] & 0xFF)); }",
            "static short f(short a, short b) { return (short) (a / b); }",
            "static boolean f(short a, short b) { return a % b > 0; }",
            "static boolean f(short a, short b) { return (a & b) > 0; }",
            "static boolean f(short a) { return (a >> 1) > 0; }",
            "static byte f(byte[] buf, short off) { return buf[(short) (off + 1)]; }",
            "static byte f(byte[] b, short x) { return b[(short) (x & 3)]; }",
            "static byte f(byte[] b, boolean c, short i, short j) { return b[c ? i : j]; }",
            "static byte f(byte[] b) { return b[b.length - 1 > 0 ? 1 : 0]; }",
            "static short f(byte[] buf) { return (short) (buf[0] & 0xFF); }",
            "static boolean f(short a, short b, boolean c) { return (c ? a : b) < 10; }",
            "static void f(byte[] buf, short s) { buf[0] = (byte) (s + 1); buf[1] += 5; }",
            "static short f(short s) { s++; s += 300; return s; }",
            "static short f(byte[] buf) { short n = 0; for (short i = 0; i < 8; i++) { n += buf[i]; } return n; }",
            "static short f(byte p1) { switch (p1) { case 1: return 10; case 2: return 20; default: return 0; } }"
    })
    void exactShortArithmeticIsAccepted(String method) throws Exception {
        assertThat(check(method)).isEmpty();
    }

    // ── with int support ──

    @Test
    void intArithmeticIsAcceptedWithIntSupport() throws Exception {
        assertThat(check("int total;\nstatic short f(short a, short b) { int s = a + b; if (s > 100) return 1;"
                + " return (short) (s / 2); }", true)).isEmpty();
    }

    @Test
    void intArrayIndexIsRejectedEvenWithIntSupport_2_2_1_1_8() throws Exception {
        assertThat(check("static byte f(byte[] buf, int i) { return buf[i]; }", true))
                .singleElement().satisfies(x -> assertThat(x.message()).contains("array index"));
        assertThat(check("static byte f(byte[] buf) { int k = 2; return buf[k]; }", true))
                .singleElement().satisfies(x -> assertThat(x.message()).contains("array index"));
        assertThat(check("static void f(byte[] buf, short off) { int i = off; buf[i] = 0; }", true))
                .singleElement().satisfies(x -> assertThat(x.message()).contains("array index"));
        assertThat(check("static void f(byte[] buf, short off) { buf[off + 1] = 0; }", true))
                .singleElement().satisfies(x -> assertThat(x.message()).contains("array index"));
        assertThat(check("static void f(byte[] buf) { short off = 5; buf[off + 1] = 0; }", true))
                .singleElement().satisfies(x -> assertThat(x.message()).contains("array index"));
    }

    @Test
    void intVariableCastToShortIsAValidIndexWithIntSupport_2_2_1_1_8() throws Exception {
        assertThat(check("static void f(byte[] buf, short off) { int i = off; buf[(short) i] = 0; }", true))
                .isEmpty();
    }

    private List<Violation> check(String members) throws Exception {
        return check(members, false);
    }

    private List<Violation> check(String members, boolean intSupported) throws Exception {
        String source = "package probe;\n\npublic class Probe {\n" + members + "\n}\n";
        FixtureCompiler.compileSource("probe.Probe", source, 8, out);
        ClassInfo ci = ClassFileReader.readFile(out.resolve("probe/Probe.class"));
        return SubsetChecker.check(List.of(ci), intSupported);
    }
}
