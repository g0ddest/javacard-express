package name.velikodniy.jcexpress.converter;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.constant.ConstantDescs;
import java.util.List;

import static name.velikodniy.jcexpress.converter.SubroutineShapes.ARITHMETIC;
import static name.velikodniy.jcexpress.converter.SubroutineShapes.FIN;
import static name.velikodniy.jcexpress.converter.SubroutineShapes.jsr;
import static name.velikodniy.jcexpress.converter.SubroutineShapes.ret;
import static name.velikodniy.jcexpress.converter.SubroutineShapes.rethrowHandler;
import static name.velikodniy.jcexpress.converter.SubroutineShapes.step;
import static name.velikodniy.jcexpress.converter.SubroutineShapes.throwIf;

/**
 * The {@link SubroutineShapes} with several subroutines in one method: nested {@code finally} blocks in the
 * layouts of ECJ and javac 1.3, sequential ones that share a return address local, and a {@code finally} block
 * with switches.
 */
final class NestedSubroutineShapes {

    private NestedSubroutineShapes() {}

    /**
     * ECJ's layout of {@code try { try { A } finally { F1 } B } catch (ArithmeticException e) { C } finally
     * { F2 }}: the inner subroutine lies inside the outer protected ranges, so the outer handlers are also
     * reached from it, and the catch handler stores its exception in the inner subroutine's return address
     * local.
     */
    static void nestedWithCatch(CodeBuilder b) {
        Label outerStart = b.newLabel(), innerEnd = b.newLabel(), notTwo = b.newLabel(), innerHandler = b.newLabel();
        Label f1 = b.newLabel(), innerNormal = b.newLabel(), outerEnd = b.newLabel(), catchHandler = b.newLabel();
        Label anyHandler = b.newLabel(), f2 = b.newLabel(), f2Normal = b.newLabel();
        b.lineNumber(30).iconst_0().istore(1).labelBinding(outerStart).iconst_1().istore(1);
        throwIf(b, 1);
        b.iload(0).iconst_2().if_icmpne(notTwo);
        jsr(b, f1);
        jsr(b, f2);
        b.bipush(0x22).ireturn().labelBinding(notTwo).iconst_2().istore(1).labelBinding(innerEnd).goto_(innerNormal);
        rethrowHandler(b, innerHandler, 4, f1);
        nestedInnerFinally(b, f1);
        b.labelBinding(innerNormal);
        jsr(b, f1);
        b.lineNumber(36).iload(1).sipush(0x100).ior().i2s().istore(1).labelBinding(outerEnd).goto_(f2Normal);
        b.labelBinding(catchHandler).lineNumber(38).astore(3).bipush(7).istore(1);
        step(b, 2);
        b.goto_(f2Normal);
        rethrowHandler(b, anyHandler, 6, f2);
        nestedOuterFinally(b, f2);
        b.labelBinding(f2Normal);
        jsr(b, f2);
        b.iload(1).ireturn();
        b.exceptionCatchAll(outerStart, innerEnd, innerHandler);
        b.exceptionCatch(outerStart, outerEnd, catchHandler, ARITHMETIC);
        b.exceptionCatchAll(outerStart, anyHandler, anyHandler);
    }

    /** F1 of {@link #nestedWithCatch}: {@code r |= 0x10; step 1; throw if 3}. */
    private static void nestedInnerFinally(CodeBuilder b, Label f1) {
        b.labelBinding(f1).lineNumber(33).astore(3).iload(1).bipush(0x10).ior().i2s().istore(1);
        step(b, 1);
        throwIf(b, 3);
        ret(b, 3);
    }

    /** F2 of {@link #nestedWithCatch}: {@code step 0; r |= 0x4000}. */
    private static void nestedOuterFinally(CodeBuilder b, Label f2) {
        b.labelBinding(f2).lineNumber(41).astore(5);
        step(b, 0);
        b.iload(1).sipush(0x4000).ior().i2s().istore(1);
        ret(b, 5);
    }

    /** {@code try { step 1; } finally { try { step 2; throw if 4; } finally { step 3; } } return trace;}. */
    static void finallyInFinally(CodeBuilder b) {
        Label start = b.newLabel(), end = b.newLabel(), handler = b.newLabel(), outer = b.newLabel();
        Label innerStart = b.newLabel(), innerEnd = b.newLabel(), innerHandler = b.newLabel(), inner = b.newLabel();
        Label outerEnd = b.newLabel(), after = b.newLabel();
        b.labelBinding(start).lineNumber(50);
        step(b, 1);
        b.labelBinding(end);
        jsr(b, outer);
        b.goto_(after);
        rethrowHandler(b, handler, 1, outer);
        b.labelBinding(outer).lineNumber(52).astore(2).labelBinding(innerStart);
        step(b, 2);
        throwIf(b, 4);
        b.labelBinding(innerEnd);
        jsr(b, inner);
        b.goto_(outerEnd);
        rethrowHandler(b, innerHandler, 3, inner);
        b.labelBinding(inner).lineNumber(55).astore(4);
        step(b, 3);
        ret(b, 4);
        b.labelBinding(outerEnd);
        ret(b, 2);
        b.labelBinding(after).lineNumber(57).getstatic(FIN, "trace", ConstantDescs.CD_short).ireturn();
        b.exceptionCatchAll(start, end, handler);
        b.exceptionCatchAll(innerStart, innerEnd, innerHandler);
    }

    /**
     * ECJ's layout of {@code try { try { r = 1; throw if 1; } finally { r |= 2; try { throw if 2; r |= 4; }
     * finally { r |= 8; step 1; } } } catch (ArithmeticException e) { r |= 0x100; } return r;}: the catch range
     * covers both subroutines, and its handler stores the exception in the local that holds the outer
     * subroutine's return address. The handler never returns from a subroutine, so the write is harmless.
     */
    static void finallyInFinallyCaught(CodeBuilder b) {
        Label start = b.newLabel(), tryEnd = b.newLabel(), anyOuter = b.newLabel(), outer = b.newLabel();
        Label innerStart = b.newLabel(), innerEnd = b.newLabel(), anyInner = b.newLabel(), inner = b.newLabel();
        Label callInner = b.newLabel(), retOuter = b.newLabel(), afterTry = b.newLabel(), catchEnd = b.newLabel();
        Label caught = b.newLabel(), done = b.newLabel();
        b.lineNumber(130).iconst_0().istore(1).labelBinding(start).iconst_1().istore(1);
        throwIf(b, 1);
        b.labelBinding(tryEnd).goto_(afterTry);
        rethrowHandler(b, anyOuter, 3, outer);
        b.labelBinding(outer).lineNumber(133).astore(2).iload(1).iconst_2().ior().i2s().istore(1)
                .labelBinding(innerStart);
        throwIf(b, 2);
        b.iload(1).iconst_4().ior().i2s().istore(1).labelBinding(innerEnd).goto_(callInner);
        rethrowHandler(b, anyInner, 5, inner);
        caughtInnerFinally(b, inner);
        b.labelBinding(callInner);
        jsr(b, inner);
        b.labelBinding(retOuter);
        ret(b, 2);
        b.labelBinding(afterTry);
        jsr(b, outer);
        b.labelBinding(catchEnd).goto_(done)
                .labelBinding(caught).lineNumber(141).astore(2).iload(1).sipush(0x100).ior().i2s().istore(1)
                .labelBinding(done).iload(1).ireturn();
        b.exceptionCatchAll(start, tryEnd, anyOuter);
        b.exceptionCatchAll(afterTry, catchEnd, anyOuter);
        b.exceptionCatchAll(innerStart, innerEnd, anyInner);
        b.exceptionCatchAll(callInner, retOuter, anyInner);
        b.exceptionCatch(start, catchEnd, caught, ARITHMETIC);
    }

    /** Inner subroutine of {@link #finallyInFinallyCaught}: {@code r |= 8; step 1}. */
    private static void caughtInnerFinally(CodeBuilder b, Label inner) {
        b.labelBinding(inner).lineNumber(137).astore(4).iload(1).bipush(8).ior().i2s().istore(1);
        step(b, 1);
        ret(b, 4);
    }

    /** Two try/finally statements in a row whose subroutines keep their return address in local 3. */
    static void sequentialFinally(CodeBuilder b) {
        Label s1 = b.newLabel(), e1 = b.newLabel(), h1 = b.newLabel(), f1 = b.newLabel();
        Label s2 = b.newLabel(), e2 = b.newLabel(), h2 = b.newLabel(), f2 = b.newLabel(), after = b.newLabel();
        b.labelBinding(s1).lineNumber(100);
        step(b, 1);
        b.labelBinding(e1);
        jsr(b, f1);
        b.goto_(s2);
        rethrowHandler(b, h1, 2, f1);
        b.labelBinding(f1).astore(3);
        step(b, 2);
        ret(b, 3);
        b.labelBinding(s2).lineNumber(104);
        throwIf(b, 1);
        step(b, 3);
        b.labelBinding(e2);
        jsr(b, f2);
        b.goto_(after);
        rethrowHandler(b, h2, 2, f2);
        b.labelBinding(f2).astore(3);
        step(b, 4);
        ret(b, 3);
        b.labelBinding(after).getstatic(FIN, "trace", ConstantDescs.CD_short).ireturn();
        b.exceptionCatchAll(s1, e1, h1);
        b.exceptionCatchAll(s2, e2, h2);
    }

    /** A tableswitch and a lookupswitch in a finally block. */
    static void switchInFinally(CodeBuilder b) {
        Label start = b.newLabel(), end = b.newLabel(), handler = b.newLabel(), fin = b.newLabel();
        Label after = b.newLabel(), lookup = b.newLabel(), back = b.newLabel();
        Label[] cases = {b.newLabel(), b.newLabel(), b.newLabel()};
        Label[] keys = {b.newLabel(), b.newLabel()};
        b.labelBinding(start).lineNumber(120);
        throwIf(b, 6);
        b.labelBinding(end);
        jsr(b, fin);
        b.goto_(after);
        rethrowHandler(b, handler, 1, fin);
        b.labelBinding(fin).lineNumber(122).astore(2).iload(0).tableswitch(0, 2, lookup, List.of(
                SwitchCase.of(0, cases[0]), SwitchCase.of(1, cases[1]), SwitchCase.of(2, cases[2])));
        stepsTo(b, cases, 4, back);
        b.labelBinding(lookup).iload(0).lookupswitch(back, List.of(SwitchCase.of(6, keys[0]),
                SwitchCase.of(100, keys[1])));
        stepsTo(b, keys, 8, back);
        b.labelBinding(back);
        ret(b, 2);
        b.labelBinding(after).lineNumber(130).getstatic(FIN, "trace", ConstantDescs.CD_short).ireturn();
        b.exceptionCatchAll(start, end, handler);
    }

    /**
     * ECJ's layout (-target 1.2 to 1.4) of {@code try { step 1; } finally { try { try { step 2; throw if 1; }
     * finally { step 3; throw if 2; } } catch (ArithmeticException e) { step 4; } } return trace;}: the inner
     * subroutine lies inside the range of the catch, whose handler is code of the outer subroutine. An exception
     * of the inner subroutine leaves it for the outer one's handler, without its ret.
     */
    static void tryCatchInFinally(CodeBuilder b) {
        Label start = b.newLabel(), end = b.newLabel(), anyOuter = b.newLabel(), outer = b.newLabel();
        Label innerStart = b.newLabel(), innerEnd = b.newLabel(), anyInner = b.newLabel(), inner = b.newLabel();
        Label callInner = b.newLabel(), callEnd = b.newLabel(), caught = b.newLabel(), retOuter = b.newLabel();
        Label exit = b.newLabel();
        b.labelBinding(start).lineNumber(190);
        step(b, 1);
        b.labelBinding(end).goto_(exit);
        rethrowHandler(b, anyOuter, 3, outer);
        b.labelBinding(outer).lineNumber(192).astore(2).labelBinding(innerStart);
        step(b, 2);
        throwIf(b, 1);
        b.goto_(callInner).labelBinding(innerEnd);
        rethrowHandler(b, anyInner, 5, inner);
        b.labelBinding(inner).lineNumber(195).astore(4);
        step(b, 3);
        throwIf(b, 2);
        ret(b, 4);
        b.labelBinding(callInner);
        jsr(b, inner);
        b.labelBinding(callEnd).goto_(retOuter).labelBinding(caught).lineNumber(197).pop();
        step(b, 4);
        b.labelBinding(retOuter);
        ret(b, 2);
        b.labelBinding(exit);
        jsr(b, outer);
        b.lineNumber(199).getstatic(FIN, "trace", ConstantDescs.CD_short).ireturn();
        b.exceptionCatchAll(start, end, anyOuter);
        b.exceptionCatchAll(innerStart, innerEnd, anyInner);
        b.exceptionCatchAll(callInner, callEnd, anyInner);
        b.exceptionCatch(innerStart, callEnd, caught, ARITHMETIC);
    }

    /**
     * {@code try { } finally { for (short i = 0; i < 4; i++) { step 1; try { } finally { step 3; if (i == m)
     * continue; } step 2; } } return trace;}: the {@code continue} leaves the inner subroutine for the loop of the
     * outer one, which calls the inner subroutine again.
     */
    static void continueInNestedFinally(CodeBuilder b) {
        Label outer = b.newLabel(), cond = b.newLabel(), inc = b.newLabel(), outerEnd = b.newLabel();
        Label inner = b.newLabel(), back = b.newLabel();
        b.lineNumber(210);
        jsr(b, outer);
        b.getstatic(FIN, "trace", ConstantDescs.CD_short).ireturn()
                .labelBinding(outer).lineNumber(211).astore(1).iconst_0().istore(2)
                .labelBinding(cond).iload(2).iconst_4().if_icmpge(outerEnd);
        step(b, 1);
        jsr(b, inner);
        step(b, 2);
        b.labelBinding(inc).iload(2).iconst_1().iadd().i2s().istore(2).goto_(cond).labelBinding(outerEnd);
        ret(b, 1);
        b.labelBinding(inner).lineNumber(214).astore(3);
        step(b, 3);
        b.iload(2).iload(0).if_icmpne(back).goto_(inc).labelBinding(back);
        ret(b, 3);
    }

    /** At each label {@code step first + i}, then a jump to {@code to}. */
    private static void stepsTo(CodeBuilder b, Label[] labels, int first, Label to) {
        for (int i = 0; i < labels.length; i++) {
            b.labelBinding(labels[i]);
            step(b, first + i);
            b.goto_(to);
        }
    }
}
