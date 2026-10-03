package name.velikodniy.jcexpress.converter;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction.JsrInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction.RetInstruction;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LocalVariableType;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes the code of a method with its subroutines inlined: the method's own body with, at every
 * {@code jsr}, a copy of the subroutine body it runs, as javac and ECJ place {@code finally} code from
 * {@code -target} 1.5 on. In a copy, {@code jsr} becomes {@code aconst_null} followed by the copy (the
 * subroutine's {@code astore} then stores {@code null} where the return address went) and {@code ret}
 * becomes {@code goto <instruction after the jsr in the calling copy>} (JCVM 3.1 §7.5.69, §7.5.79). Control
 * that leaves a subroutine for the code of a level around it (a jump, the next instruction or an exception
 * handler that is an exit of the body, see {@link Subroutines}) continues in the nearest enclosing copy that
 * holds that code: the calling subroutine's copy, or the method's own body.
 *
 * <p>Exception table entries and local variable scopes are given to each copy for the members it holds, in
 * the original order (the order is the handler priority, JVMS 2.10), one entry per run of members that no
 * nested copy interrupts: a range never covers a nested copy unless the original range covers the subroutine's
 * own code. Because a nested copy lies inside the code of its caller, before the handlers that protect the
 * caller, handlers keep their priority when the Method component sorts them by handler offset (JCVM 3.1
 * §6.10.1). Source lines are kept; the {@code StackMapTable} is not written (JCVM 3.1 §2.3.1.2.7 does not use
 * it, and the class files that contain subroutines are older than it).
 */
final class SubroutineCopies {

    /** Members of a copy written one after the other, from {@code first} to {@code last}; {@code end} follows. */
    private record Run(int first, int last, Label end) {}

    /** One copy of a body: the method's own body, or a subroutine run by a {@code jsr} of its parent copy. */
    private final class Copy {
        final Subroutines.Body body;
        final Copy parent;
        final int callSite;
        final Map<Integer, Label> labels = new HashMap<>();
        final List<Run> runs = new ArrayList<>();

        Copy(Subroutines.Body body, Copy parent, int callSite) {
            this.body = body;
            this.parent = parent;
            this.callSite = callSite;
        }

        /**
         * The label of an instruction as this copy reaches it: its own copy of the instruction, or else the copy
         * of the nearest enclosing copy that holds it (the code of a level around this body, where an exit
         * leads).
         */
        Label label(int index) {
            for (Copy copy = this; copy != null; copy = copy.parent) {
                if (copy.body.members.get(index)) {
                    Copy holder = copy;
                    return holder.labels.computeIfAbsent(index, i -> out.newLabel());
                }
            }
            throw new IllegalStateException("instruction " + index + " is in no enclosing copy");
        }
    }

    private final IndexedCode code;
    private final Subroutines subroutines;
    private final CodeBuilder out;
    private final Copy mainCopy;
    private final List<Copy> copies = new ArrayList<>();

    private SubroutineCopies(IndexedCode code, Subroutines subroutines, CodeBuilder out) {
        this.code = code;
        this.subroutines = subroutines;
        this.out = out;
        this.mainCopy = new Copy(subroutines.main(), null, -1);
    }

    /**
     * Writes the inlined code of a method.
     *
     * @param code        the method's code
     * @param subroutines its checked subroutines
     * @param out         receives the new code
     */
    static void write(IndexedCode code, Subroutines subroutines, CodeBuilder out) {
        new SubroutineCopies(code, subroutines, out).write();
    }

    private void write() {
        writeCopy(mainCopy);
        for (IndexedCode.Catch c : code.catches()) {
            copies.forEach(copy -> writeCatch(copy, c));
        }
        for (IndexedCode.Scope s : code.scopes()) {
            copies.forEach(copy -> writeScope(copy, s));
        }
    }

    /** Writes the members of a copy in code order, each called subroutine's copy right after its jsr. */
    private void writeCopy(Copy copy) {
        copies.add(copy);
        int runFirst = -1;
        int line = -1;
        for (int i = copy.body.members.nextSetBit(0); i >= 0; i = copy.body.members.nextSetBit(i + 1)) {
            if (runFirst < 0) {
                runFirst = i;
                line = -1;
            }
            out.labelBinding(copy.label(i));
            if (code.line(i) >= 0 && code.line(i) != line) {
                line = code.line(i);
                out.lineNumber(line);
            }
            if (code.at(i) instanceof JsrInstruction) {
                Copy callee = call(copy, i);
                copy.runs.add(new Run(runFirst, i, out.newBoundLabel()));
                runFirst = -1;
                writeCopy(callee);
            } else {
                writeInstruction(copy, i);
            }
        }
        if (runFirst >= 0) {
            copy.runs.add(new Run(runFirst, copy.body.members.length() - 1, out.newBoundLabel()));
        }
    }

    /** {@code jsr}: {@code aconst_null} for the return address, then the copy (a jump if it does not start there). */
    private Copy call(Copy copy, int jsr) {
        Copy callee = new Copy(subroutines.callee(jsr), copy, jsr);
        out.aconst_null();
        if (callee.body.members.nextSetBit(0) != callee.body.entry) {
            out.goto_(callee.label(callee.body.entry));
        }
        return callee;
    }

    private void writeInstruction(Copy copy, int i) {
        Instruction insn = code.at(i);
        switch (insn) {
            case RetInstruction ret -> out.goto_(copy.parent.label(copy.callSite + 1));
            case BranchInstruction b -> out.branch(b.opcode(), copy.label(code.index(b.target())));
            case TableSwitchInstruction t -> out.tableswitch(t.lowValue(), t.highValue(),
                    copy.label(code.index(t.defaultTarget())), cases(copy, t.cases()));
            case LookupSwitchInstruction l -> out.lookupswitch(copy.label(code.index(l.defaultTarget())),
                    cases(copy, l.cases()));
            default -> out.with(insn);
        }
        if (fallsThrough(insn) && !copy.body.members.get(i + 1)) {
            out.goto_(copy.label(i + 1));  // the next instruction is code of a level around: an exit of the body
        }
    }

    /** Whether control can continue with the next instruction (JVMS 6.5); {@code jsr} is handled apart. */
    private static boolean fallsThrough(Instruction insn) {
        return switch (insn) {
            case RetInstruction r -> false;
            case ReturnInstruction r -> false;
            case ThrowInstruction t -> false;
            case TableSwitchInstruction t -> false;
            case LookupSwitchInstruction l -> false;
            case BranchInstruction b -> b.opcode() != Opcode.GOTO && b.opcode() != Opcode.GOTO_W;
            default -> true;
        };
    }

    private List<SwitchCase> cases(Copy copy, List<SwitchCase> cases) {
        return cases.stream().map(c -> SwitchCase.of(c.caseValue(), copy.label(code.index(c.target())))).toList();
    }

    private void writeCatch(Copy copy, IndexedCode.Catch c) {
        for (Run run : copy.runs) {
            Label[] range = range(copy, run, c.start(), c.end());
            if (range != null) {
                out.exceptionCatch(range[0], range[1], copy.label(c.handler()), c.catchType());
            }
        }
    }

    private void writeScope(Copy copy, IndexedCode.Scope s) {
        for (Run run : copy.runs) {
            Label[] range = range(copy, run, s.start(), s.end());
            if (range == null) {
                continue;
            }
            switch (s.entry()) {
                case LocalVariable lv -> out.localVariable(lv.slot(), lv.name(), lv.type(), range[0], range[1]);
                case LocalVariableType lt -> out.localVariableType(lt.slot(), lt.name(), lt.signature(), range[0],
                        range[1]);
                default -> throw new IllegalStateException("not a local variable entry: " + s.entry());
            }
        }
    }

    /** Start and end label of the members of a run in {@code [start, end)}, or {@code null} if there are none. */
    private static Label[] range(Copy copy, Run run, int start, int end) {
        int first = copy.body.members.nextSetBit(Math.max(run.first(), start));
        if (first < 0 || first > run.last() || first >= end) {
            return null;
        }
        int last = copy.body.members.previousSetBit(Math.min(run.last(), end - 1));
        Label after = last == run.last() ? run.end() : copy.label(copy.body.members.nextSetBit(last + 1));
        return new Label[]{copy.label(first), after};
    }
}
