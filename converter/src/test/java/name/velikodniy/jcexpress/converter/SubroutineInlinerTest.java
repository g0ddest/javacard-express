package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.SubroutineShapes.Shape;
import name.velikodniy.jcexpress.converter.check.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code jsr}/{@code ret} subroutines are inlined into ordinary code with the same behaviour.
 *
 * <p>JCVM 3.1 §2.3.2.2 lists {@code jsr} and {@code ret} among the supported class file bytecodes, and
 * javac 1.3 or ECJ with {@code -target} 1.4 or lower compile {@code finally} blocks into such subroutines
 * (JVMS 4.10.2.5). The converter replaces every call by a copy of the subroutine (a {@code ret} becomes a
 * jump back to the instruction after the call). Each test method of {@link SubroutineShapes} runs on this JVM
 * in both forms, before and after inlining, for a range of arguments: same results, same exceptions, same
 * side effects.
 */
class SubroutineInlinerTest {

    private static final int JAVA_1_2 = 46;
    private static final short[] ARGUMENTS = {-1, 0, 1, 2, 3, 4, 5, 6, 7, 100};

    static Stream<Shape> shapes() {
        return Stream.concat(SubroutineShapes.JAVA_CARD.stream(), Stream.of(SubroutineShapes.WIDE_FORMS));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void inlinedCodeBehavesLikeTheSubroutines_2_3_2_2_7_5_69_7_5_79(Shape shape) throws Exception {
        byte[] original = SubroutineShapes.finClass(JAVA_1_2, List.of(shape));
        byte[] inlined = inlineWithoutViolations(original);

        assertThat(subroutineInstructions(original)).isPositive();
        assertThat(subroutineInstructions(inlined)).isZero();
        for (short m : ARGUMENTS) {
            assertThat(run(inlined, shape.name(), m)).as("%s(%d)", shape, m).isEqualTo(run(original, shape.name(), m));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapes")
    void sourceLinesAreKept(Shape shape) {
        byte[] original = SubroutineShapes.finClass(JAVA_1_2, List.of(shape));

        assertThat(lines(inlineWithoutViolations(original), shape.name())).isEqualTo(lines(original, shape.name()));
    }

    @Test
    void classesWithoutSubroutinesAreReturnedUnchanged() {
        byte[] plain = SubroutineShapes.finClass(JAVA_1_2, List.of());

        assertThat(inlineWithoutViolations(plain)).isSameAs(plain);
    }

    @Test
    void onlyMethodsWithSubroutinesAreRewritten() {
        byte[] original = SubroutineShapes.finClass(JAVA_1_2, SubroutineShapes.JAVA_CARD);
        ClassModel before = ClassFile.of().parse(original);
        ClassModel after = ClassFile.of().parse(inlineWithoutViolations(original));

        assertThat(after.majorVersion()).isEqualTo(JAVA_1_2);
        assertThat(after.methods()).extracting(m -> m.methodName().stringValue())
                .containsExactlyElementsOf(before.methods().stream().map(m -> m.methodName().stringValue()).toList());
        assertThat(code(after, ConstantDescs.INIT_NAME).codeArray())
                .isEqualTo(code(before, ConstantDescs.INIT_NAME).codeArray());
    }

    // ---- shapes that cannot be inlined (no compiler writes them): rejected with the reason

    /** JVMS 4.9.2 allows a nested subroutine to return to the caller of the outer one; never inlined. */
    @Test
    void returnToAnOuterSubroutineIsRejected() {
        Violation v = singleViolation(JAVA_1_2, b -> {
            Label outer = b.newLabel();
            Label inner = b.newLabel();
            SubroutineShapes.jsr(b, outer);
            b.iconst_0().ireturn().labelBinding(outer).lineNumber(7).astore(1);
            SubroutineShapes.jsr(b, inner);
            SubroutineShapes.ret(b, 1);
            b.labelBinding(inner).lineNumber(8).astore(2);
            SubroutineShapes.ret(b, 1);
        });

        assertThat(v.message()).contains("ret at bci 12 uses local 1", "local 2", "outer subroutine");
        assertThat(v.bci()).isEqualTo(12);
        assertThat(v.line()).isEqualTo(8);
    }

    @Test
    void recursiveSubroutineIsRejected() {
        Violation v = singleViolation(JAVA_1_2, b -> {
            Label sub = b.newLabel();
            Label done = b.newLabel();
            SubroutineShapes.jsr(b, sub);
            b.iconst_0().ireturn().labelBinding(sub).astore(1).iload(0).ifeq(done);
            SubroutineShapes.jsr(b, sub);
            b.labelBinding(done);
            SubroutineShapes.ret(b, 1);
        });

        assertThat(v.message()).contains("jsr to itself");
        assertThat(v.bci()).isEqualTo(10);
    }

    /**
     * A subroutine whose exit leads into the code of a finally block that does not enclose one of its callers
     * could not be copied there (no compiler writes it: a finally block is called only from inside its own try
     * statement): rejected with the reason instead of a broken copy.
     */
    @Test
    void exitIntoAFinallyBlockThatDoesNotEncloseTheCallerIsRejected() {
        Violation v = singleViolation(JAVA_1_2, b -> {
            Label s1 = b.newLabel(), s3 = b.newLabel(), s4 = b.newLabel(), s = b.newLabel(), back = b.newLabel();
            SubroutineShapes.jsr(b, s1);
            SubroutineShapes.jsr(b, s3);
            b.iconst_0().ireturn().labelBinding(s1).astore(1);
            SubroutineShapes.jsr(b, s);
            b.labelBinding(back);
            SubroutineShapes.ret(b, 1);
            b.labelBinding(s3).astore(2);
            SubroutineShapes.jsr(b, s4);
            SubroutineShapes.ret(b, 2);
            b.labelBinding(s4).astore(3);
            SubroutineShapes.jsr(b, s);
            SubroutineShapes.ret(b, 3);
            b.labelBinding(s).astore(4).goto_(back);
        });

        assertThat(v.message()).contains("is called at bci", "not inside the code around it");
    }

    @Test
    void retOutsideAnySubroutineIsRejected() {
        Violation v = singleViolation(JAVA_1_2, b -> {
            b.aconst_null().astore(1);
            SubroutineShapes.ret(b, 1);
        });

        assertThat(v.message()).contains("ret at bci 2", "not inside a subroutine");
    }

    @Test
    void subroutineThatDoesNotStoreItsReturnAddressFirstIsRejected() {
        Violation v = singleViolation(JAVA_1_2, b -> {
            Label sub = b.newLabel();
            SubroutineShapes.jsr(b, sub);
            b.iconst_0().ireturn().labelBinding(sub).dup().astore(1).astore(2);
            SubroutineShapes.ret(b, 2);
        });

        assertThat(v.message()).contains("subroutine at bci 5", "astore");
    }

    @Test
    void overwrittenReturnAddressIsRejected() {
        Violation v = singleViolation(JAVA_1_2, b -> {
            Label sub = b.newLabel();
            SubroutineShapes.jsr(b, sub);
            b.iconst_0().ireturn().labelBinding(sub).astore(1).aconst_null().astore(1);
            SubroutineShapes.ret(b, 1);
        });

        assertThat(v.message()).contains("local 1", "return address of the subroutine at bci 5", "bci 7");
    }

    /** The nested subroutine keeps its return address in the outer one's local, so the outer ret goes astray. */
    @Test
    void nestedSubroutineOverwritingTheReturnAddressIsRejected() {
        Violation v = singleViolation(JAVA_1_2, b -> {
            Label outer = b.newLabel();
            Label inner = b.newLabel();
            SubroutineShapes.jsr(b, outer);
            b.iconst_0().ireturn().labelBinding(outer).astore(1);
            SubroutineShapes.jsr(b, inner);
            SubroutineShapes.ret(b, 1);
            b.labelBinding(inner).astore(1);
            SubroutineShapes.ret(b, 1);
        });

        assertThat(v.message()).contains("local 1", "return address of the subroutine at bci 5", "written at bci 6",
                "ret at bci 9");
    }

    @Test
    void jumpBackToTheSubroutineEntryIsRejected() {
        Violation v = singleViolation(JAVA_1_2, b -> {
            Label sub = b.newLabel();
            Label done = b.newLabel();
            SubroutineShapes.jsr(b, sub);
            b.iconst_0().ireturn().labelBinding(sub).astore(1).iload(0).ifeq(done).aconst_null().goto_(sub)
                    .labelBinding(done);
            SubroutineShapes.ret(b, 1);
        });

        assertThat(v.message()).contains("subroutine at bci 5", "jump");
    }

    /** JVMS 4.9.1: from version 51.0 on, a class file must not contain jsr, jsr_w or ret. */
    @Test
    void subroutinesInClassFilesOfVersion51AreRejected_JVMS_4_9_1() {
        Violation v = singleViolation(ClassFile.JAVA_7_VERSION, b -> {
            Label sub = b.newLabel();
            SubroutineShapes.jsr(b, sub);
            b.iconst_0().ireturn().labelBinding(sub).astore(1);
            SubroutineShapes.ret(b, 1);
        });

        assertThat(v.message()).contains("51.0", "JVMS 4.9.1");
    }

    /** 21 nested subroutines, each calling the next twice, would need 2^20 copies of the last one. */
    @Test
    void inliningThatOutgrowsAMethodIsRejected() {
        Violation v = singleViolation(JAVA_1_2, b -> {
            Label[] subs = new Label[21];
            for (int i = 0; i < subs.length; i++) {
                subs[i] = b.newLabel();
            }
            SubroutineShapes.jsr(b, subs[0]);
            b.iconst_0().ireturn();
            for (int i = 0; i < subs.length; i++) {
                b.labelBinding(subs[i]).astore(1 + i);
                if (i + 1 < subs.length) {
                    SubroutineShapes.jsr(b, subs[i + 1]);
                    SubroutineShapes.jsr(b, subs[i + 1]);
                }
                SubroutineShapes.ret(b, 1 + i);
            }
        });

        assertThat(v.message()).contains("instructions", "65535");
    }

    /**
     * Fewer instructions than a method can hold, but more than its 65535 bytes of code (JVMS 4.7.3) once copied
     * (a wide iinc takes 6 bytes): a violation naming the method, not a crash of the class file writer.
     */
    @Test
    void inliningThatOutgrowsTheCodeSizeIsRejected() {
        Violation v = singleViolation(JAVA_1_2, b -> {
            Label sub = b.newLabel();
            b.lineNumber(8).iconst_0().istore(2);
            for (int i = 0; i < 4; i++) {
                SubroutineShapes.jsr(b, sub);
            }
            b.iconst_0().ireturn().labelBinding(sub).lineNumber(9).astore(1);
            for (int i = 0; i < 3000; i++) {
                b.iinc(2, 1000);
            }
            SubroutineShapes.ret(b, 1);
        });

        assertThat(v.context()).isEqualTo("odd(S)S");
        assertThat(v.message()).contains("more than the 65535 bytes of code a method can hold (JVMS 4.7.3)",
                "JCVM 3.1 §2.2.4.4", "Code length", "split the method");
        assertThat(v.bci()).as("the first jsr").isEqualTo(2);
        assertThat(v.line()).isEqualTo(8);
    }

    @Test
    void violationsNameTheClassMethodAndSourceAndTellHowToRecompile() {
        Violation v = singleViolation(JAVA_1_2, b -> {
            b.lineNumber(42).aconst_null().astore(1);
            SubroutineShapes.ret(b, 1);
        });

        assertThat(v.className()).isEqualTo("probe/Fin");
        assertThat(v.context()).isEqualTo("odd(S)S");
        assertThat(v.sourceFile()).isEqualTo("Fin.java");
        assertThat(v.line()).isEqualTo(42);
        assertThat(v.message()).contains("JCVM 3.1 §2.3.2.2", "javac -target 1.6", "ECJ -target 1.5");
    }

    // ---- helpers

    private static byte[] inlineWithoutViolations(byte[] classFile) {
        List<Violation> violations = new ArrayList<>();
        byte[] inlined = SubroutineInliner.inline(classFile, violations);
        assertThat(violations).isEmpty();
        return inlined;
    }

    private static Violation singleViolation(int version, Consumer<CodeBuilder> body) {
        byte[] classFile = SubroutineShapes.finClass(version, List.of(new Shape("odd", body)));
        List<Violation> violations = new ArrayList<>();
        assertThat(SubroutineInliner.inline(classFile, violations)).isSameAs(classFile);
        assertThat(violations).hasSize(1);
        return violations.getFirst();
    }

    private static long subroutineInstructions(byte[] classFile) {
        return ClassFile.of().parse(classFile).methods().stream()
                .flatMap(m -> m.code().stream()).flatMap(c -> c.elementStream())
                .filter(e -> e instanceof DiscontinuedInstruction).count();
    }

    private static Set<Integer> lines(byte[] classFile, String method) {
        Set<Integer> lines = new TreeSet<>();
        for (CodeElement e : code(ClassFile.of().parse(classFile), method)) {
            if (e instanceof LineNumber ln) {
                lines.add(ln.line());
            }
        }
        return lines;
    }

    private static CodeAttribute code(ClassModel model, String method) {
        MethodModel mm = model.methods().stream().filter(m -> m.methodName().equalsString(method)).findFirst()
                .orElseThrow();
        return (CodeAttribute) mm.code().orElseThrow();
    }

    /** Runs {@code probe.Fin.method(m)} in a fresh class loader: result or exception, then the trace. */
    private static String run(byte[] classFile, String method, short m) throws ReflectiveOperationException {
        Class<?> fin = new Loader(classFile).loadClass("probe.Fin");
        Method target = fin.getMethod(method, short.class);
        String outcome;
        try {
            outcome = "returns " + target.invoke(null, m);
        } catch (InvocationTargetException e) {
            outcome = "throws " + e.getCause().getClass().getName();
        }
        return outcome + ", trace " + fin.getField("trace").getShort(null);
    }

    /** Defines {@code probe.Fin} from bytes; the JVM verifies it (type inference for version 49 and older). */
    private static final class Loader extends ClassLoader {
        private final byte[] classFile;

        Loader(byte[] classFile) {
            super(MethodHandles.lookup().lookupClass().getClassLoader());
            this.classFile = classFile;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!name.equals("probe.Fin")) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, classFile, 0, classFile.length);
        }
    }
}
