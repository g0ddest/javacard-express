package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.resolve.ReferenceResolver;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static name.velikodniy.jcexpress.converter.translate.JcvmOpcode.*;

/**
 * Translates the code of one JVM method into symbolic JCVM code (JCVM 3.1 Chapter 7).
 *
 * <p>Every JVM instruction is either mapped to its exact JCVM counterpart or rejected with a
 * {@link TranslationException} naming the class, method and source line; nothing is dropped.
 * Two peephole patterns of the Java Card instruction set are applied on straight-line code (no
 * branch target or handler boundary in between): {@code aload_0; getfield} becomes
 * {@code getfield_<t>_this} (§7.5.21) and {@code aload_0; <push>; putfield} becomes
 * {@code <push>; putfield_<t>_this} (§7.5.76), both only in instance methods that never
 * overwrite local 0. Short increments of local variables become {@code sinc} (§7.5.89,
 * {@link JcvmPeephole}).
 *
 * <p>Label positions do not depend on the {@code StackMapTable} attribute, which JCVM 3.1
 * §2.3.1.2.7 does not require: the JDK ClassFile API derives the branch targets it delivers as
 * {@code Label} elements from that table, so in a class file of version 51 or later without one
 * (ProGuard {@code -dontpreverify}, ASM {@code COMPUTE_MAXS}) the used labels missing from the
 * element stream are bound at their {@link CodeAttribute#labelToBci bytecode index} instead.
 */
final class MethodTranslator {

    private final MethodModel method;
    private final ClassModel classModel;
    private final CodeModel code;
    private final TranslationOptions options;
    private final CodeEmitter emit;
    private final MemberInstructions members;
    private final List<CodeElement> elements;
    private final Set<Label> usedLabels = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Used labels that the element stream does not deliver, by bytecode index (class javadoc). */
    private final Map<Integer, List<Label>> undeliveredLabels = new HashMap<>();
    private final boolean thisAvailable;
    private final int[] bciOf;
    private TypedInstructions typed;
    private int line = -1;

    MethodTranslator(MethodModel method, ClassModel classModel, ReferenceResolver resolver,
                     TranslationOptions options) {
        this.method = method;
        this.classModel = classModel;
        this.code = method.code().orElseThrow();
        this.options = options;
        this.emit = new CodeEmitter(resolver);
        this.members = new MemberInstructions(emit, resolver, options.supportInt32());
        this.elements = code.elementList();
        this.thisAvailable = BytecodeTranslator.thisAvailable(method, code);
        this.bciOf = bytecodeIndices(elements);
    }

    /** JVM bytecode index of every instruction element (-1 for pseudo-instructions). */
    private static int[] bytecodeIndices(List<CodeElement> elements) {
        int[] bci = new int[elements.size()];
        int pc = 0;
        for (int i = 0; i < bci.length; i++) {
            if (elements.get(i) instanceof Instruction insn) {
                bci[i] = pc;
                pc += insn.sizeInBytes();
            } else {
                bci[i] = -1;
            }
        }
        return bci;
    }

    /** Translates the method; failures are reported with class, method and source line. */
    TranslatedMethod translate() {
        try {
            if (options.supportInt32()) {
                // int support (JCVM 3.1 §2.2.3.1): each value is a short or an int (IntPlan)
                typed = new TypedInstructions(emit, members, IntPlan.build(method),
                        method.methodType().stringValue());
            }
            return translateCode().assemble(new int[0]);
        } catch (TranslationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw failure(e.getMessage(), e);
        }
    }

    TranslationException failure(String message, Throwable cause) {
        return new TranslationException(classModel.thisClass().asInternalName(),
                method.methodName().stringValue() + method.methodType().stringValue(),
                sourceFile(), line, message, cause);
    }

    private String sourceFile() {
        return classModel.findAttribute(Attributes.sourceFile())
                .map(sf -> sf.sourceFile().stringValue()).orElse(null);
    }

    /** Class, method and source file of the translated method, for diagnostics. */
    private String origin() {
        String where = classModel.thisClass().asInternalName() + "."
                + method.methodName().stringValue() + method.methodType().stringValue();
        String file = sourceFile();
        return file == null ? where : where + " (" + file + ")";
    }

    private JcvmCode translateCode() {
        collectUsedLabels();
        collectUndeliveredLabels();
        List<JcvmCode.Handler> handlers = symbolicHandlers();
        for (int i = 0; i < elements.size(); ) {
            i = translateElement(i);
        }
        markUndelivered(codeLength());
        List<JcvmInsn> insns = JcvmPeephole.shortIncrements(emit.instructions());
        if (typed != null) {
            IntPlan plan = typed.plan();
            return new JcvmCode(insns, handlers, plan.maxStack(), plan.maxLocals(), plan.nargs(), origin());
        }
        int nargs = argumentWords();
        int maxStack = code instanceof CodeAttribute ca ? ca.maxStack() : 0;
        int jvmMaxLocals = code instanceof CodeAttribute ca ? ca.maxLocals() : 0;
        return new JcvmCode(insns, handlers, maxStack, Math.max(0, jvmMaxLocals - nargs), nargs, origin());
    }

    private void collectUsedLabels() {
        for (CodeElement e : elements) {
            switch (e) {
                case BranchInstruction b -> usedLabels.add(b.target());
                case TableSwitchInstruction t -> {
                    usedLabels.add(t.defaultTarget());
                    t.cases().forEach(c -> usedLabels.add(c.target()));
                }
                case LookupSwitchInstruction l -> {
                    usedLabels.add(l.defaultTarget());
                    l.cases().forEach(c -> usedLabels.add(c.target()));
                }
                case ExceptionCatch c -> {
                    usedLabels.add(c.tryStart());
                    usedLabels.add(c.tryEnd());
                    usedLabels.add(c.handler());
                }
                default -> { /* no labels */ }
            }
        }
    }

    /**
     * Groups the used labels that are not {@code Label} elements of the code by bytecode index.
     *
     * @throws IllegalStateException if such a label is not bound to the start of an instruction or
     *                               to the end of the code
     */
    private void collectUndeliveredLabels() {
        Set<Label> delivered = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Integer> boundaries = new HashSet<>();
        for (int i = 0; i < elements.size(); i++) {
            switch (elements.get(i)) {
                case Label l -> delivered.add(l);
                case Instruction insn -> boundaries.add(bciOf[i]);
                default -> { /* no position */ }
            }
        }
        boundaries.add(codeLength());
        for (Label l : usedLabels) {
            if (delivered.contains(l)) {
                continue;
            }
            int bci = code instanceof CodeAttribute ca ? ca.labelToBci(l) : -1;
            if (!boundaries.contains(bci)) {
                throw new IllegalStateException("a branch target (bytecode index " + bci
                        + ") is not the start of an instruction");
            }
            undeliveredLabels.computeIfAbsent(bci, k -> new ArrayList<>()).add(l);
        }
    }

    /** Binds the undelivered labels at bytecode index {@code bci} to the current position. */
    private void markUndelivered(int bci) {
        for (Label l : undeliveredLabels.getOrDefault(bci, List.of())) {
            emit.mark(l);
        }
    }

    /** Length of the JVM code in bytes (the bytecode index just after the last instruction). */
    private int codeLength() {
        for (int i = elements.size() - 1; i >= 0; i--) {
            if (elements.get(i) instanceof Instruction insn) {
                return bciOf[i] + insn.sizeInBytes();
            }
        }
        return 0;
    }

    /**
     * Builds the handler table in JVM order. Catch types are resolved before the code, so their
     * class references come first in the constant pool (the converter's CP order relies on it).
     */
    private List<JcvmCode.Handler> symbolicHandlers() {
        List<JcvmCode.Handler> handlers = new ArrayList<>();
        for (ExceptionCatch c : code.exceptionHandlers()) {
            int type = c.catchType().map(t -> emit.catchType(t.asInternalName()))
                    .orElse(JcvmCode.FINALLY);
            handlers.add(new JcvmCode.Handler(emit.label(c.tryStart()), emit.label(c.tryEnd()),
                    emit.label(c.handler()), type));
        }
        return handlers;
    }

    private int argumentWords() {
        int words = Descriptors.argumentSlots(method.methodType().stringValue());
        return (method.flags().flagsMask() & ClassFile.ACC_STATIC) != 0 ? words : words + 1;
    }

    /** Translates the element at {@code i} and returns the index of the next untranslated one. */
    private int translateElement(int i) {
        CodeElement e = elements.get(i);
        switch (e) {
            case Label label -> emit.mark(label);
            case LineNumber ln -> line = ln.line();
            case Instruction insn -> {
                markUndelivered(bciOf[i]);
                return translateInstruction(insn, i);
            }
            default -> { /* exception table entries, local variable tables, stack maps */ }
        }
        return i + 1;
    }

    private int translateInstruction(Instruction insn, int i) {
        if (insn instanceof LoadInstruction load && load.typeKind() == TypeKind.REFERENCE
                && load.slot() == 0 && thisAvailable) {
            return translateAload0(i);
        }
        translateSingle(insn, bciOf[i]);
        return i + 1;
    }

    private void translateSingle(Instruction insn, int bci) {
        if (typed == null) {
            translateShort(insn);
            return;
        }
        IntFlow.Step step = typed.plan().step(bci);
        switch (insn) {
            case LoadInstruction l when l.typeKind() == TypeKind.INT -> typed.load(l.slot(), step);
            case StoreInstruction s when s.typeKind() == TypeKind.INT -> typed.store(s.slot(), step);
            case LoadInstruction l -> typed.referenceLocal(l.slot(), ALOAD, ALOAD_0);
            case StoreInstruction s -> typed.referenceLocal(s.slot(), ASTORE, ASTORE_0);
            case TableSwitchInstruction t -> translateTableSwitch(t, typed.intKey(step));
            case LookupSwitchInstruction l -> translateLookupSwitch(l, typed.intKey(step));
            default -> {
                if (!typed.translate(insn, bci)) {
                    translateShort(insn);
                }
            }
        }
    }

    /** Translation without int support, and of the instructions int support does not change. */
    @SuppressWarnings("java:S1541") // one case per instruction family of the ClassFile API
    private void translateShort(Instruction insn) {
        switch (insn) {
            case NopInstruction n -> emit.op(NOP);
            case ConstantInstruction c -> translateConstant(c);
            case LoadInstruction l -> translateLoad(l.typeKind(), l.slot());
            case StoreInstruction s -> translateStore(s.typeKind(), s.slot());
            case IncrementInstruction inc -> translateIncrement(inc);
            case ArrayLoadInstruction a -> emit.op(arrayOpcode(a.typeKind(), AALOAD, BALOAD, SALOAD, IALOAD));
            case ArrayStoreInstruction a -> emit.op(arrayOpcode(a.typeKind(), AASTORE, BASTORE, SASTORE, IASTORE));
            case StackInstruction s -> emit.op(OpcodeMap.stack(s.opcode()), OpcodeMap.stackOperands(s.opcode()));
            case OperatorInstruction o -> emit.op(OpcodeMap.operator(o.opcode(), false));
            case ConvertInstruction c -> translateConvert(c);
            case BranchInstruction b -> emit.branch(OpcodeMap.branch(b.opcode()), b.target());
            case ReturnInstruction r -> emit.op(returnOpcode(r.typeKind()));
            case TableSwitchInstruction t -> translateTableSwitch(t, false);
            case LookupSwitchInstruction l -> translateLookupSwitch(l, false);
            case ThrowInstruction t -> emit.op(ATHROW);
            case FieldInstruction f -> members.field(f);
            case InvokeInstruction inv -> members.invoke(inv);
            case NewObjectInstruction n -> emit.classRefOp(NEW, n.className().asInternalName());
            case NewPrimitiveArrayInstruction n -> emit.op(NEWARRAY, MemberInstructions.arrayType(n.typeKind()));
            case NewReferenceArrayInstruction n -> members.newReferenceArray(n);
            case TypeCheckInstruction t -> members.typeCheck(t);
            default -> throw new IllegalStateException(OpcodeMap.unsupported(insn));
        }
    }

    // ── aload_0 peepholes (getfield_<t>_this §7.5.21, putfield_<t>_this §7.5.76) ──

    private int translateAload0(int i) {
        int next = nextInstruction(i);
        if (next >= 0 && elements.get(next) instanceof FieldInstruction f
                && f.opcode() == Opcode.GETFIELD) {
            skipTo(i, next);
            members.thisField(JcvmInsn.FieldOp.GET_THIS, f, -1);
            if (typed != null) {
                typed.afterGetfieldThis(f, typed.plan().step(bciOf[next]));
            }
            return next + 1;
        }
        int store = next >= 0 ? nextInstruction(next) : -1;
        if (options.optimizePutfieldThis() && store >= 0 && isSimplePush(elements.get(next))
                && elements.get(store) instanceof FieldInstruction f && f.opcode() == Opcode.PUTFIELD) {
            int anchor = emit.anchor();
            skipTo(i, next);
            translateSingle((Instruction) elements.get(next), bciOf[next]);
            skipTo(next, store);
            if (typed != null) {
                typed.beforePutfieldThis(f, typed.plan().step(bciOf[store]));
            }
            members.thisField(JcvmInsn.FieldOp.PUT_THIS, f, anchor);
            return store + 1;
        }
        emit.op(ALOAD_0);
        return i + 1;
    }

    /**
     * Index of the next instruction after {@code i}, or -1 if a used label (delivered or not) or
     * the end comes first.
     */
    private int nextInstruction(int i) {
        for (int j = i + 1; j < elements.size(); j++) {
            CodeElement e = elements.get(j);
            if (e instanceof Instruction) {
                return undeliveredLabels.containsKey(bciOf[j]) ? -1 : j;
            }
            if (e instanceof Label l && usedLabels.contains(l)) {
                return -1;
            }
        }
        return -1;
    }

    /** Processes the pseudo-instructions strictly between {@code from} and {@code to}. */
    private void skipTo(int from, int to) {
        for (int j = from + 1; j < to; j++) {
            translateElement(j);
        }
    }

    private static boolean isSimplePush(CodeElement e) {
        return e instanceof ConstantInstruction
                || (e instanceof LoadInstruction l && !(l.typeKind() == TypeKind.REFERENCE && l.slot() == 0));
    }

    // ── constants, locals ──

    private void translateConstant(ConstantInstruction c) {
        Object value = c.constantValue();
        if (c.opcode() == Opcode.ACONST_NULL) {
            emit.op(ACONST_NULL);
        } else if (value instanceof Integer v) {
            Constants.push(emit, v, false);
        } else {
            throw new IllegalStateException("unsupported constant " + value + " ("
                    + (value == null ? "null" : value.getClass().getSimpleName()) + "): JCVM 3.1"
                    + " §2.2.1.4 supports no String or Class constants and §2.2.1.3 no long, float"
                    + " or double values");
        }
    }

    private void translateLoad(TypeKind kind, int slot) {
        switch (kind) {
            case REFERENCE -> localOp(slot, ALOAD, ALOAD_0);
            case INT -> localOp(slot, SLOAD, SLOAD_0);
            default -> throw new IllegalStateException("unsupported local variable type " + kind);
        }
    }

    private void translateStore(TypeKind kind, int slot) {
        switch (kind) {
            case REFERENCE -> localOp(slot, ASTORE, ASTORE_0);
            case INT -> localOp(slot, SSTORE, SSTORE_0);
            default -> throw new IllegalStateException("unsupported local variable type " + kind);
        }
    }

    /** JCVM 3.1 §7.5.4/§7.5.5 etc.: {@code <op>_<n>} for locals 0-3, {@code <op> index} above. */
    private void localOp(int slot, int opcode, int shortForm0) {
        if (slot > 0xFF) {
            throw new IllegalStateException("local variable index " + slot
                    + " exceeds 255 (JCVM 3.1 §2.2.4.4)");
        }
        if (slot <= 3) {
            emit.op(shortForm0 + slot);
        } else {
            emit.op(opcode, slot);
        }
    }

    // JVM iinc → JCVM sinc/sinc_w (§7.5.89/§7.5.90); with int support see TypedInstructions
    private void translateIncrement(IncrementInstruction inc) {
        boolean wide = inc.constant() < Byte.MIN_VALUE || inc.constant() > Byte.MAX_VALUE;
        int opcode = wide ? SINC_W : SINC;
        if (wide) {
            emit.op(opcode, inc.slot(), inc.constant() >> 8, inc.constant());
        } else {
            emit.op(opcode, inc.slot(), inc.constant());
        }
    }

    private static int arrayOpcode(TypeKind kind, int ref, int byteOrBoolean, int shortOp, int intOp) {
        return switch (kind) {
            case REFERENCE -> ref;
            case BYTE, BOOLEAN -> byteOrBoolean;
            case SHORT -> shortOp;
            case INT -> intOp;
            default -> throw new IllegalStateException("unsupported array element type " + kind
                    + " (JCVM 3.1 §2.2.1.3: char, long, float and double are not supported)");
        };
    }

    private void translateConvert(ConvertInstruction c) {
        switch (c.opcode()) {
            case I2B -> emit.op(S2B);
            case I2S -> { /* a short is already a short (§2.2.3.1: only exact values reach here) */ }
            default -> throw new IllegalStateException(OpcodeMap.unsupported(c));
        }
    }

    private int returnOpcode(TypeKind kind) {
        return switch (kind) {
            case VOID -> RETURN;
            case REFERENCE -> ARETURN;
            case INT -> SRETURN;
            default -> throw new IllegalStateException("unsupported return type " + kind
                    + " (JCVM 3.1 §2.2.1.3)");
        };
    }

    // ── switches: stableswitch §7.5.106, slookupswitch §7.5.94 ──

    /**
     * stableswitch / itableswitch have one offset per key from low to high (§7.5.106, §7.5.66);
     * the ClassFile API lists only the explicit cases, so keys without a case get the default
     * target.
     */
    private void translateTableSwitch(TableSwitchInstruction t, boolean intKey) {
        if (!intKey) {
            Constants.requireShortKeys(t.lowValue(), t.highValue());
        }
        List<JcvmLabel> targets = new ArrayList<>();
        Map<Integer, Label> byKey = new HashMap<>();
        t.cases().forEach(c -> byKey.put(c.caseValue(), c.target()));
        for (long key = t.lowValue(); key <= t.highValue(); key++) {
            targets.add(emit.label(byKey.getOrDefault((int) key, t.defaultTarget())));
        }
        emit.add(new JcvmInsn.TableSwitch(intKey ? ITABLESWITCH : STABLESWITCH,
                emit.label(t.defaultTarget()), t.lowValue(), t.highValue(), targets));
    }

    /** slookupswitch / ilookupswitch (§7.5.94, §7.5.50). */
    private void translateLookupSwitch(LookupSwitchInstruction l, boolean intKey) {
        int[] keys = new int[l.cases().size()];
        List<JcvmLabel> targets = new ArrayList<>();
        for (int k = 0; k < keys.length; k++) {
            keys[k] = l.cases().get(k).caseValue();
            targets.add(emit.label(l.cases().get(k).target()));
        }
        if (keys.length > 0 && !intKey) {
            Constants.requireShortKeys(keys[0], keys[keys.length - 1]);
        }
        emit.add(new JcvmInsn.LookupSwitch(intKey ? ILOOKUPSWITCH : SLOOKUPSWITCH,
                emit.label(l.defaultTarget()), keys, targets));
    }
}
