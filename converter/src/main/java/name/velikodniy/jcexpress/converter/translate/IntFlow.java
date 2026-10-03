package name.velikodniy.jcexpress.converter.translate;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data flow of the int-typed and reference values of one JVM method, built by abstract
 * interpretation (one pass over the reachable instructions, phi values at join points and
 * exception handler entries), followed by a fixpoint computation of the {@link ValueRange}s.
 *
 * <p>The result tells for every reachable instruction which values it consumes and produces;
 * {@link IntRules} uses it to enforce JCVM 3.1 §2.2.3.1.
 */
final class IntFlow {

    /**
     * What one reachable instruction consumes and produces.
     *
     * @param operands     values popped (in stack order, bottom first) or the local read by
     *                     iinc
     * @param results      values pushed (bottom first) or the local written by iinc
     * @param localsBefore local variables before the instruction
     * @param stored       the value a store or iinc writes into its local variable, or
     *                     {@code null}
     */
    record Step(List<FlowValue> operands, List<FlowValue> results, FlowValue[] localsBefore,
                FlowValue stored) {}

    private final MethodModel method;
    private final CodeModel code;
    private final List<Instruction> insns = new ArrayList<>();
    private final List<Integer> bcis = new ArrayList<>();
    private final List<Integer> lines = new ArrayList<>();
    private final Map<Label, Integer> labelIndex = new IdentityHashMap<>();
    private final List<FlowValue> values = new ArrayList<>();
    private final List<ExceptionCatch> catches;
    private final int maxLocals;
    private int codeLength;
    private FlowFrame[] inStates;
    private boolean[] joins;
    private Step[] steps;
    private final Map<Integer, FlowValue> exceptionValues = new HashMap<>();
    private FlowValue undefined;

    private IntFlow(MethodModel method, CodeModel code) {
        this.method = method;
        this.code = code;
        this.catches = code.exceptionHandlers();
        this.maxLocals = code instanceof CodeAttribute ca ? ca.maxLocals() : 0;
        index(code);
    }

    /**
     * Builds the data flow of a method with code.
     *
     * @param method method with a Code attribute
     * @return the analysed flow
     * @throws IllegalStateException if the code is not well-formed enough to be analysed
     */
    static IntFlow analyze(MethodModel method) {
        IntFlow flow = new IntFlow(method, method.code().orElseThrow());
        flow.interpret();
        flow.computeRanges();
        return flow;
    }

    int size() {
        return insns.size();
    }

    Instruction instruction(int i) {
        return insns.get(i);
    }

    /** JVM bytecode index of instruction {@code i}. */
    int bci(int i) {
        return bcis.get(i);
    }

    /** Source line of instruction {@code i}, or -1. */
    int line(int i) {
        return lines.get(i);
    }

    /** Step of instruction {@code i}, or {@code null} if it is unreachable. */
    Step step(int i) {
        return steps[i];
    }

    /** Operand stack (bottom first) before instruction {@code i}, or {@code null} if unreachable. */
    List<FlowValue> stackBefore(int i) {
        return inStates[i] == null ? null : inStates[i].stack();
    }

    /**
     * Index of the instruction a label is bound to ({@link #size()} for the end of the code).
     *
     * <p>The JDK ClassFile API derives the branch targets it delivers as {@code Label} elements from
     * the StackMapTable; a class file of version 51 or later without one (ProGuard
     * {@code -dontpreverify}, ASM {@code COMPUTE_MAXS}; JCVM 3.1 §2.3.1.2.7 does not require it) has
     * no element for them, so their position comes from {@link CodeAttribute#labelToBci}.
     *
     * @throws IllegalStateException if the label is not bound to the start of an instruction
     */
    int indexOf(Label label) {
        Integer index = labelIndex.get(label);
        if (index == null) {
            index = indexAtBytecodeIndex(label);
            labelIndex.put(label, index);
        }
        return index;
    }

    private int indexAtBytecodeIndex(Label label) {
        int bci = code instanceof CodeAttribute ca ? ca.labelToBci(label) : -1;
        if (bci == codeLength) {
            return insns.size();
        }
        int index = Collections.binarySearch(bcis, bci);
        if (index < 0) {
            throw new IllegalStateException("a branch target (bytecode index " + bci
                    + ") is not the start of an instruction");
        }
        return index;
    }

    List<FlowValue> values() {
        return values;
    }

    MethodModel method() {
        return method;
    }

    FlowValue newSource(ValueRange range, Integer constant, int producer) {
        FlowValue v = FlowValue.source(values.size(), range, constant, producer);
        values.add(v);
        return v;
    }

    FlowValue newOperation(FlowValue.Op op, List<FlowValue> inputs, int producer) {
        FlowValue v = FlowValue.operation(values.size(), op, inputs, producer);
        values.add(v);
        return v;
    }

    private FlowValue newPhi(int producer) {
        FlowValue v = FlowValue.phi(values.size(), producer);
        values.add(v);
        return v;
    }

    private void index(CodeModel code) {
        int bci = 0;
        int line = -1;
        List<Label> pending = new ArrayList<>();
        for (CodeElement e : code.elementList()) {
            switch (e) {
                case Label l -> pending.add(l);
                case LineNumber ln -> line = ln.line();
                case Instruction insn -> {
                    for (Label l : pending) {
                        labelIndex.put(l, insns.size());
                    }
                    pending.clear();
                    insns.add(insn);
                    bcis.add(bci);
                    lines.add(line);
                    bci += insn.sizeInBytes();
                }
                default -> { /* exception table, local variable tables, stack maps */ }
            }
        }
        for (Label l : pending) {
            labelIndex.put(l, insns.size());
        }
        codeLength = bci;
    }

    // ── abstract interpretation ──

    private void interpret() {
        int n = insns.size();
        inStates = new FlowFrame[n];
        steps = new Step[n];
        joins = joinPoints();
        undefined = newSource(null, null, -1);
        flowTo(0, entryFrame());
        Deque<Integer> work = new ArrayDeque<>();
        work.push(0);
        boolean[] done = new boolean[n];
        while (!work.isEmpty()) {
            int i = work.pop();
            if (done[i]) {
                continue;
            }
            done[i] = true;
            interpretOne(i, work);
        }
    }

    private void interpretOne(int i, Deque<Integer> work) {
        FlowFrame frame = inStates[i].copy();
        FlowValue[] localsBefore = frame.locals().clone();
        for (ExceptionCatch c : catches) {
            if (indexOf(c.tryStart()) <= i && i < indexOf(c.tryEnd())) {
                int h = indexOf(c.handler());
                FlowFrame exceptional = new FlowFrame(localsBefore.clone(), new ArrayList<>());
                exceptional.push(exceptionValues.computeIfAbsent(h,
                        k -> newSource(ValueRange.REFERENCE, null, k)));
                enqueue(h, exceptional, work);
            }
        }
        steps[i] = new IntFlowInterpreter(this, i, frame).execute(insns.get(i));
        for (int s : successors(i)) {
            enqueue(s, frame, work);
        }
    }

    private void enqueue(int target, FlowFrame frame, Deque<Integer> work) {
        if (target >= insns.size()) {
            throw new IllegalStateException("control flow falls off the end of the code");
        }
        boolean first = inStates[target] == null;
        flowTo(target, frame);
        if (first) {
            work.push(target);
        }
    }

    private void flowTo(int target, FlowFrame frame) {
        if (!joins[target]) {
            if (inStates[target] != null) {
                throw new IllegalStateException("instruction " + target + " reached twice without a join");
            }
            inStates[target] = frame.copy();
            return;
        }
        if (inStates[target] == null) {
            inStates[target] = phiFrame(target, frame);
        }
        inStates[target].addPhiInputs(frame);
    }

    private FlowFrame phiFrame(int target, FlowFrame shape) {
        FlowValue[] locals = new FlowValue[shape.locals().length];
        for (int l = 0; l < locals.length; l++) {
            locals[l] = newPhi(target);
        }
        List<FlowValue> stack = new ArrayList<>();
        for (int s = 0; s < shape.stack().size(); s++) {
            stack.add(newPhi(target));
        }
        return new FlowFrame(locals, stack);
    }

    private FlowFrame entryFrame() {
        FlowValue[] locals = new FlowValue[Math.max(maxLocals, 1)];
        Arrays.fill(locals, undefined);
        int slot = 0;
        if ((method.flags().flagsMask() & ClassFile.ACC_STATIC) == 0) {
            locals[slot++] = newSource(ValueRange.REFERENCE, null, -1);
        }
        for (String p : Descriptors.parameters(method.methodType().stringValue())) {
            locals[slot++] = newSource(ValueRange.ofType(p), null, -1);
            if (p.equals("J") || p.equals("D")) {
                locals[slot++] = undefined;
            }
        }
        return new FlowFrame(locals, new ArrayList<>());
    }

    private boolean[] joinPoints() {
        int n = insns.size();
        int[] preds = new int[n + 1];
        preds[0] = 1; // method entry
        for (int i = 0; i < n; i++) {
            for (int s : successors(i)) {
                preds[Math.min(s, n)]++;
            }
        }
        boolean[] result = new boolean[n];
        for (int i = 0; i < n; i++) {
            result[i] = preds[i] > 1;
        }
        for (ExceptionCatch c : catches) {
            result[indexOf(c.handler())] = true;
        }
        return result;
    }

    private List<Integer> successors(int i) {
        Instruction insn = insns.get(i);
        List<Integer> result = new ArrayList<>();
        switch (insn) {
            case BranchInstruction b -> {
                result.add(indexOf(b.target()));
                if (b.opcode() != Opcode.GOTO && b.opcode() != Opcode.GOTO_W) {
                    result.add(i + 1);
                }
            }
            case TableSwitchInstruction t -> {
                result.add(indexOf(t.defaultTarget()));
                t.cases().forEach(c -> result.add(indexOf(c.target())));
            }
            case LookupSwitchInstruction l -> {
                result.add(indexOf(l.defaultTarget()));
                l.cases().forEach(c -> result.add(indexOf(c.target())));
            }
            default -> {
                if (!endsFlow(insn.opcode())) {
                    result.add(i + 1);
                }
            }
        }
        return result;
    }

    private static boolean endsFlow(Opcode op) {
        return switch (op) {
            case IRETURN, LRETURN, FRETURN, DRETURN, ARETURN, RETURN, ATHROW, RET -> true;
            default -> false;
        };
    }

    // ── ranges ──

    /**
     * Computes the ranges of all values to a fixpoint. Ranges only grow, and join points widen
     * their intervals to a few thresholds ({@link ValueRange#widen}), so the iteration ends.
     */
    void computeRanges() {
        boolean changed;
        do {
            changed = false;
            for (FlowValue v : values) {
                if (v.op != FlowValue.Op.SOURCE && ValueTransfer.update(v)) {
                    changed = true;
                }
            }
        } while (changed);
    }
}
