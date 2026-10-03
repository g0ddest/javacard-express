package name.velikodniy.jcexpress.converter;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.function.Consumer;

/**
 * Class files whose {@code finally} blocks are {@code jsr}/{@code ret} subroutines, written the way javac
 * 1.3 and ECJ with {@code -target} 1.4 or lower compile them (JVMS 4.10.2.5): the protected code calls the
 * subroutine with {@code jsr} before every exit, a catch-any handler stores the exception, calls the
 * subroutine and rethrows, and the subroutine stores its return address with {@code astore} and ends with
 * {@code ret}.
 *
 * <p>Every method is {@code public static short name(short m)} of {@code probe.Fin} and records what it did
 * in {@code public static short trace}; exceptions are {@code java.lang.ArithmeticException}, so the class
 * runs on the JVM and converts on the Java Card platform.
 */
final class SubroutineShapes {

    static final ClassDesc FIN = ClassDesc.of("probe.Fin");
    static final MethodTypeDesc SHORT_TO_SHORT = MethodTypeDesc.of(ConstantDescs.CD_short, ConstantDescs.CD_short);
    static final ClassDesc ARITHMETIC = ClassDesc.of("java.lang.ArithmeticException");

    /** A method of {@code probe.Fin}. */
    record Shape(String name, Consumer<CodeBuilder> body) {
        @Override
        public String toString() {
            return name;
        }
    }

    /** Shapes that the Java Card platform can represent (one-word locals, fewer than 256 locals). */
    static final List<Shape> JAVA_CARD = List.of(
            new Shape("tryFinally", SubroutineShapes::tryFinally),
            new Shape("returnInTry", SubroutineShapes::returnInTry),
            new Shape("nestedWithCatch", NestedSubroutineShapes::nestedWithCatch),
            new Shape("finallyInFinally", NestedSubroutineShapes::finallyInFinally),
            new Shape("finallyInFinallyCaught", NestedSubroutineShapes::finallyInFinallyCaught),
            new Shape("returnInFinally", SubroutineShapes::returnInFinally),
            new Shape("finallyNeverReturns", SubroutineShapes::finallyNeverReturns),
            new Shape("loopBreakContinue", SubroutineShapes::loopBreakContinue),
            new Shape("jumpOutOfFinally", SubroutineShapes::jumpOutOfFinally),
            new Shape("catchInFinally", SubroutineShapes::catchInFinally),
            new Shape("sequentialFinally", NestedSubroutineShapes::sequentialFinally),
            new Shape("returnAddressSlotReused", SubroutineShapes::returnAddressSlotReused),
            new Shape("callerTypedLocal", SubroutineShapes::callerTypedLocal),
            new Shape("switchInFinally", NestedSubroutineShapes::switchInFinally),
            new Shape("tryCatchInFinally", NestedSubroutineShapes::tryCatchInFinally),
            new Shape("continueInNestedFinally", NestedSubroutineShapes::continueInNestedFinally),
            new Shape("finallyFallsIntoTheCodeAfter", SubroutineShapes::finallyFallsIntoTheCodeAfter));

    /** {@code jsr_w} and a {@code wide ret}, possible in class files only (JCVM 3.1 §2.3.2.1, §2.3.2.3.4). */
    static final Shape WIDE_FORMS = new Shape("wideForms", SubroutineShapes::wideForms);

    private SubroutineShapes() {}

    /**
     * Writes {@code probe.Fin} with the given methods.
     *
     * @param version class file major version
     * @param shapes  methods
     * @return the class file
     */
    static byte[] finClass(int version, List<Shape> shapes) {
        return ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS).build(FIN, cb -> {
            cb.withVersion(version, 0).withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER)
                    .withField("trace", ConstantDescs.CD_short, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC)
                    .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC,
                            b -> b.aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME,
                                    ConstantDescs.MTD_void).return_());
            for (Shape shape : shapes) {
                cb.withMethodBody(shape.name(), SHORT_TO_SHORT, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        shape.body());
            }
            cb.with(SourceFileAttribute.of("Fin.java"));
        });
    }

    // ---- building blocks

    /** {@code trace = (short) (trace * 3 + k)}. */
    static void step(CodeBuilder b, int k) {
        b.getstatic(FIN, "trace", ConstantDescs.CD_short).iconst_3().imul().bipush(k).iadd().i2s()
                .putstatic(FIN, "trace", ConstantDescs.CD_short);
    }

    /** {@code if (m == value) throw new ArithmeticException();}. */
    static void throwIf(CodeBuilder b, int value) {
        Label skip = b.newLabel();
        b.iload(0).bipush(value).if_icmpne(skip)
                .new_(ARITHMETIC).dup().invokespecial(ARITHMETIC, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                .athrow().labelBinding(skip);
    }

    static void jsr(CodeBuilder b, Label subroutine) {
        b.with(DiscontinuedInstruction.JsrInstruction.of(subroutine));
    }

    static void ret(CodeBuilder b, int slot) {
        b.with(DiscontinuedInstruction.RetInstruction.of(slot));
    }

    /** Catch-any handler of a try/finally: store the exception, run the subroutine, rethrow. */
    static void rethrowHandler(CodeBuilder b, Label handler, int slot, Label subroutine) {
        b.labelBinding(handler).astore(slot);
        jsr(b, subroutine);
        b.aload(slot).athrow();
    }

    // ---- shapes

    /** {@code r = 1; try { step 1; throw if 1; r = 2; } finally { step 2; } return r;}. */
    private static void tryFinally(CodeBuilder b) {
        Label start = b.newLabel();
        Label end = b.newLabel();
        Label handler = b.newLabel();
        Label fin = b.newLabel();
        Label after = b.newLabel();
        b.lineNumber(10).iconst_1().istore(1).labelBinding(start).lineNumber(11);
        step(b, 1);
        throwIf(b, 1);
        b.lineNumber(12).iconst_2().istore(1).labelBinding(end);
        jsr(b, fin);
        b.goto_(after);
        rethrowHandler(b, handler, 2, fin);
        b.labelBinding(fin).lineNumber(14).astore(3);
        step(b, 2);
        ret(b, 3);
        b.labelBinding(after).lineNumber(16).iload(1).ireturn();
        b.exceptionCatchAll(start, end, handler);
    }

    /**
     * {@code try { if (m == 2) return 0x22; step 1; throw if 3; } finally { step 2; throw if 2; } return 0x33;}:
     * the exception of the subroutine called from the return path must not reach the handler of the try block,
     * whose range covers the call but not the subroutine.
     */
    private static void returnInTry(CodeBuilder b) {
        Label start = b.newLabel();
        Label notTwo = b.newLabel();
        Label end = b.newLabel();
        Label handler = b.newLabel();
        Label fin = b.newLabel();
        Label after = b.newLabel();
        b.labelBinding(start).lineNumber(20).iload(0).iconst_2().if_icmpne(notTwo).bipush(0x22).istore(1);
        jsr(b, fin);
        b.iload(1).ireturn().labelBinding(notTwo).lineNumber(21);
        step(b, 1);
        throwIf(b, 3);
        b.labelBinding(end);
        jsr(b, fin);
        b.goto_(after);
        rethrowHandler(b, handler, 2, fin);
        b.labelBinding(fin).lineNumber(23).astore(3);
        step(b, 2);
        throwIf(b, 2);
        ret(b, 3);
        b.labelBinding(after).lineNumber(25).bipush(0x33).ireturn();
        b.exceptionCatchAll(start, end, handler);
    }

    /** {@code try { throw if 1; r = 1; } finally { if (m != 0) return (short) (0x0F00 + m); } return r;}. */
    private static void returnInFinally(CodeBuilder b) {
        Label start = b.newLabel();
        Label end = b.newLabel();
        Label handler = b.newLabel();
        Label fin = b.newLabel();
        Label back = b.newLabel();
        b.labelBinding(start).lineNumber(60);
        throwIf(b, 1);
        b.iconst_1().istore(1).labelBinding(end);
        jsr(b, fin);
        b.iload(1).ireturn();
        rethrowHandler(b, handler, 2, fin);
        b.labelBinding(fin).lineNumber(62).astore(3).iload(0).ifeq(back)
                .sipush(0x0F00).iload(0).iadd().i2s().ireturn().labelBinding(back);
        ret(b, 3);
        b.exceptionCatchAll(start, end, handler);
    }

    /**
     * {@code try { throw if 1; step 1; } finally { return (short) (trace + 5); }}: the subroutine never
     * returns, so nothing follows its calls.
     */
    private static void finallyNeverReturns(CodeBuilder b) {
        Label start = b.newLabel();
        Label end = b.newLabel();
        Label handler = b.newLabel();
        Label fin = b.newLabel();
        b.labelBinding(start).lineNumber(70);
        throwIf(b, 1);
        step(b, 1);
        b.labelBinding(end);
        jsr(b, fin);
        b.labelBinding(handler).astore(1);
        jsr(b, fin);
        b.labelBinding(fin).lineNumber(72).astore(2).getstatic(FIN, "trace", ConstantDescs.CD_short)
                .iconst_5().iadd().i2s().ireturn();
        b.exceptionCatchAll(start, end, handler);
    }

    /**
     * {@code for (short i = 0; i < m; i++) { try { if (i == 2) continue; if (i == 4) break; acc += i; }
     * finally { acc += 0x10; } } return acc;}.
     */
    private static void loopBreakContinue(CodeBuilder b) {
        Label cond = b.newLabel();
        Label start = b.newLabel();
        Label notTwo = b.newLabel();
        Label notFour = b.newLabel();
        Label end = b.newLabel();
        Label handler = b.newLabel();
        Label fin = b.newLabel();
        Label inc = b.newLabel();
        Label done = b.newLabel();
        b.lineNumber(80).iconst_0().istore(1).iconst_0().istore(2)
                .labelBinding(cond).iload(2).iload(0).if_icmpge(done)
                .labelBinding(start).lineNumber(81).iload(2).iconst_2().if_icmpne(notTwo);
        jsr(b, fin);
        b.goto_(inc).labelBinding(notTwo).iload(2).iconst_4().if_icmpne(notFour);
        jsr(b, fin);
        b.goto_(done).labelBinding(notFour).iload(1).iload(2).iadd().i2s().istore(1).labelBinding(end);
        jsr(b, fin);
        b.goto_(inc);
        rethrowHandler(b, handler, 3, fin);
        b.labelBinding(fin).lineNumber(84).astore(4).iload(1).bipush(0x10).iadd().i2s().istore(1);
        ret(b, 4);
        b.labelBinding(inc).iload(2).iconst_1().iadd().i2s().istore(2).goto_(cond)
                .labelBinding(done).lineNumber(86).iload(1).ireturn();
        b.exceptionCatchAll(start, end, handler);
    }

    /**
     * {@code for (short i = 0; i < 6; i++) { try { if (i == m) throw; acc += i; } finally { acc += 0x10; if (i
     * == 2) continue; if (i == 4) break; } } return acc;}: the subroutine leaves for the loop without ret (and
     * drops a pending exception), and the loop calls it again.
     */
    private static void jumpOutOfFinally(CodeBuilder b) {
        Label cond = b.newLabel();
        Label start = b.newLabel();
        Label noThrow = b.newLabel();
        Label end = b.newLabel();
        Label handler = b.newLabel();
        Label fin = b.newLabel();
        Label notTwo = b.newLabel();
        Label notFour = b.newLabel();
        Label inc = b.newLabel();
        Label done = b.newLabel();
        b.lineNumber(150).iconst_0().istore(1).iconst_0().istore(2)
                .labelBinding(cond).iload(2).bipush(6).if_icmpge(done)
                .labelBinding(start).lineNumber(151).iload(2).iload(0).if_icmpne(noThrow)
                .new_(ARITHMETIC).dup().invokespecial(ARITHMETIC, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                .athrow().labelBinding(noThrow).iload(1).iload(2).iadd().i2s().istore(1).labelBinding(end);
        jsr(b, fin);
        b.goto_(inc);
        rethrowHandler(b, handler, 3, fin);
        b.labelBinding(fin).lineNumber(155).astore(4).iload(1).bipush(0x10).iadd().i2s().istore(1)
                .iload(2).iconst_2().if_icmpne(notTwo).goto_(inc)
                .labelBinding(notTwo).iload(2).iconst_4().if_icmpne(notFour).goto_(done)
                .labelBinding(notFour);
        ret(b, 4);
        b.labelBinding(inc).iload(2).iconst_1().iadd().i2s().istore(2).goto_(cond)
                .labelBinding(done).lineNumber(160).iload(1).ireturn();
        b.exceptionCatchAll(start, end, handler);
    }

    /** {@code try { step 1; } finally { try { throw if 5; } catch (ArithmeticException e) { step 2; } }}. */
    private static void catchInFinally(CodeBuilder b) {
        Label start = b.newLabel();
        Label end = b.newLabel();
        Label handler = b.newLabel();
        Label fin = b.newLabel();
        Label tryStart = b.newLabel();
        Label tryEnd = b.newLabel();
        Label caught = b.newLabel();
        Label finEnd = b.newLabel();
        Label after = b.newLabel();
        b.labelBinding(start).lineNumber(90);
        step(b, 1);
        b.labelBinding(end);
        jsr(b, fin);
        b.goto_(after);
        rethrowHandler(b, handler, 1, fin);
        b.labelBinding(fin).lineNumber(92).astore(2).labelBinding(tryStart);
        throwIf(b, 5);
        b.labelBinding(tryEnd).goto_(finEnd).labelBinding(caught).lineNumber(94).astore(3);
        step(b, 2);
        b.labelBinding(finEnd);
        ret(b, 2);
        b.labelBinding(after).lineNumber(96).getstatic(FIN, "trace", ConstantDescs.CD_short).ireturn();
        b.exceptionCatchAll(start, end, handler);
        b.exceptionCatch(tryStart, tryEnd, caught, ARITHMETIC);
    }

    /**
     * {@code try { step 1; } finally { step 2; } short s = (short) (m + 5); return s;} with {@code s} in the local
     * that held the return address: after inlining it holds {@code null}, then a {@code short}.
     */
    private static void returnAddressSlotReused(CodeBuilder b) {
        Label start = b.newLabel();
        Label end = b.newLabel();
        Label handler = b.newLabel();
        Label fin = b.newLabel();
        Label after = b.newLabel();
        b.labelBinding(start).lineNumber(170);
        step(b, 1);
        b.labelBinding(end);
        jsr(b, fin);
        b.goto_(after);
        rethrowHandler(b, handler, 1, fin);
        b.labelBinding(fin).astore(2);
        step(b, 2);
        ret(b, 2);
        b.labelBinding(after).lineNumber(173).iload(0).iconst_5().iadd().i2s().istore(2).iload(2).ireturn();
        b.exceptionCatchAll(start, end, handler);
    }

    /**
     * One subroutine called where local 4 holds a {@code short} and where it holds a reference: the
     * subroutine leaves local 4 alone, so each caller finds its own value after the call (JVMS 4.10.2.5).
     */
    private static void callerTypedLocal(CodeBuilder b) {
        Label other = b.newLabel();
        Label nonNull = b.newLabel();
        Label sub = b.newLabel();
        b.lineNumber(110).iload(0).ifeq(other).iconst_5().istore(4);
        jsr(b, sub);
        b.iload(4).iconst_1().iadd().i2s().ireturn()
                .labelBinding(other).aconst_null().astore(4);
        jsr(b, sub);
        b.aload(4).ifnonnull(nonNull).bipush(9).ireturn().labelBinding(nonNull).iconst_0().ireturn();
        b.labelBinding(sub).lineNumber(115).astore(3);
        step(b, 1);
        ret(b, 3);
    }

    /**
     * {@code try { step 1; throw if 4; } finally { step 2; if (m == 4) <leave for the code after the try statement>;
     * } step 5; return trace;} with the leaving branch falling through: the subroutine's last instruction, a
     * conditional branch to its ret, is followed by the code after the try statement, so the subroutine leaves by
     * falling through into it (and drops the pending exception when it was called from the handler).
     */
    private static void finallyFallsIntoTheCodeAfter(CodeBuilder b) {
        Label start = b.newLabel();
        Label end = b.newLabel();
        Label handler = b.newLabel();
        Label back = b.newLabel();
        Label fin = b.newLabel();
        Label after = b.newLabel();
        b.labelBinding(start).lineNumber(180);
        step(b, 1);
        throwIf(b, 4);
        b.labelBinding(end);
        jsr(b, fin);
        b.goto_(after);
        rethrowHandler(b, handler, 2, fin);
        b.labelBinding(back);
        ret(b, 3);
        b.labelBinding(fin).lineNumber(182).astore(3);
        step(b, 2);
        b.iload(0).iconst_4().if_icmpne(back)
                .labelBinding(after).lineNumber(184);
        step(b, 5);
        b.getstatic(FIN, "trace", ConstantDescs.CD_short).ireturn();
        b.exceptionCatchAll(start, end, handler);
    }

    /** {@link #tryFinally} with {@code jsr_w} calls and the return address in local 300 ({@code wide ret}). */
    private static void wideForms(CodeBuilder b) {
        Label start = b.newLabel();
        Label end = b.newLabel();
        Label handler = b.newLabel();
        Label fin = b.newLabel();
        Label after = b.newLabel();
        b.iconst_1().istore(1).labelBinding(start);
        step(b, 1);
        throwIf(b, 1);
        b.iconst_2().istore(1).labelBinding(end)
                .with(DiscontinuedInstruction.JsrInstruction.of(Opcode.JSR_W, fin));
        b.goto_w(after).labelBinding(handler).astore(2)
                .with(DiscontinuedInstruction.JsrInstruction.of(Opcode.JSR_W, fin));
        b.aload(2).athrow().labelBinding(fin).astore(300);
        step(b, 2);
        ret(b, 300);
        b.labelBinding(after).iload(1).ireturn();
        b.exceptionCatchAll(start, end, handler);
    }
}
