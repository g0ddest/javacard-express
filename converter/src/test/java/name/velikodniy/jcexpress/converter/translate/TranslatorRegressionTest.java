package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec-derived regression tests for single-instruction translation defects found by the audit.
 * Methods are translated in placeholder mode (constant pool operands are {@code #0}) and the
 * result is compared instruction by instruction with the sequence required by JCVM 3.1 Chapter 7.
 */
class TranslatorRegressionTest {

    private static final Path CLASSES = Path.of("target/test-classes");

    // ── arraylength (JCVM 3.1 §7.5.8) ──

    @Test
    void arraylengthIsTranslatedNotDropped_7_5_8() throws Exception {
        assertThat(translate("com/example/bytecode/arrlen/ArrLenApplet", "lengthOf"))
                .containsExactly("aload_0", "arraylength", "sreturn");
    }

    @Test
    void arraylengthInsideProcessIsKept_7_5_8() throws Exception {
        assertThat(translate("com/example/bytecode/arrlen/ArrLenApplet", "process"))
                .containsSubsequence("aload_2", "arraylength", "sstore_3");
    }

    // ── dup_x (JCVM 3.1 §7.5.18): n counts all words down to the insertion point incl. the m copied ──

    @Test
    void dupX1IsDupXWithMn12_7_5_18() throws Exception {
        assertThat(translate("com/example/bytecode/dupx/DupXApplet", "postIncrement"))
                .containsExactly("aload_0", "dup", "getfield_s #0", "dup_x 0x12", "sconst_1",
                        "sadd", "putfield_s #0", "sreturn");
    }

    @Test
    void dupX2IsDupXWithMn13_7_5_18() throws Exception {
        List<String> code = translate("com/example/bytecode/dupx/DupXApplet", "chained");
        assertThat(code).filteredOn(l -> l.startsWith("dup_x")).containsExactly("dup_x 0x13", "dup_x 0x13");
        assertThat(code).startsWith("aload_0", "sconst_0", "aload_0", "sconst_1", "sconst_5",
                "dup_x 0x13", "bastore", "bastore");
    }

    // ── getfield_<t>_this / putfield_<t>_this only in instance methods (JCVM 3.1 §7.5.21, §7.5.76) ──

    @Test
    void staticMethodDoesNotUseThisFieldOpcodes_7_5_21() throws Exception {
        assertThat(translate("com/example/bytecode/statthis/StatThisApplet", "peek"))
                .containsExactly("aload_0", "getfield_s #0", "sreturn");
    }

    @Test
    void staticMethodDoesNotUsePutfieldThis_7_5_76() throws Exception {
        assertThat(translate("com/example/bytecode/statthis/StatThisApplet", "bump"))
                .containsExactly("aload_0", "sload_1", "putfield_s #0", "return");
    }

    @Test
    void instanceMethodStillUsesThisFieldOpcodes_7_5_21() throws Exception {
        assertThat(translate("com/example/bytecode/statthis/StatThisApplet", "own"))
                .containsExactly("getfield_s_this #0", "sreturn");
        assertThat(translate("com/example/bytecode/statthis/StatThisApplet", "setOwn"))
                .containsExactly("sload_1", "putfield_s_this #0", "return");
    }

    // ── checkcast / instanceof against array types (JCVM 3.1 §7.5.16 Table 7-2, §7.5.53) ──

    @Test
    void checkcastAndInstanceofUseArrayAtypes_7_5_16() throws Exception {
        List<String> code = translate("com/example/bytecode/castarr/CastArrApplet", "process");
        assertThat(code).filteredOn(l -> l.startsWith("checkcast") || l.startsWith("instanceof"))
                .containsExactly(
                        "checkcast 11 #0",   // (byte[]) -> T_BYTE, index bytes zero
                        "instanceof 12 #0",  // instanceof short[] -> T_SHORT
                        "checkcast 14 #0",   // (AID[]) -> T_REFERENCE + ClassRef of AID
                        "instanceof 10 #0"); // instanceof boolean[] -> T_BOOLEAN
    }

    // ── stableswitch (JCVM 3.1 §7.5.106) ──

    /**
     * JCVM 3.1 §7.5.106: stableswitch has "high - low + 1 signed 16-bit offsets"; a key without a
     * case of its own jumps to the default. The JDK ClassFile API lists only the explicit cases of
     * a JVM tableswitch, so the gaps (key 3 here) must be filled with the default target; before,
     * the table was emitted one entry short and every following byte was misread.
     */
    @Test
    void tableswitchGapsJumpToTheDefault_7_5_106(@TempDir Path out) throws Exception {
        FixtureCompiler.compileSource("probe.Sw", """
                package probe;
                public class Sw {
                    static short f(byte k) {
                        switch (k) {
                            case 1: return 10;
                            case 2: return 20;
                            case 4: return 40;
                            default: return 0;
                        }
                    }
                }
                """, 8, out);
        ClassModel cm = ClassFile.of().parse(Files.readAllBytes(out.resolve("probe/Sw.class")));
        MethodModel f = cm.methods().stream().filter(m -> m.methodName().equalsString("f"))
                .findFirst().orElseThrow();
        List<JcvmDisassembler.Insn> code = JcvmDisassembler.disassemble(
                BytecodeTranslator.translate(f, cm, new JcvmConstantPool()).bytecode());

        JcvmDisassembler.Insn sw = code.get(1);
        assertThat(sw.mnemonic()).isEqualTo("stableswitch");
        Matcher m = Pattern.compile("default->(\\d+) low=1 high=4 \\[(\\d+), (\\d+), (\\d+), (\\d+)]")
                .matcher(sw.operands());
        assertThat(m.matches()).as(sw.operands()).isTrue();
        assertThat(m.group(4)).as("key 3 has no case").isEqualTo(m.group(1));
        assertThat(textAt(code, m.group(2))).isEqualTo("bspush 10");
        assertThat(textAt(code, m.group(3))).isEqualTo("bspush 20");
        assertThat(textAt(code, m.group(5))).isEqualTo("bspush 40");
        assertThat(textAt(code, m.group(1))).isEqualTo("sconst_0");
    }

    // ── short increment (JCVM 3.1 §7.5.89 sinc) ──

    /**
     * JCVM 3.1 §7.5.89: sinc increments a short local variable by a sign-extended byte constant,
     * the same 16-bit sum as sload, push, sadd/ssub, sstore. javac's {@code s++}, {@code s--},
     * {@code s += k} and {@code s -= k} on short locals become sinc when the increment is a byte
     * constant (the Oracle 3.0.5 converter, run as a black box, does the same and keeps the four
     * instructions for sspush constants); a byte local keeps its s2b truncation.
     */
    @Test
    void shortIncrementOfALocalVariableBecomesSinc_7_5_89() throws Exception {
        FixtureCompiler.compileSource("probe.Inc", """
                package probe;
                public class Inc {
                    static short f(short s, short t) {
                        s++;
                        s--;
                        s += 5;
                        s -= 100;
                        s += 300;
                        s -= 128;
                        t = (short) (t + 1);
                        byte b = (byte) s;
                        b++;
                        return (short) (s + t + b);
                    }
                }
                """, 8, probeClasses);
        ClassModel cm = ClassFile.of().parse(Files.readAllBytes(probeClasses.resolve("probe/Inc.class")));
        MethodModel f = cm.methods().stream().filter(m -> m.methodName().equalsString("f"))
                .findFirst().orElseThrow();

        assertThat(JcvmDisassembler.lines(BytecodeTranslator.translate(f, cm, new JcvmConstantPool()).bytecode()))
                .containsExactly(
                        "sinc 0 1", "sinc 0 -1", "sinc 0 5", "sinc 0 -100",
                        "sload_0", "sspush 300", "sadd", "sstore_0",
                        "sload_0", "sspush 128", "ssub", "sstore_0",
                        "sinc 1 1",
                        "sload_0", "s2b", "sstore_2",
                        "sload_2", "sconst_1", "sadd", "s2b", "sstore_2",
                        "sload_0", "sload_1", "sadd", "sload_2", "sadd", "sreturn");
    }

    // ── Method component limits (JCVM 3.1 §6.10.1) ──

    /**
     * JCVM 3.1 §6.10.1: handler_count is a u1, so the Method component of a package holds at most
     * 255 exception handlers. 90 methods with try/catch/finally need more (javac emits several
     * handlers per such block); the error names the methods with the most handlers and their
     * source file, so the user knows where to cut.
     */
    @Test
    void moreThan255HandlersAreRejectedNamingTheMethods_6_10_1(@TempDir Path out) throws Exception {
        StringBuilder src = new StringBuilder("package probe.manyh;\npublic class ManyH {\n"
                + "    private short cnt;\n");
        for (int i = 0; i < 90; i++) {
            src.append("    public short m").append(i).append("(byte[] b) {\n")
                    .append("        try { if (b[0] == ").append(i).append(") { cnt = 1; } }\n")
                    .append("        catch (NullPointerException e) { return 0; }\n")
                    .append("        finally { cnt++; }\n")
                    .append("        return 1;\n    }\n");
        }
        FixtureCompiler.compileSource("probe.manyh.ManyH", src.append("}\n").toString(), 8, out);
        Converter converter = Converter.builder().classesDirectory(out).packageName("probe.manyh")
                .packageAid("F04A4358430C").packageVersion(1, 0).build();

        assertThatThrownBy(converter::convert).isInstanceOf(ConverterException.class)
                .hasMessageMatching("(?s).*Package contains \\d{3} exception handlers;.* at most 255"
                        + ".*probe/manyh/ManyH\\.m\\d+\\(\\[B\\)S \\(ManyH\\.java\\): \\d+.*");
    }

    // ── fail-closed translation (JCVM 3.1 Chapter 7 defines every instruction of the CAP) ──

    @TempDir
    Path probeClasses;

    /**
     * An instruction without an exact JCVM counterpart stops the translation with the class,
     * method and source line; nothing is dropped (before, ldc of a String and multianewarray
     * disappeared silently and the CAP failed verification with a stack underflow). The subset
     * check rejects these constructs first; this is the translator's own guard.
     */
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            static Object f() { return "abc"; }                 | unsupported constant abc (String)
            static Object f() { return new byte[2][3]; }        | unsupported instruction multianewarray
            static long f(long a) { return a; }                 | unsupported local variable type LONG
            static Object f() { return (Runnable) () -> { }; }  | unsupported instruction invokedynamic
            """)
    void untranslatableInstructionStopsTheTranslationWithItsLocation(String method, String reason)
            throws Exception {
        FixtureCompiler.compileSource("probe.Bad", "package probe;\n\npublic class Bad {\n"
                + method + "\n}\n", 8, probeClasses);
        ClassModel cm = ClassFile.of().parse(Files.readAllBytes(probeClasses.resolve("probe/Bad.class")));
        MethodModel f = cm.methods().stream().filter(m -> m.methodName().equalsString("f"))
                .findFirst().orElseThrow();

        assertThatThrownBy(() -> BytecodeTranslator.translate(f, cm, new JcvmConstantPool()))
                .isInstanceOf(TranslationException.class)
                .hasMessageStartingWith("probe/Bad.f")
                .hasMessageContaining("(Bad.java:4): " + reason);
    }

    private static String textAt(List<JcvmDisassembler.Insn> code, String pc) {
        int target = Integer.parseInt(pc);
        return code.stream().filter(i -> i.pc() == target).findFirst()
                .map(JcvmDisassembler.Insn::text).orElse("no instruction at " + target);
    }

    // ── helpers ──

    static List<String> translate(String internalName, String methodName) throws Exception {
        ClassModel cm = ClassFile.of().parse(Files.readAllBytes(CLASSES.resolve(internalName + ".class")));
        MethodModel mm = cm.methods().stream()
                .filter(m -> m.methodName().stringValue().equals(methodName))
                .findFirst().orElseThrow(() -> new AssertionError("no method " + methodName));
        TranslatedMethod tm = BytecodeTranslator.translate(mm, cm, new JcvmConstantPool());
        return JcvmDisassembler.lines(tm.bytecode());
    }
}
