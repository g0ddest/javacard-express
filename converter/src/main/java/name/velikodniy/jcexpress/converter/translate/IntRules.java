package name.velikodniy.jcexpress.converter.translate;

import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.OperatorInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Enforces JCVM 3.1 §2.2.3.1 (Integer Data Type) on one method: "A Java Card virtual machine that
 * does not support the int data type will reject programs which use the int data type or 32-bit
 * intermediate values ... must reject expressions that could produce a different result."
 *
 * <p>Without int support every use of the int type is rejected (int constants outside the short
 * range, int fields, arrays, parameters, locals, calls of methods with int in their signature),
 * as is every intermediate value that may leave the short range ({@link ValueKind#LOW16}, decided
 * with value intervals: the sum of two bytes is exact, the sum of two shorts is not) and reaches
 * an instruction whose result depends on more than its low 16 bits: comparisons, switches,
 * division and remainder, right shifts, array indices and sizes, arguments, return values and
 * local variables. Array indices and sizes must be short values in any case (§2.2.1.1.8: "The
 * Java Card platform only supports array indexing using the short data type").
 */
public final class IntRules {

    private static final String SPEC = " (JCVM 3.1 §2.2.3.1)";
    private static final String CAST = ": cast it to short or byte" + SPEC;

    private final IntFlow flow;
    private final boolean intSupported;
    private final List<IntIssue> issues = new ArrayList<>();

    private IntRules(IntFlow flow, boolean intSupported) {
        this.flow = flow;
        this.intSupported = intSupported;
    }

    /**
     * Checks one method.
     *
     * @param method       method model (methods without code have no issues here)
     * @param intSupported whether the target supports the int type
     * @return the issues, in instruction order
     * @throws IllegalStateException if the code cannot be analysed (e.g. it uses long values)
     */
    public static List<IntIssue> check(MethodModel method, boolean intSupported) {
        if (method.code().isEmpty()) {
            return List.of();
        }
        IntRules rules = new IntRules(IntFlow.analyze(method), intSupported);
        if (!intSupported) {
            rules.checkLocalVariableTable();
        }
        for (int i = 0; i < rules.flow.size(); i++) {
            if (rules.flow.step(i) != null) {
                rules.checkInstruction(i, rules.flow.instruction(i), rules.flow.step(i));
            }
        }
        return List.copyOf(rules.issues);
    }

    private void checkLocalVariableTable() {
        for (CodeElement e : flow.method().code().orElseThrow()) {
            if (e instanceof LocalVariable lv && isIntType(lv.typeSymbol().descriptorString())) {
                report(-1, "local variable '" + lv.name().stringValue() + "' of type "
                        + lv.typeSymbol().displayName() + " needs int support" + SPEC);
            }
        }
    }

    private static boolean isIntType(String descriptor) {
        return descriptor.equals("I") || descriptor.equals("[I");
    }

    @SuppressWarnings("java:S1541") // one case per instruction family
    private void checkInstruction(int i, Instruction insn, IntFlow.Step step) {
        switch (insn) {
            case ConstantInstruction c -> constant(i, c);
            case FieldInstruction f -> field(i, f);
            case ArrayLoadInstruction a -> arrayAccess(i, a.typeKind(), step.operands().get(1));
            case ArrayStoreInstruction a -> arrayAccess(i, a.typeKind(), step.operands().get(1));
            case NewPrimitiveArrayInstruction n -> newArray(i, n.typeKind(), step.operands().getFirst());
            case NewReferenceArrayInstruction n -> newArray(i, TypeKind.REFERENCE, step.operands().getFirst());
            case InvokeInstruction inv -> invoke(i, inv, step);
            case StoreInstruction s -> store(i, s, step.operands().getFirst());
            case IncrementInstruction inc -> increment(i, inc);
            case OperatorInstruction o -> operator(i, o.opcode(), step.operands());
            case BranchInstruction b -> fullValues(i, step.operands(), "compared");
            case TableSwitchInstruction t -> fullValues(i, step.operands(), "used as switch key");
            case LookupSwitchInstruction l -> fullValues(i, step.operands(), "used as switch key");
            case ReturnInstruction r -> fullValues(i, step.operands(), "returned");
            default -> { /* no int rule */ }
        }
    }

    private void constant(int i, ConstantInstruction c) {
        if (!intSupported && c.constantValue() instanceof Integer v
                && (v < Short.MIN_VALUE || v > Short.MAX_VALUE)) {
            report(i, "int constant " + v + " is outside the short range and needs int support" + SPEC);
        }
    }

    private void field(int i, FieldInstruction f) {
        String owner = f.owner().asInternalName();
        if (!isJavaSeClass(owner)) {
            declaredType(i, f.typeSymbol().descriptorString(),
                    "field " + owner + "." + f.name().stringValue());
        }
    }

    private void declaredType(int i, String descriptor, String what) {
        if (!intSupported && isIntType(descriptor)) {
            report(i, what + " of type " + (descriptor.equals("I") ? "int" : "int[]")
                    + " needs int support" + SPEC);
        }
    }

    private void arrayAccess(int i, TypeKind element, FlowValue index) {
        if (!intSupported && element == TypeKind.INT) {
            report(i, "int[] element access needs int support" + SPEC);
        }
        shortIndex(i, index, "array index");
    }

    private void newArray(int i, TypeKind element, FlowValue count) {
        if (!intSupported && element == TypeKind.INT) {
            report(i, "int[] creation needs int support" + SPEC);
        }
        shortIndex(i, count, "array size");
    }

    /**
     * JCVM 3.1 §2.2.1.1.8: "The Java Card platform only supports array indexing using the short
     * data type" (and §7.5.71: a short count). The index or size must be a short value in the
     * Java Card sense: its value stays in the short range for every value of the types it is
     * computed from, where a local variable stands for any value of its declared type
     * ({@link #javaCardRange}). So an int variable, field, parameter or result is rejected even
     * when its value would fit, as is {@code buf[off + 1]} with a short {@code off}, while casts,
     * constants and expressions such as {@code b[x & 3]} or {@code buf[a + 1]} with a byte
     * {@code a} are short values. Without int support an int source is reported as such already.
     */
    private void shortIndex(int i, FlowValue v, String what) {
        if (v.kind() == ValueKind.INT && !intSupported) {
            return;
        }
        ValueRange typed = javaCardRange(v, new HashMap<>());
        if (v.kind() != ValueKind.SHORT || typed == null || typed.kind() != ValueKind.SHORT) {
            report(i, "int expression used as " + what + ": cast it to short or byte (JCVM 3.1"
                    + " §2.2.1.1.8: arrays are indexed with short values only)");
        }
    }

    /**
     * Range of a value in the Java Card type system: the data flow range, except that a load of a
     * local variable has the range of the variable's declared type (LocalVariableTable) instead
     * of the range of the values assigned to it, and an iinc result is an int (javac increments
     * int variables only). A cycle falls back to the data flow range.
     */
    private ValueRange javaCardRange(FlowValue v, Map<FlowValue, ValueRange> memo) {
        if (memo.containsKey(v)) {
            return memo.get(v);
        }
        memo.put(v, v.range());
        ValueRange range = switch (v.op) {
            case SOURCE -> v.range();
            case INC -> ValueRange.INT;
            case COPY -> declaredRange(v).orElseGet(() -> javaCardRange(v.inputs.getFirst(), memo));
            case PHI -> v.inputs.stream().map(in -> javaCardRange(in, memo))
                    .reduce(null, ValueRange::join);
            default -> ValueTransfer.apply(v.op, v.inputs.stream()
                    .map(in -> javaCardRange(in, memo)).toList());
        };
        memo.put(v, range);
        return range;
    }

    /** The range of the declared type of the local variable a load reads, if the LVT has it. */
    private Optional<ValueRange> declaredRange(FlowValue copy) {
        if (copy.producer < 0 || !(flow.instruction(copy.producer) instanceof LoadInstruction load)
                || load.typeKind() != TypeKind.INT) {
            return Optional.empty();
        }
        int bci = flow.bci(copy.producer);
        for (CodeElement e : flow.method().code().orElseThrow()) {
            if (e instanceof LocalVariable lv && lv.slot() == load.slot()
                    && flow.bci(flow.indexOf(lv.startScope())) <= bci && bci < endBci(lv)) {
                return Optional.of(ValueRange.ofType(lv.typeSymbol().descriptorString()));
            }
        }
        return Optional.empty();
    }

    private int endBci(LocalVariable lv) {
        int end = flow.indexOf(lv.endScope());
        return end < flow.size() ? flow.bci(end) : Integer.MAX_VALUE;
    }

    private void invoke(int i, InvokeInstruction inv, IntFlow.Step step) {
        String desc = inv.typeSymbol().descriptorString();
        String target = inv.owner().asInternalName() + "." + inv.name().stringValue() + desc;
        if (!intSupported && usesIntInSignature(desc) && !isJavaSeClass(inv.owner().asInternalName())) {
            report(i, "call of " + target + " uses the int type and needs int support" + SPEC);
        }
        List<String> params = Descriptors.parameters(desc);
        int first = step.operands().size() - params.size();
        for (int p = 0; p < params.size(); p++) {
            if ("BSZ".indexOf(params.get(p).charAt(0)) >= 0
                    && step.operands().get(first + p).kind() != ValueKind.SHORT) {
                report(i, "int intermediate value passed as a short/byte argument of " + target + CAST);
            }
        }
    }

    /**
     * Whether a class belongs to the Java SE library. The Java Card java.* packages declare only
     * constructors and {@code equals} (no member with int in its signature, no field), so a Java
     * SE member with int in its signature is not part of the Java Card platform at all (JCVM 3.1
     * §2.2.1.4); the link check against the export files reports it as such, with the target
     * version. Reporting it as a use of the int type would suggest that int support helps.
     */
    private static boolean isJavaSeClass(String owner) {
        return owner.startsWith("java/") || owner.startsWith("javax/");
    }

    private static boolean usesIntInSignature(String desc) {
        if (isIntType(Descriptors.returnType(desc))) {
            return true;
        }
        return Descriptors.parameters(desc).stream().anyMatch(IntRules::isIntType);
    }

    private void store(int i, StoreInstruction s, FlowValue value) {
        if (!intSupported && s.typeKind() == TypeKind.INT && value.kind() == ValueKind.LOW16) {
            report(i, "int intermediate value stored in local variable " + s.slot()
                    + " (a local variable of type int)" + CAST);
        }
    }

    /** javac uses iinc only for int local variables (also the hidden index of an enhanced for loop). */
    private void increment(int i, IncrementInstruction inc) {
        if (!intSupported) {
            report(i, "local variable " + inc.slot() + " is incremented as an int (iinc):"
                    + " a local variable of type int needs int support" + SPEC
                    + "; an int loop counter or an enhanced for loop over an array (JCVM 3.1"
                    + " §2.2.1.1.8) needs a short counter instead");
        }
    }

    private void operator(int i, Opcode op, List<FlowValue> operands) {
        switch (op) {
            case IDIV, IREM -> fullValues(i, operands, "divided");
            case ISHR, IUSHR -> fullValues(i, operands.subList(0, 1), "shifted right");
            default -> { /* ring operations keep the low 16 bits exact */ }
        }
    }

    /** Consumers whose result depends on all 32 bits of their operands. */
    private void fullValues(int i, List<FlowValue> operands, String use) {
        if (intSupported) {
            return;
        }
        for (FlowValue v : operands) {
            if (v.kind() == ValueKind.LOW16) {
                report(i, "int intermediate value " + use + CAST);
                return;
            }
        }
    }

    private void report(int i, String message) {
        int bci = i < 0 ? -1 : flow.bci(i);
        int line = i < 0 ? firstLine() : flow.line(i);
        IntIssue issue = new IntIssue(bci, line, message);
        if (!issues.contains(issue)) {
            issues.add(issue);
        }
    }

    private int firstLine() {
        for (int i = 0; i < flow.size(); i++) {
            if (flow.line(i) >= 0) {
                return flow.line(i);
            }
        }
        return -1;
    }
}
