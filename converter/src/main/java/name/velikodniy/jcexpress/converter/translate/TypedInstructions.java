package name.velikodniy.jcexpress.converter.translate;

import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.util.List;
import java.util.Locale;

import static name.velikodniy.jcexpress.converter.translate.JcvmOpcode.*;

/**
 * Translates the instructions whose JCVM form depends on whether a value is a short or an int,
 * for a target with int support (JCVM 3.1 §2.2.3.1), following an {@link IntPlan}.
 *
 * <p>Short values use the s-instructions, int values the i-instructions (two words each,
 * §6.10.4). Where a value meets a consumer or producer of the other representation, the value on
 * top of the operand stack is converted with s2i (§7.5.82, exact) or i2s (§7.5.27, which keeps the
 * low 16 bits; the plan only allows it where the value is a short or only its low 16 bits are
 * used). Comparisons of ints use icmp (§7.5.32) followed by if&lt;cond&gt;; switches on ints use
 * itableswitch (§7.5.66) and ilookupswitch (§7.5.50).
 */
final class TypedInstructions {

    private final CodeEmitter emit;
    private final MemberInstructions members;
    private final IntPlan plan;
    private final boolean intReturn;

    TypedInstructions(CodeEmitter emit, MemberInstructions members, IntPlan plan, String methodDescriptor) {
        this.emit = emit;
        this.members = members;
        this.plan = plan;
        this.intReturn = Descriptors.returnType(methodDescriptor).equals("I");
    }

    IntPlan plan() {
        return plan;
    }

    /**
     * Translates an instruction with an int-dependent form.
     *
     * @param insn the instruction
     * @param bci  its JVM bytecode index
     * @return {@code false} if the instruction has no int-dependent form
     */
    @SuppressWarnings("java:S1541") // one case per instruction family of the ClassFile API
    boolean translate(Instruction insn, int bci) {
        IntFlow.Step step = plan.step(bci);
        switch (insn) {
            case ConstantInstruction c when c.constantValue() instanceof Integer v ->
                    Constants.push(emit, v, resultIsInt(step));
            case IncrementInstruction inc -> increment(inc, step);
            case ArrayLoadInstruction a -> arrayLoad(a, step);
            case ArrayStoreInstruction a -> arrayStore(a, step);
            case StackInstruction s -> stack(s.opcode(), step);
            case OperatorInstruction o -> operator(o.opcode(), step);
            case ConvertInstruction c -> convert(c.opcode(), step);
            case BranchInstruction b -> branch(b, step);
            case ReturnInstruction r when r.typeKind() == TypeKind.INT -> returnValue(step);
            case FieldInstruction f -> field(f, step);
            case InvokeInstruction inv -> invoke(inv, step);
            case NewPrimitiveArrayInstruction n -> newArray(step, () -> emit.op(NEWARRAY,
                    MemberInstructions.arrayType(n.typeKind())));
            case NewReferenceArrayInstruction n -> newArray(step, () -> members.newReferenceArray(n));
            case TypeCheckInstruction t -> {
                members.typeCheck(t);
                resultFrom(step, false);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** ireturn for an int method, sreturn for byte, short and boolean (§7.5.60, §7.5.99). */
    private void returnValue(IntFlow.Step step) {
        topTo(step, intReturn);
        emit.op(intReturn ? IRETURN : SRETURN);
    }

    /** newarray / anewarray take a short count (§7.5.71, §7.5.6). */
    private void newArray(IntFlow.Step step, Runnable instruction) {
        topTo(step, false);
        instruction.run();
    }

    // ── conversions ──

    /** Whether the value on top of the stack after the instruction is an int. */
    private boolean resultIsInt(IntFlow.Step step) {
        return step != null && !step.results().isEmpty() && plan.isInt(step.results().getLast());
    }

    /** Converts the value on top of the stack before the instruction to the given representation. */
    void topTo(IntFlow.Step step, boolean toInt) {
        if (step != null && !step.operands().isEmpty()) {
            convert(plan.isInt(step.operands().getLast()), toInt);
        }
    }

    /** Converts the result an instruction produced in its natural representation to the planned one. */
    void resultFrom(IntFlow.Step step, boolean naturalInt) {
        if (step != null && !step.results().isEmpty()) {
            convert(naturalInt, plan.isInt(step.results().getLast()));
        }
    }

    private void convert(boolean fromInt, boolean toInt) {
        if (fromInt && !toInt) {
            emit.op(I2S);
        } else if (!fromInt && toInt) {
            emit.op(S2I);
        }
    }

    /** A value below the top of the stack cannot be converted; the plan must have chosen its form. */
    private void requireOperand(IntFlow.Step step, int index, boolean isInt, String what) {
        if (step != null && plan.isInt(step.operands().get(index)) != isInt) {
            throw new IllegalStateException("int support: the " + what + " is computed as "
                    + (isInt ? "a short" : "an int") + " but must be " + (isInt ? "an int" : "a short")
                    + " below the top of the operand stack; assign it to a local variable first");
        }
    }

    // ── locals ──

    /** Loads a local variable: s/i form by the variable's representation, then converts. */
    void load(int slot, IntFlow.Step step) {
        FlowValue copy = step == null ? null : step.results().getFirst();
        boolean local = copy != null && plan.isInt(copy.inputs.getFirst());
        localOp(plan.localIndex(slot), local ? ILOAD : SLOAD, local ? ILOAD_0 : SLOAD_0);
        resultFrom(step, local);
    }

    /** Stores into a local variable after converting the value to the variable's representation. */
    void store(int slot, IntFlow.Step step) {
        boolean local = step != null && plan.isInt(step.stored());
        topTo(step, local);
        localOp(plan.localIndex(slot), local ? ISTORE : SSTORE, local ? ISTORE_0 : SSTORE_0);
    }

    /** Reference loads and stores only need the renumbered index. */
    void referenceLocal(int slot, int opcode, int shortForm0) {
        localOp(plan.localIndex(slot), opcode, shortForm0);
    }

    private void localOp(int index, int opcode, int shortForm0) {
        if (index > 0xFF) {
            throw new IllegalStateException("local variable index " + index
                    + " exceeds 255 (JCVM 3.1 §2.2.4.4)");
        }
        if (index <= 3) {
            emit.op(shortForm0 + index);
        } else {
            emit.op(opcode, index);
        }
    }

    /** iinc on an int variable (§7.5.45/§7.5.46), sinc on a short one (§7.5.89/§7.5.90). */
    private void increment(IncrementInstruction inc, IntFlow.Step step) {
        boolean local = step != null && plan.isInt(step.stored());
        boolean wide = inc.constant() < Byte.MIN_VALUE || inc.constant() > Byte.MAX_VALUE;
        int index = plan.localIndex(inc.slot());
        if (index > 0xFF) {
            throw new IllegalStateException("local variable index " + index + " exceeds 255 (JCVM 3.1 §2.2.4.4)");
        }
        int opcode = local ? (wide ? IINC_W : IINC) : (wide ? SINC_W : SINC);
        if (wide) {
            emit.op(opcode, index, inc.constant() >> 8, inc.constant());
        } else {
            emit.op(opcode, index, inc.constant());
        }
    }

    // ── arrays ──

    private void arrayLoad(ArrayLoadInstruction a, IntFlow.Step step) {
        topTo(step, false); // the index is a short (§2.2.1.1.8)
        int opcode = switch (a.typeKind()) {
            case REFERENCE -> AALOAD;
            case BYTE, BOOLEAN -> BALOAD;
            case SHORT -> SALOAD;
            case INT -> IALOAD;
            default -> throw new IllegalStateException("unsupported array element type " + a.typeKind());
        };
        emit.op(opcode);
        if (a.typeKind() != TypeKind.REFERENCE) {
            resultFrom(step, a.typeKind() == TypeKind.INT);
        }
    }

    private void arrayStore(ArrayStoreInstruction a, IntFlow.Step step) {
        requireOperand(step, 1, false, "array index");
        int opcode = switch (a.typeKind()) {
            case REFERENCE -> AASTORE;
            case BYTE, BOOLEAN -> BASTORE;
            case SHORT -> SASTORE;
            case INT -> IASTORE;
            default -> throw new IllegalStateException("unsupported array element type " + a.typeKind());
        };
        if (a.typeKind() != TypeKind.REFERENCE) {
            topTo(step, a.typeKind() == TypeKind.INT);
        }
        emit.op(opcode);
    }

    // ── operand stack (§7.5.17-§7.5.19, §7.5.73, §7.5.74, §7.5.108): word counts ──

    private void stack(Opcode op, IntFlow.Step step) {
        if (step == null) {
            emit.op(OpcodeMap.stack(op), OpcodeMap.stackOperands(op));
            return;
        }
        List<FlowValue> v = step.operands(); // bottom first
        int top = words(v.getLast());
        switch (op) {
            case POP -> emit.op(top == 2 ? POP2 : POP);
            case POP2 -> {
                emit.op(top == 2 ? POP2 : POP);
                emit.op(words(v.getFirst()) == 2 ? POP2 : POP);
            }
            case DUP -> emit.op(top == 2 ? DUP2 : DUP);
            case DUP2 -> dupTop(top + words(v.getFirst()));
            case DUP_X1 -> dupX(top, top + words(v.get(0)));
            case DUP_X2 -> dupX(top, top + words(v.get(0)) + words(v.get(1)));
            case DUP2_X1 -> dupX(top + words(v.get(1)), top + words(v.get(1)) + words(v.get(0)));
            case DUP2_X2 -> dupX(top + words(v.get(2)),
                    top + words(v.get(2)) + words(v.get(1)) + words(v.get(0)));
            case SWAP -> emit.op(SWAP_X, (top << 4) | words(v.getFirst()));
            default -> throw new IllegalStateException("unsupported stack instruction " + op);
        }
    }

    private int words(FlowValue v) {
        return plan.isInt(v) ? 2 : 1;
    }

    private void dupTop(int m) {
        if (m == 2) {
            emit.op(DUP2);
        } else {
            emit.op(DUP_X, m << 4); // n = 0: copy the top m words onto the top (§7.5.18)
        }
    }

    private void dupX(int m, int n) {
        emit.op(DUP_X, (m << 4) | n);
    }

    // ── arithmetic and conversions ──

    private void operator(Opcode op, IntFlow.Step step) {
        if (op == Opcode.ARRAYLENGTH) {
            emit.op(ARRAYLENGTH);
            resultFrom(step, false);
            return;
        }
        boolean isInt = resultIsInt(step);
        for (int k = 0; step != null && k < step.operands().size() - 1; k++) {
            requireOperand(step, k, isInt, "operand of " + op.name().toLowerCase(Locale.ROOT));
        }
        topTo(step, isInt);
        emit.op(OpcodeMap.operator(op, isInt));
    }

    /** i2b/i2s: from an int with i2b/i2s, from a short with s2b or nothing (§7.5.26, §7.5.81). */
    private void convert(Opcode op, IntFlow.Step step) {
        boolean fromInt = step != null && plan.isInt(step.operands().getFirst());
        switch (op) {
            case I2B -> emit.op(fromInt ? I2B : S2B);
            case I2S -> {
                if (fromInt) {
                    emit.op(I2S);
                }
            }
            default -> throw new IllegalStateException("unsupported conversion " + op);
        }
        resultFrom(step, false);
    }

    // ── branches and switches ──

    private void branch(BranchInstruction b, IntFlow.Step step) {
        Opcode op = b.opcode();
        boolean intOperands = step != null && !step.operands().isEmpty()
                && plan.isInt(step.operands().getLast());
        switch (op) {
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE -> {
                if (intOperands) {
                    emit.op(ICONST_0);
                    emit.op(ICMP);
                }
                emit.branch(OpcodeMap.branch(op), b.target());
            }
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> {
                requireOperand(step, 0, intOperands, "left operand of the comparison");
                if (intOperands) {
                    emit.op(ICMP);
                    emit.branch(compareWithZero(op), b.target());
                } else {
                    emit.branch(OpcodeMap.branch(op), b.target());
                }
            }
            default -> emit.branch(OpcodeMap.branch(op), b.target());
        }
    }

    /** if_icmp&lt;cond&gt; after icmp: the comparison result is tested against zero. */
    private static int compareWithZero(Opcode op) {
        return switch (op) {
            case IF_ICMPEQ -> IFEQ;
            case IF_ICMPNE -> IFNE;
            case IF_ICMPLT -> IFLT;
            case IF_ICMPGE -> IFGE;
            case IF_ICMPGT -> IFGT;
            default -> IFLE;
        };
    }

    /** Whether the key of a switch at this step is an int (itableswitch / ilookupswitch). */
    boolean intKey(IntFlow.Step step) {
        return step != null && plan.isInt(step.operands().getFirst());
    }

    // ── fields and invocations ──

    private void field(FieldInstruction f, IntFlow.Step step) {
        boolean intField = f.typeSymbol().descriptorString().equals("I");
        boolean numeric = MemberInstructions.fieldType(f.typeSymbol().descriptorString()) != 0;
        if (numeric && (f.opcode() == Opcode.PUTFIELD || f.opcode() == Opcode.PUTSTATIC)) {
            topTo(step, intField);
        }
        members.field(f);
        if (numeric && (f.opcode() == Opcode.GETFIELD || f.opcode() == Opcode.GETSTATIC)) {
            resultFrom(step, intField);
        }
    }

    /** Converts the value of a putfield_&lt;t&gt;_this before the store (§7.5.76). */
    void beforePutfieldThis(FieldInstruction f, IntFlow.Step step) {
        if (MemberInstructions.fieldType(f.typeSymbol().descriptorString()) != 0) {
            topTo(step, f.typeSymbol().descriptorString().equals("I"));
        }
    }

    /** Converts the value of a getfield_&lt;t&gt;_this after the load (§7.5.21). */
    void afterGetfieldThis(FieldInstruction f, IntFlow.Step step) {
        if (MemberInstructions.fieldType(f.typeSymbol().descriptorString()) != 0) {
            resultFrom(step, f.typeSymbol().descriptorString().equals("I"));
        }
    }

    private void invoke(InvokeInstruction inv, IntFlow.Step step) {
        String desc = inv.typeSymbol().descriptorString();
        List<String> params = Descriptors.parameters(desc);
        if (step != null) {
            int first = step.operands().size() - params.size();
            for (int p = 0; p < params.size() - 1; p++) {
                if ("BSZI".indexOf(params.get(p).charAt(0)) >= 0) {
                    requireOperand(step, first + p, params.get(p).equals("I"), "argument " + (p + 1));
                }
            }
            if (!params.isEmpty() && "BSZI".indexOf(params.getLast().charAt(0)) >= 0) {
                topTo(step, params.getLast().equals("I"));
            }
        }
        members.invoke(inv);
        String ret = Descriptors.returnType(desc);
        if ("BSZI".indexOf(ret.charAt(0)) >= 0) {
            resultFrom(step, ret.equals("I"));
        }
    }
}
