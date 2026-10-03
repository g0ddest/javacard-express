package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.resolve.ReferenceResolver;

import java.lang.classfile.Label;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects the symbolic JCVM instructions of one method and resolves their constant pool
 * operands through the {@link ReferenceResolver}. Without a resolver (placeholder mode, used to
 * test translation in isolation) every constant pool index is 0.
 */
final class CodeEmitter {

    private static final byte[] NO_BYTES = {};

    private final ReferenceResolver resolver;
    private final List<JcvmInsn> insns = new ArrayList<>();
    private final Map<Label, JcvmLabel> labels = new IdentityHashMap<>();
    private int anchors;

    CodeEmitter(ReferenceResolver resolver) {
        this.resolver = resolver;
    }

    List<JcvmInsn> instructions() {
        return insns;
    }

    /** Returns the symbolic label for a JVM label (created on first use). */
    JcvmLabel label(Label jvmLabel) {
        return labels.computeIfAbsent(jvmLabel, l -> new JcvmLabel());
    }

    /** Emits an instruction with fixed operand bytes (each operand is truncated to a byte). */
    void op(int opcode, int... operands) {
        byte[] bytes = new byte[operands.length];
        for (int i = 0; i < operands.length; i++) {
            bytes[i] = (byte) operands[i];
        }
        insns.add(new JcvmInsn.Plain(opcode, bytes));
    }

    /** Emits an instruction with a 2-byte signed or unsigned operand. */
    void opU2(int opcode, int value) {
        op(opcode, value >> 8, value);
    }

    void mark(Label jvmLabel) {
        insns.add(new JcvmInsn.Mark(label(jvmLabel)));
    }

    void branch(int opcode, Label target) {
        insns.add(new JcvmInsn.Branch(opcode, label(target)));
    }

    void add(JcvmInsn insn) {
        insns.add(insn);
    }

    /** Emits a new {@link JcvmInsn.ThisAnchor} and returns its id. */
    int anchor() {
        int id = anchors++;
        insns.add(new JcvmInsn.ThisAnchor(id));
        return id;
    }

    /** Emits {@code opcode prefix u2(classRef) } for a class or interface. */
    void classRefOp(int opcode, byte[] prefix, String internalName) {
        int index = resolver == null ? 0 : resolver.resolveClassRef(internalName);
        insns.add(new JcvmInsn.CpRef(opcode, prefix, index, NO_BYTES));
    }

    void classRefOp(int opcode, String internalName) {
        classRefOp(opcode, NO_BYTES, internalName);
    }

    /** Emits an invoke instruction with a method reference (JCVM 3.1 §7.5.54-§7.5.57). */
    void methodRefOp(int opcode, String owner, String name, String desc,
                     ReferenceResolver.InvokeKind kind) {
        int index = resolver == null ? 0 : resolver.resolveMethodRef(owner, name, desc, kind);
        insns.add(new JcvmInsn.CpRef(opcode, NO_BYTES, index, NO_BYTES));
    }

    /** Emits {@code invokeinterface nargs, u2(classRef), token} (JCVM 3.1 §7.5.54). */
    void invokeInterface(int nargs, String owner, String name, String desc) {
        if (resolver == null) {
            insns.add(new JcvmInsn.CpRef(JcvmOpcode.INVOKEINTERFACE, new byte[]{(byte) nargs}, 0,
                    new byte[]{0}));
            return;
        }
        ReferenceResolver.InterfaceMethodRef ref = resolver.resolveInterfaceMethodRef(owner, name, desc);
        insns.add(new JcvmInsn.CpRef(JcvmOpcode.INVOKEINTERFACE, new byte[]{(byte) nargs},
                ref.cpIndex(), new byte[]{(byte) ref.methodToken()}));
    }

    /** Emits getstatic_&lt;t&gt; / putstatic_&lt;t&gt; with a 2-byte static field reference. */
    void staticFieldOp(int opcode, String owner, String name, String desc) {
        int index = resolver == null ? 0 : resolver.resolveFieldRef(owner, name, desc, true);
        insns.add(new JcvmInsn.CpRef(opcode, NO_BYTES, index, NO_BYTES));
    }

    /** Emits an instance field instruction; the index width is chosen at assembly time. */
    void instanceFieldOp(JcvmInsn.FieldOp op, int type, String owner, String name, String desc,
                         int anchor) {
        int index = resolver == null ? 0 : resolver.resolveFieldRef(owner, name, desc, false);
        insns.add(new JcvmInsn.FieldRef(op, type, index, anchor));
    }

    /** Resolves the constant pool index of a catch type (JCVM 3.1 §6.10.3). */
    int catchType(String internalName) {
        return resolver == null ? 0 : resolver.resolveClassRef(internalName);
    }

    boolean hasResolver() {
        return resolver != null;
    }

    ReferenceResolver resolver() {
        return resolver;
    }
}
