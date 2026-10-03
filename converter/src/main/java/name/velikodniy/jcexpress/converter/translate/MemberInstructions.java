package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.resolve.ReferenceResolver;

import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;

/**
 * Translates the JVM instructions that reference classes, fields and methods through the
 * constant pool (JCVM 3.1 §7.5.6 anewarray, §7.5.16 checkcast, §7.5.20-§7.5.23 field access,
 * §7.5.53 instanceof, §7.5.54-§7.5.57 invocations, §7.5.75-§7.5.78 field stores).
 */
final class MemberInstructions {

    /** JCVM 3.1 Table 7-2 array types. */
    static final int T_BOOLEAN = 10;
    static final int T_BYTE = 11;
    static final int T_SHORT = 12;
    static final int T_INT = 13;
    static final int T_REFERENCE = 14;

    private final CodeEmitter emit;
    private final ReferenceResolver resolver;
    private final boolean intWords;

    /**
     * @param emit     code emitter
     * @param resolver reference resolver, or {@code null} in placeholder mode
     * @param intWords whether int parameters take two words (int support, JCVM 3.1 §6.10.4)
     */
    MemberInstructions(CodeEmitter emit, ReferenceResolver resolver, boolean intWords) {
        this.emit = emit;
        this.resolver = resolver;
        this.intWords = intWords;
    }

    /**
     * JCVM field type index of a field descriptor: 0 = reference, 1 = byte/boolean, 2 = short,
     * 3 = int (the order of the {@code _a/_b/_s/_i} opcode forms).
     */
    static int fieldType(String descriptor) {
        return switch (descriptor.charAt(0)) {
            case 'L', '[' -> 0;
            case 'B', 'Z' -> 1;
            case 'S' -> 2;
            case 'I' -> 3;
            default -> throw new IllegalStateException("unsupported field type " + descriptor
                    + " (JCVM 3.1 §2.2.1.3: char, long, float and double are not supported)");
        };
    }

    /** Array type code of newarray (JCVM 3.1 §7.5.71, Table 7-2). */
    static int arrayType(TypeKind kind) {
        return switch (kind) {
            case BOOLEAN -> T_BOOLEAN;
            case BYTE -> T_BYTE;
            case SHORT -> T_SHORT;
            case INT -> T_INT;
            default -> throw new IllegalStateException("unsupported array type " + kind
                    + "[] (JCVM 3.1 §2.2.1.3: char, long, float and double are not supported)");
        };
    }

    void field(FieldInstruction f) {
        String desc = f.typeSymbol().descriptorString();
        int type = fieldType(desc);
        String owner = f.owner().asInternalName();
        String name = f.name().stringValue();
        switch (f.opcode()) {
            case GETSTATIC -> emit.staticFieldOp(JcvmOpcode.GETSTATIC_A + type, owner, name, desc);
            case PUTSTATIC -> emit.staticFieldOp(JcvmOpcode.PUTSTATIC_A + type, owner, name, desc);
            case GETFIELD -> emit.instanceFieldOp(JcvmInsn.FieldOp.GET, type, owner, name, desc, -1);
            case PUTFIELD -> emit.instanceFieldOp(JcvmInsn.FieldOp.PUT, type, owner, name, desc, -1);
            default -> throw new IllegalStateException("unsupported field instruction " + f.opcode());
        }
    }

    /** Emits a {@code getfield_<t>_this} or {@code putfield_<t>_this} for the field of {@code f}. */
    void thisField(JcvmInsn.FieldOp op, FieldInstruction f, int anchor) {
        String desc = f.typeSymbol().descriptorString();
        emit.instanceFieldOp(op, fieldType(desc), f.owner().asInternalName(),
                f.name().stringValue(), desc, anchor);
    }

    void invoke(InvokeInstruction inv) {
        String owner = inv.owner().asInternalName();
        String name = inv.name().stringValue();
        String desc = inv.typeSymbol().descriptorString();
        switch (inv.opcode()) {
            case INVOKEVIRTUAL -> invokeVirtual(owner, name, desc);
            case INVOKESPECIAL -> emit.methodRefOp(JcvmOpcode.INVOKESPECIAL, owner, name, desc,
                    ReferenceResolver.InvokeKind.SPECIAL);
            case INVOKESTATIC -> emit.methodRefOp(JcvmOpcode.INVOKESTATIC, owner, name, desc,
                    ReferenceResolver.InvokeKind.STATIC);
            case INVOKEINTERFACE -> invokeInterface(owner, name, desc);
            default -> throw new IllegalStateException("unsupported invocation " + inv.opcode());
        }
    }

    /**
     * invokeinterface (§7.5.54) with the interface method token, except for a public method of
     * {@code java.lang.Object} that the interface does not have (e.g. {@code iface.equals(x)}, JLS 9.2,
     * JVMS 5.4.3.4): that one is an invokevirtual through {@code Object}'s virtual method token (§7.5.57).
     */
    private void invokeInterface(String owner, String name, String desc) {
        if (resolver != null && resolver.isObjectMethodOfInterface(owner, name, desc)) {
            emit.methodRefOp(JcvmOpcode.INVOKEVIRTUAL, "java/lang/Object", name, desc,
                    ReferenceResolver.InvokeKind.VIRTUAL);
        } else {
            emit.invokeInterface(Descriptors.argumentWords(desc, intWords) + 1, owner, name, desc);
        }
    }

    /**
     * Since JEP 181 (Java 11) javac calls private instance methods of the same class with
     * invokevirtual; the JCVM invokes private methods with invokespecial and a
     * CONSTANT_StaticMethodref (JCVM 3.1 §7.5.55).
     */
    private void invokeVirtual(String owner, String name, String desc) {
        if (resolver != null && resolver.isPrivateInstanceMethod(owner, name, desc)) {
            emit.methodRefOp(JcvmOpcode.INVOKESPECIAL, owner, name, desc,
                    ReferenceResolver.InvokeKind.SPECIAL);
        } else {
            emit.methodRefOp(JcvmOpcode.INVOKEVIRTUAL, owner, name, desc,
                    ReferenceResolver.InvokeKind.VIRTUAL);
        }
    }

    /** anewarray (JCVM 3.1 §7.5.6): arrays of class or interface types only (§2.2.1.3). */
    void newReferenceArray(NewReferenceArrayInstruction n) {
        String component = n.componentType().asInternalName();
        if (component.startsWith("[")) {
            throw new IllegalStateException("unsupported array of arrays " + component
                    + "[] (JCVM 3.1 §2.2.1.3: arrays of more than one dimension are not supported)");
        }
        emit.classRefOp(JcvmOpcode.ANEWARRAY, component);
    }

    /**
     * checkcast (§7.5.16) / instanceof (§7.5.53): atype 0 with a class reference for classes and
     * interfaces; Table 7-2 array types 10..13 with a zero index, 14 with the element class.
     */
    void typeCheck(TypeCheckInstruction t) {
        int opcode = t.opcode() == Opcode.CHECKCAST ? JcvmOpcode.CHECKCAST : JcvmOpcode.INSTANCEOF;
        String type = t.type().asInternalName();
        if (!type.startsWith("[")) {
            emit.classRefOp(opcode, new byte[]{0}, type);
            return;
        }
        int atype = switch (type.charAt(1)) {
            case 'Z' -> T_BOOLEAN;
            case 'B' -> T_BYTE;
            case 'S' -> T_SHORT;
            case 'I' -> T_INT;
            case 'L' -> T_REFERENCE;
            default -> throw new IllegalStateException("unsupported array type " + type
                    + " in checkcast/instanceof (JCVM 3.1 §2.2.1.3: no char, long, float, double"
                    + " or multi-dimensional arrays)");
        };
        if (atype == T_REFERENCE) {
            emit.classRefOp(opcode, new byte[]{(byte) atype}, type.substring(2, type.length() - 1));
        } else {
            emit.op(opcode, atype, 0, 0);
        }
    }
}
