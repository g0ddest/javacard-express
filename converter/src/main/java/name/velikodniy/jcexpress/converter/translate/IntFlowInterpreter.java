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
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.MonitorInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.NopInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StackInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the stack and local variable effect of one JVM instruction (JVMS §6.5) to a
 * {@link FlowFrame}, creating the {@link FlowValue}s it produces.
 */
final class IntFlowInterpreter {

    private final IntFlow flow;
    private final int index;
    private final FlowFrame frame;
    private final FlowValue[] localsBefore;
    private final List<FlowValue> operands = new ArrayList<>();
    private final List<FlowValue> results = new ArrayList<>();
    private FlowValue stored;

    IntFlowInterpreter(IntFlow flow, int index, FlowFrame frame) {
        this.flow = flow;
        this.index = index;
        this.frame = frame;
        this.localsBefore = frame.locals().clone();
    }

    IntFlow.Step execute(Instruction insn) {
        dispatch(insn);
        return new IntFlow.Step(List.copyOf(operands), List.copyOf(results), localsBefore, stored);
    }

    private FlowValue copy(FlowValue v) {
        return flow.newOperation(FlowValue.Op.COPY, List.of(v), index);
    }

    private void store(int slot, FlowValue v) {
        frame.setLocal(slot, v);
        stored = v;
    }

    /** Loads, stores, constants, stack and array instructions; everything else in {@link #operate}. */
    private void dispatch(Instruction insn) {
        switch (insn) {
            case LoadInstruction l -> push(copy(frame.local(l.slot())));
            case StoreInstruction s -> store(s.slot(), copy(pop(1).getFirst()));
            case IncrementInstruction inc -> increment(inc);
            case ConstantInstruction c -> constant(c);
            case ArrayLoadInstruction a -> {
                pop(2);
                push(source(elementRange(a.typeKind())));
            }
            case ArrayStoreInstruction a -> pop(3);
            case StackInstruction s -> stack(s.opcode());
            case NopInstruction n -> { /* no effect */ }
            default -> operate(insn);
        }
    }

    @SuppressWarnings("java:S1541") // one case per instruction family of the ClassFile API
    private void operate(Instruction insn) {
        switch (insn) {
            case OperatorInstruction o -> operator(o.opcode());
            case ConvertInstruction c -> convert(c.opcode());
            case BranchInstruction b -> pop(branchOperands(b.opcode()));
            case TableSwitchInstruction t -> pop(1);
            case LookupSwitchInstruction l -> pop(1);
            case ReturnInstruction r -> pop(r.typeKind() == TypeKind.VOID ? 0 : 1);
            case ThrowInstruction t -> pop(1);
            case MonitorInstruction m -> pop(1);
            case FieldInstruction f -> field(f);
            case InvokeInstruction inv -> invoke(inv.opcode() != Opcode.INVOKESTATIC,
                    inv.typeSymbol().descriptorString());
            case InvokeDynamicInstruction d -> invoke(false, d.typeSymbol().descriptorString());
            case NewObjectInstruction n -> push(source(ValueRange.REFERENCE));
            case NewPrimitiveArrayInstruction n -> newArray(1);
            case NewReferenceArrayInstruction n -> newArray(1);
            case NewMultiArrayInstruction n -> newArray(n.dimensions());
            case TypeCheckInstruction t -> {
                pop(1);
                push(source(t.opcode() == Opcode.CHECKCAST ? ValueRange.REFERENCE : ValueRange.BOOLEAN));
            }
            default -> throw new IllegalStateException("cannot analyse instruction " + insn.opcode());
        }
    }

    private List<FlowValue> pop(int n) {
        List<FlowValue> popped = frame.pop(n);
        operands.addAll(popped);
        return popped;
    }

    private void push(FlowValue v) {
        frame.push(v);
        results.add(v);
    }

    private FlowValue source(ValueRange range) {
        return flow.newSource(range, null, index);
    }

    /** Range of an array element; baload serves byte and boolean arrays (JVMS §6.5 baload). */
    private static ValueRange elementRange(TypeKind kind) {
        return switch (kind) {
            case REFERENCE -> ValueRange.REFERENCE;
            case INT -> ValueRange.INT;
            case BYTE, BOOLEAN -> ValueRange.BYTE;
            case SHORT -> ValueRange.SHORT;
            case CHAR -> ValueRange.ofType("C");
            default -> ValueRange.UNUSABLE;
        };
    }

    /** i2b and i2s keep the value when it fits (JVMS §6.5); other conversions are not modelled. */
    private void convert(Opcode op) {
        switch (op) {
            case I2B -> push(flow.newOperation(FlowValue.Op.TO_BYTE, pop(1), index));
            case I2S -> push(flow.newOperation(FlowValue.Op.TO_SHORT, pop(1), index));
            case I2C -> {
                pop(1);
                push(source(ValueRange.ofType("C")));
            }
            default -> {
                pop(1);
                push(source(ValueRange.UNUSABLE));
            }
        }
    }

    private void increment(IncrementInstruction inc) {
        FlowValue old = frame.local(inc.slot());
        operands.add(old);
        FlowValue constant = flow.newSource(ValueRange.constant(inc.constant()), inc.constant(), index);
        FlowValue result = flow.newOperation(FlowValue.Op.INC, List.of(old, constant), index);
        store(inc.slot(), result);
        results.add(result);
    }

    private void constant(ConstantInstruction c) {
        Object value = c.constantValue();
        if (value instanceof Integer v) {
            push(flow.newSource(ValueRange.constant(v), v, index));
        } else if (c.opcode() == Opcode.ACONST_NULL || value instanceof String
                || value instanceof ClassDesc) {
            push(source(ValueRange.REFERENCE));
        } else {
            push(source(ValueRange.UNUSABLE));
        }
    }

    /** JVMS §6.5 dup*, pop*, swap on category 1 values (the Java Card subset has no others). */
    private void stack(Opcode op) {
        switch (op) {
            case POP -> pop(1);
            case POP2 -> pop(2);
            case DUP -> repush(pop(1), 0, 0);
            case DUP_X1 -> repush(pop(2), 1, 0, 1);
            case DUP_X2 -> repush(pop(3), 2, 0, 1, 2);
            case DUP2 -> repush(pop(2), 0, 1, 0, 1);
            case DUP2_X1 -> repush(pop(3), 1, 2, 0, 1, 2);
            case DUP2_X2 -> repush(pop(4), 2, 3, 0, 1, 2, 3);
            case SWAP -> repush(pop(2), 1, 0);
            default -> throw new IllegalStateException("cannot analyse " + op);
        }
    }

    /** Pushes the popped values (bottom first) again in the given order. */
    private void repush(List<FlowValue> popped, int... order) {
        for (int k : order) {
            push(popped.get(k));
        }
    }

    private void operator(Opcode op) {
        if (op == Opcode.ARRAYLENGTH) {
            pop(1);
            push(source(ValueRange.ARRAY_LENGTH));
            return;
        }
        if (op == Opcode.INEG) {
            push(flow.newOperation(FlowValue.Op.NEG, pop(1), index));
            return;
        }
        FlowValue.Op flowOp = switch (op) {
            case IADD -> FlowValue.Op.ADD;
            case ISUB -> FlowValue.Op.SUB;
            case IMUL -> FlowValue.Op.MUL;
            case IDIV -> FlowValue.Op.DIV;
            case IREM -> FlowValue.Op.REM;
            case IAND -> FlowValue.Op.AND;
            case IOR -> FlowValue.Op.OR;
            case IXOR -> FlowValue.Op.XOR;
            case ISHL -> FlowValue.Op.SHL;
            case ISHR -> FlowValue.Op.SHR;
            case IUSHR -> FlowValue.Op.USHR;
            default -> null; // long, float, double: rejected by the subset rules
        };
        List<FlowValue> inputs = pop(isUnaryNonInt(op) ? 1 : 2);
        push(flowOp == null ? source(ValueRange.UNUSABLE) : flow.newOperation(flowOp, inputs, index));
    }

    private static boolean isUnaryNonInt(Opcode op) {
        return op == Opcode.LNEG || op == Opcode.FNEG || op == Opcode.DNEG;
    }

    private static int branchOperands(Opcode op) {
        return switch (op) {
            case GOTO, GOTO_W -> 0;
            case IFEQ, IFNE, IFLT, IFGE, IFGT, IFLE, IFNULL, IFNONNULL -> 1;
            default -> 2;
        };
    }

    private void field(FieldInstruction f) {
        ValueRange range = ValueRange.ofType(f.typeSymbol().descriptorString());
        switch (f.opcode()) {
            case GETSTATIC -> push(source(range));
            case PUTSTATIC -> pop(1);
            case GETFIELD -> {
                pop(1);
                push(source(range));
            }
            default -> pop(2);
        }
    }

    private void invoke(boolean hasReceiver, String descriptor) {
        int args = Descriptors.parameters(descriptor).size() + (hasReceiver ? 1 : 0);
        pop(args);
        String ret = Descriptors.returnType(descriptor);
        if (!ret.equals("V")) {
            push(source(ValueRange.ofType(ret)));
        }
    }

    private void newArray(int dimensions) {
        pop(dimensions);
        push(source(ValueRange.REFERENCE));
    }
}
