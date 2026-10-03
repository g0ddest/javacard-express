package name.velikodniy.jcexpress.converter.translate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Translation for a target with int support (JCVM 3.1 §2.2.3.1). javac computes byte, short and
 * boolean expressions with int instructions; on the JCVM a value is a short (one word) or an int
 * (two words, §6.10.4), so the translator must choose s- or i-instructions per value, convert
 * with s2i (§7.5.82) / i2s (§7.5.27) where representations meet, renumber local variables (an int
 * takes two, §6.10.4) and count nargs, max_locals and invokeinterface nargs in words.
 *
 * <p>Before, the converter replaced every JVM int instruction by the JCVM int instruction, so short
 * code became unverifiable (sload/iload mismatch, "Bad local number"), and int comparisons became
 * if_scmp on ints. Expected sequences follow from the JVM semantics of each probe.
 */
class IntModeTranslationTest {

    @TempDir
    static Path out;

    @BeforeAll
    static void compile() throws Exception {
        FixtureCompiler.compileSource("probe.Ints", """
                package probe;
                public class Ints {
                    int total;
                    int v;
                    static short sum2(short a, short b) {
                        short r = 0;
                        if (a + b > 100) r = 1;
                        return r;
                    }
                    static short avg(short a, short b) { return (short) ((a + b) / 2); }
                    static short hash(byte[] buf) {
                        int acc = 0;
                        for (short i = 0; i < 8; i++) {
                            acc = acc * 31 + (buf[i] & 0xFF);
                        }
                        return (short) (acc >> 16);
                    }
                    static int twice(int v) { return v + v; }
                    static short mix(short a, int b, short c) { return (short) (a + b + c); }
                    static short sw(int k) {
                        switch (k) {
                            case 1: return 10;
                            case 2: return 20;
                            case 100000: return 30;
                            default: return 0;
                        }
                    }
                    void add() { total = total + 70000; }
                    boolean nonZero() { return total != 0; }
                    static int post(Ints o) { return o.v++; }
                    static int sum(int[] a) {
                        int s = 0;
                        for (short i = 0; i < a.length; i++) s += a[i];
                        return s;
                    }
                    static byte top(int x) { return (byte) (x >>> 24); }
                    static void take(int a, short b) { }
                    static void call(short s) { take(s, (short) 5); }
                    static void clear(byte[] buf) { for (int i = 0; i < 4; i++) buf[(short) i] = 0; }
                    static void setTotal(Ints o, short s) { o.total = s; }
                    static short shorts(byte[] buf, short off) {
                        short n = 0;
                        for (short i = 0; i < 4; i++) n = (short) (n + (buf[(short) (off + i)] & 0x7F));
                        return n;
                    }
                }
                """, 8, out);
    }

    @Test
    void sumOfShortsComparedAsInt() throws Exception {
        // JVM: a + b is an int (30000 + 30000 > 100); icmp compares the exact sum
        assertThat(translate("sum2").lines()).containsExactly(
                "sconst_0", "sstore_2", "sload_0", "s2i", "sload_1", "s2i", "iadd", "bipush 100",
                "icmp", "ifle -> 14", "sconst_1", "sstore_2", "sload_2", "sreturn");
    }

    @Test
    void divisionOfAnIntSum() throws Exception {
        assertThat(translate("avg").lines()).containsExactly(
                "sload_0", "s2i", "sload_1", "s2i", "iadd", "iconst_2", "idiv", "i2s", "sreturn");
    }

    @Test
    void intAccumulatorWithShortCounter() throws Exception {
        // locals: buf = 0, acc = 1..2 (int), i = 3
        Translated t = translate("hash");
        assertThat(t.lines()).containsSubsequence(
                "iconst_0", "istore_1", "sconst_0", "sstore_3", "sload_3", "bspush 8",
                "iload_1", "bipush 31", "imul", "aload_0", "sload_3", "baload", "s2i", "sipush 255",
                "iand", "iadd", "istore_1",
                "sinc 3 1", // the short counter's i++ (JCVM 3.1 §7.5.89)
                "iload_1", "bipush 16", "ishr", "i2s", "sreturn");
        assertThat(t.method().nargs()).isEqualTo(1);
        assertThat(t.method().maxLocals()).isEqualTo(3);
    }

    @Test
    void intParametersTakeTwoWords_6_10_4() throws Exception {
        Translated twice = translate("twice");
        assertThat(twice.lines()).containsExactly("iload_0", "iload_0", "iadd", "ireturn");
        assertThat(twice.method().nargs()).isEqualTo(2);

        Translated mix = translate("mix"); // a = 0, b = 1..2, c = 3
        assertThat(mix.lines()).containsExactly(
                "sload_0", "s2i", "iload_1", "iadd", "sload_3", "s2i", "iadd", "i2s", "sreturn");
        assertThat(mix.method().nargs()).isEqualTo(4);
    }

    @Test
    void switchOnAnIntKey_7_5_50() throws Exception {
        List<String> lines = translate("sw").lines();
        assertThat(lines.get(0)).isEqualTo("iload_0");
        assertThat(lines.get(1)).startsWith("ilookupswitch").contains("1->", "2->", "100000->");
        assertThat(lines).contains("bspush 10", "bspush 20", "bspush 30", "sconst_0", "sreturn");
    }

    @Test
    void intFieldArithmetic() throws Exception {
        assertThat(translate("add").lines()).containsExactly(
                "aload_0", "getfield_i_this #0", "iipush 70000", "iadd", "putfield_i #0", "return");
    }

    @Test
    void intComparedWithZeroUsesIcmp_7_5_32() throws Exception {
        assertThat(translate("nonZero").lines()).startsWith("getfield_i_this #0", "iconst_0", "icmp")
                .contains("sconst_1", "sconst_0", "sreturn");
    }

    @Test
    void dupX1OfAnIntBelowAReference_7_5_18() throws Exception {
        // o.v++: the int (2 words) is copied below the object reference (1 word): m = 2, n = 3
        assertThat(translate("post").lines()).containsExactly(
                "aload_0", "dup", "getfield_i #0", "dup_x 0x23", "iconst_1", "iadd", "putfield_i #0",
                "ireturn");
    }

    @Test
    void intArrayElementsAndShortIndex() throws Exception {
        // locals: a = 0, s = 1..2 (int), i = 3
        assertThat(translate("sum").lines()).containsSubsequence(
                "iconst_0", "istore_1", "sconst_0", "sstore_3", "sload_3", "aload_0", "arraylength",
                "iload_1", "aload_0", "sload_3", "iaload", "iadd", "istore_1", "iload_1", "ireturn");
    }

    @Test
    void unsignedShiftOfAnInt() throws Exception {
        assertThat(translate("top").lines()).containsExactly(
                "iload_0", "bipush 24", "iushr", "i2b", "sreturn");
    }

    @Test
    void shortArgumentWidenedForAnIntParameter() throws Exception {
        assertThat(translate("call").lines()).containsExactly(
                "sload_0", "s2i", "sconst_5", "invokestatic #0", "return");
    }

    @Test
    void intLoopCounterUsesIinc_7_5_45() throws Exception {
        assertThat(translate("clear").lines()).containsSubsequence(
                "iconst_0", "istore_1", "iload_1", "iconst_4", "icmp",
                "aload_0", "iload_1", "i2s", "sconst_0", "bastore", "iinc 1 1");
    }

    /**
     * JCVM 3.1 §6.10.4: max_stack counts words, an int takes two; conversions hold the value in
     * both forms for a moment (s2i of the top operand: one more word; iconst_0 before icmp: two).
     */
    @Test
    void maxStackCountsIntWords_6_10_4() throws Exception {
        assertThat(translate("sum2").method().maxStack()).isEqualTo(4);    // s2i, s2i: two ints
        assertThat(translate("nonZero").method().maxStack()).isEqualTo(4); // int + iconst_0
        assertThat(translate("call").method().maxStack()).isEqualTo(3);    // int + short argument
        Translated set = translate("setTotal");
        assertThat(set.lines()).containsExactly("aload_0", "sload_1", "s2i", "putfield_i #0", "return");
        assertThat(set.method().maxStack()).isEqualTo(3);                  // reference + int
    }

    /** Code without int values is translated exactly as for a target without int support. */
    @Test
    void shortCodeIsUnchanged() throws Exception {
        MethodModel m = method("shorts");
        TranslatedMethod withInt = BytecodeTranslator.translate(m, classModel(), null, true, true);
        TranslatedMethod withoutInt = BytecodeTranslator.translate(m, classModel(), null, false, true);
        assertThat(withInt).isEqualTo(withoutInt);
        assertThat(withInt.usesInt()).isFalse();
    }

    // ── helpers ──

    record Translated(TranslatedMethod method, List<String> lines) {}

    private static Translated translate(String name) throws Exception {
        TranslatedMethod tm = BytecodeTranslator.translate(method(name), classModel(), null, true, true);
        return new Translated(tm, JcvmDisassembler.lines(tm.bytecode()));
    }

    private static ClassModel classModel() throws Exception {
        return ClassFile.of().parse(Files.readAllBytes(out.resolve("probe/Ints.class")));
    }

    private static MethodModel method(String name) throws Exception {
        return classModel().methods().stream().filter(m -> m.methodName().equalsString(name))
                .findFirst().orElseThrow();
    }
}
