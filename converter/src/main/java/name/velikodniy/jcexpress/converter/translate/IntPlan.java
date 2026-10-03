package name.velikodniy.jcexpress.converter.translate;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Representation of the int-typed JVM values of one method on a Java Card virtual machine with
 * int support (JCVM 3.1 §2.2.3.1): every value is held either as a 16-bit short (one word) or as a
 * 32-bit int (two words, §6.10.4 "Stack entries of type int are represented in two words").
 *
 * <p>javac computes byte, short and boolean expressions with int instructions. A value is held as
 * an int when it is of type int ({@link ValueKind#INT}: int fields, arrays, parameters, results,
 * constants outside the short range), when it may leave the short range and something needs its
 * whole value ({@link ValueKind#LOW16} reaching a comparison, switch, division, right shift, int
 * store, argument or return), and when it is computed together with such a value: the inputs and
 * the result of an arithmetic instruction, the two operands of a comparison and the values merged
 * at a join point share one representation (they form a <em>web</em>). Everything else stays a
 * short, so code without int values is translated exactly as without int support.
 *
 * <p>Values meet a different representation only at loads and stores of local variables, at
 * field, array and method boundaries and at the explicit i2s/i2b conversions; there the
 * translator converts the value on top of the operand stack with s2i (§7.5.82) or i2s
 * (§7.5.27). Local variables get one representation per JVM slot; an int slot takes two words,
 * so the JCVM local variable indices are renumbered (§6.10.4 "Local variables of type int are
 * represented in two words").
 */
final class IntPlan {

    private final MethodModel method;
    private final IntFlow flow;
    private final int[] parent;
    private final boolean[] wide;
    private final Map<Integer, Integer> indexByBci = new HashMap<>();
    private int[] localIndex;
    private int nargs;
    private int maxLocals;
    private int maxStack;

    private IntPlan(MethodModel method, IntFlow flow) {
        this.method = method;
        this.flow = flow;
        this.parent = new int[flow.values().size()];
        this.wide = new boolean[flow.values().size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < flow.size(); i++) {
            indexByBci.put(flow.bci(i), i);
        }
    }

    /**
     * Computes the representation of every value of a method with code.
     *
     * @param method the method
     * @return the plan
     * @throws IllegalStateException if the code cannot be represented
     */
    static IntPlan build(MethodModel method) {
        IntPlan plan = new IntPlan(method, IntFlow.analyze(method));
        plan.unite();
        plan.markInts();
        plan.propagate();
        plan.layoutLocals();
        plan.computeMaxStack();
        return plan;
    }

    /** Step of the instruction at a JVM bytecode index, or {@code null} if it is unreachable. */
    IntFlow.Step step(int bci) {
        Integer i = indexByBci.get(bci);
        return i == null ? null : flow.step(i);
    }

    /** Whether a value is held as an int (two words); references and unknown values are not. */
    boolean isInt(FlowValue v) {
        return v != null && v.kind() != null && v.kind().isNumeric() && wide[find(v.id)];
    }

    /** JCVM local variable index of a JVM local variable slot. */
    int localIndex(int slot) {
        return localIndex[slot];
    }

    /** Parameter words including {@code this} (§6.10.4 nargs). */
    int nargs() {
        return nargs;
    }

    /** Local variable words excluding the parameters (§6.10.4 max_locals). */
    int maxLocals() {
        return maxLocals;
    }

    /** Operand stack words (§6.10.4 max_stack). */
    int maxStack() {
        return maxStack;
    }

    // ── webs ──

    private int find(int id) {
        while (parent[id] != id) {
            parent[id] = parent[parent[id]];
            id = parent[id];
        }
        return id;
    }

    private void union(FlowValue a, FlowValue b) {
        int ra = find(a.id);
        int rb = find(b.id);
        if (ra != rb) {
            parent[ra] = rb;
            wide[rb] |= wide[ra];
        }
    }

    /** Makes the web of a value int; returns whether it changed. */
    private boolean widen(FlowValue v) {
        if (v == null || v.kind() == null || !v.kind().isNumeric() || wide[find(v.id)]) {
            return false;
        }
        wide[find(v.id)] = true;
        return true;
    }

    /** Arithmetic inputs and results, merged values and compared values share a representation. */
    private void unite() {
        for (FlowValue v : flow.values()) {
            switch (v.op) {
                case PHI, ADD, SUB, MUL, NEG, DIV, REM, AND, OR, XOR, SHL, SHR, USHR ->
                        v.inputs.forEach(in -> union(v, in));
                case INC -> union(v, v.inputs.getFirst());
                default -> { /* sources, copies and conversions start their own web */ }
            }
        }
        for (int i = 0; i < flow.size(); i++) {
            if (flow.step(i) != null && flow.instruction(i) instanceof BranchInstruction b
                    && flow.step(i).operands().size() == 2 && isIntCompare(b.opcode())) {
                union(flow.step(i).operands().get(0), flow.step(i).operands().get(1));
            }
        }
    }

    private static boolean isIntCompare(Opcode op) {
        return switch (op) {
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> true;
            default -> false;
        };
    }

    /** Values of type int, int locals (javac's iinc) and switches with int keys. */
    private void markInts() {
        for (FlowValue v : flow.values()) {
            if (v.kind() == ValueKind.INT) {
                widen(v);
            }
        }
        for (int i = 0; i < flow.size(); i++) {
            IntFlow.Step step = flow.step(i);
            if (step == null) {
                continue;
            }
            switch (flow.instruction(i)) {
                case IncrementInstruction inc -> widen(step.stored());
                case TableSwitchInstruction t when !shortKeys(t.lowValue(), t.highValue()) ->
                        widen(step.operands().getFirst());
                case LookupSwitchInstruction l when l.cases().stream()
                        .anyMatch(c -> !shortKeys(c.caseValue(), c.caseValue())) ->
                        widen(step.operands().getFirst());
                default -> { /* no int declaration */ }
            }
        }
    }

    private static boolean shortKeys(int low, int high) {
        return low >= Short.MIN_VALUE && high <= Short.MAX_VALUE;
    }

    /** Widens webs until every use of a value gets the value it needs (a fixpoint). */
    private void propagate() {
        boolean changed;
        do {
            changed = false;
            for (int i = 0; i < flow.size(); i++) {
                if (flow.step(i) != null) {
                    changed |= demands(flow.instruction(i), flow.step(i));
                }
            }
        } while (changed);
    }

    @SuppressWarnings("java:S1541") // one case per kind of use
    private boolean demands(Instruction insn, IntFlow.Step step) {
        List<FlowValue> ops = step.operands();
        return switch (insn) {
            case BranchInstruction b -> ops.stream().map(this::needsWholeValue).reduce(false, Boolean::logicalOr);
            case TableSwitchInstruction t -> needsWholeValue(ops.getFirst());
            case LookupSwitchInstruction l -> needsWholeValue(ops.getFirst());
            case OperatorInstruction o -> operator(o.opcode(), ops);
            case ReturnInstruction r -> r.typeKind() == TypeKind.INT
                    && Descriptors.returnType(method.methodType().stringValue()).equals("I")
                    && needsInt(ops.getFirst(), true);
            case FieldInstruction f -> (f.opcode() == Opcode.PUTFIELD || f.opcode() == Opcode.PUTSTATIC)
                    && f.typeSymbol().descriptorString().equals("I") && needsInt(ops.getLast(), true);
            case ArrayStoreInstruction a -> a.typeKind() == TypeKind.INT && needsInt(ops.getLast(), true);
            case InvokeInstruction inv -> arguments(inv.typeSymbol().descriptorString(), ops);
            case StoreInstruction s -> s.typeKind() == TypeKind.INT && isInt(step.stored())
                    && needsInt(ops.getFirst(), true);
            case LoadInstruction l -> l.typeKind() == TypeKind.INT && loadedWhole(step.results().getFirst());
            default -> false;
        };
    }

    /** Division and remainder need exact operands, right shifts an exact shifted value. */
    private boolean operator(Opcode op, List<FlowValue> ops) {
        return switch (op) {
            case IDIV, IREM -> needsWholeValue(ops.get(0)) | needsWholeValue(ops.get(1));
            case ISHR, IUSHR -> needsWholeValue(ops.get(0));
            default -> false;
        };
    }

    private boolean arguments(String descriptor, List<FlowValue> ops) {
        List<String> params = Descriptors.parameters(descriptor);
        int first = ops.size() - params.size();
        boolean changed = false;
        for (int p = 0; p < params.size(); p++) {
            if (params.get(p).equals("I")) {
                changed |= needsInt(ops.get(first + p), p == params.size() - 1);
            }
        }
        return changed;
    }

    /** A consumer that sees the whole value: a value that may leave the short range is an int. */
    private boolean needsWholeValue(FlowValue v) {
        return v.kind() == ValueKind.LOW16 && widen(v);
    }

    /**
     * A consumer of an int: an exact short on top of the stack is widened there with s2i; a value
     * that may leave the short range, or one below the top, must be computed as an int. A
     * constant is simply pushed as an int (iconst, bipush, sipush, iipush).
     */
    private boolean needsInt(FlowValue v, boolean onTop) {
        return (v.kind() == ValueKind.LOW16 || !onTop || v.constant != null) && widen(v);
    }

    /** A load whose value is used as an int needs the whole value from the local variable. */
    private boolean loadedWhole(FlowValue copy) {
        return isInt(copy) && copy.kind() == ValueKind.LOW16 && widen(copy.inputs.getFirst());
    }

    // ── local variables ──

    private void layoutLocals() {
        CodeModel code = method.code().orElseThrow();
        int jvmLocals = code instanceof CodeAttribute ca ? ca.maxLocals() : 0;
        boolean[] intSlot = new boolean[Math.max(jvmLocals, 1)];
        int slot = (method.flags().flagsMask() & ClassFile.ACC_STATIC) != 0 ? 0 : 1;
        int paramSlots = slot;
        for (String p : Descriptors.parameters(method.methodType().stringValue())) {
            intSlot[paramSlots++] = p.equals("I");
        }
        for (int i = 0; i < flow.size(); i++) {
            IntFlow.Step step = flow.step(i);
            if (step != null && step.stored() != null && isInt(step.stored())) {
                int s = flow.instruction(i) instanceof StoreInstruction st ? st.slot()
                        : ((IncrementInstruction) flow.instruction(i)).slot();
                if (s < paramSlots && !intSlot[s]) {
                    throw new IllegalStateException("an int value is stored in the short parameter in"
                            + " local variable " + s);
                }
                intSlot[s] = true;
            }
        }
        localIndex = new int[intSlot.length + 1];
        for (int s = 0; s < intSlot.length; s++) {
            localIndex[s + 1] = localIndex[s] + (intSlot[s] ? 2 : 1);
        }
        nargs = localIndex[paramSlots];
        maxLocals = jvmLocals == 0 ? 0 : localIndex[jvmLocals] - nargs;
    }

    // ── operand stack ──

    /**
     * Operand stack words: the stack before and after every instruction, and the words held while
     * a value is converted (s2i of the top operand, an int result before i2s, the iconst_0 of an
     * int comparison with zero).
     */
    private void computeMaxStack() {
        int max = 0;
        for (int i = 0; i < flow.size(); i++) {
            IntFlow.Step step = flow.step(i);
            if (step == null) {
                continue;
            }
            Instruction insn = flow.instruction(i);
            int before = words(flow.stackBefore(i));
            int after = insn instanceof IncrementInstruction ? before
                    : before - words(step.operands()) + words(step.results());
            max = Math.max(max, peak(insn, step, before, after));
        }
        maxStack = max;
    }

    private int peak(Instruction insn, IntFlow.Step step, int before, int after) {
        int peak = Math.max(before, after);
        FlowValue top = step.operands().isEmpty() ? null : step.operands().getLast();
        if (top != null && isNumeric(top) && !isInt(top) && needsIntOnTop(insn, step)) {
            peak = Math.max(peak, before + 1);
        }
        if (insn instanceof BranchInstruction b && isZeroCompare(b.opcode()) && isInt(top)) {
            peak = Math.max(peak, before + 2);
        }
        if (naturalIntResult(insn, step) && !step.results().isEmpty() && !isInt(step.results().getLast())) {
            peak = Math.max(peak, after + 1);
        }
        return peak;
    }

    /** Consumers that take an int on top of the stack (an exact short is widened with s2i). */
    private boolean needsIntOnTop(Instruction insn, IntFlow.Step step) {
        return switch (insn) {
            case ReturnInstruction r -> r.typeKind() == TypeKind.INT
                    && Descriptors.returnType(method.methodType().stringValue()).equals("I");
            case FieldInstruction f -> (f.opcode() == Opcode.PUTFIELD || f.opcode() == Opcode.PUTSTATIC)
                    && f.typeSymbol().descriptorString().equals("I");
            case ArrayStoreInstruction a -> a.typeKind() == TypeKind.INT;
            case InvokeInstruction inv -> {
                List<String> params = Descriptors.parameters(inv.typeSymbol().descriptorString());
                yield !params.isEmpty() && params.getLast().equals("I");
            }
            case StoreInstruction s -> s.typeKind() == TypeKind.INT && isInt(step.stored());
            case OperatorInstruction o -> !step.results().isEmpty() && isInt(step.results().getFirst());
            case BranchInstruction b -> isIntCompare(b.opcode()) && isInt(step.operands().getFirst());
            default -> false;
        };
    }

    /** Producers of an int (narrowed with i2s when the plan keeps the value a short). */
    private boolean naturalIntResult(Instruction insn, IntFlow.Step step) {
        return switch (insn) {
            case FieldInstruction f -> (f.opcode() == Opcode.GETFIELD || f.opcode() == Opcode.GETSTATIC)
                    && f.typeSymbol().descriptorString().equals("I");
            case ArrayLoadInstruction a -> a.typeKind() == TypeKind.INT;
            case InvokeInstruction inv -> Descriptors.returnType(inv.typeSymbol().descriptorString()).equals("I");
            case LoadInstruction l -> l.typeKind() == TypeKind.INT
                    && isInt(step.results().getFirst().inputs.getFirst());
            default -> false;
        };
    }

    private static boolean isZeroCompare(Opcode op) {
        return switch (op) {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> true;
            default -> false;
        };
    }

    private static boolean isNumeric(FlowValue v) {
        return v.kind() != null && v.kind().isNumeric();
    }

    private int words(List<FlowValue> values) {
        int w = 0;
        for (FlowValue v : values) {
            w += isInt(v) ? 2 : 1;
        }
        return w;
    }
}
