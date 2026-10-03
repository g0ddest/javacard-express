package name.velikodniy.jcexpress.converter.check;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.input.NestmateAccess;
import name.velikodniy.jcexpress.converter.translate.IntIssue;
import name.velikodniy.jcexpress.converter.translate.IntRules;

import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Method-level part of the Java Card subset check (JCVM 3.1 §2.2): modifiers, signature types,
 * instructions and the int rules of §2.2.3.1 ({@link IntRules}).
 */
final class MethodChecker {

    private static final int ACC_PRIVATE = 0x0002;
    private static final int ACC_SYNCHRONIZED = 0x0020;
    private static final int ACC_VARARGS = 0x0080;
    private static final int ACC_NATIVE = 0x0100;
    private static final int ACC_STRICT = 0x0800;
    /** Class file major version of Java 17, from which ACC_STRICT is no longer emitted (JEP 306). */
    private static final int JAVA_17 = 61;

    private final ClassInfo ci;
    private final MethodInfo mi;
    private final boolean intSupported;
    private final List<Violation> violations;
    private final String context;
    private final MethodModel model;
    private final List<ClassInfo> packageClasses;

    MethodChecker(ClassInfo ci, MethodInfo mi, boolean intSupported, List<Violation> violations,
                  List<ClassInfo> packageClasses) {
        this.ci = ci;
        this.mi = mi;
        this.intSupported = intSupported;
        this.violations = violations;
        this.context = mi.name() + mi.descriptor();
        this.model = ci.methodModel(mi.name(), mi.descriptor()).orElse(null);
        this.packageClasses = packageClasses;
    }

    void check() {
        checkModifiers();
        checkSignature();
        if (ci.isInterface() && mi.bytecode() != null && mi.bytecode().length > 0
                && !mi.isStaticInitializer()) {
            declaration("interface method with a body (default, static or private interface"
                    + " method) is not supported: interfaces declare abstract methods only"
                    + " (JCVM 3.1 §6.10: interface method declarations are not in the Method component)");
        }
        int before = violations.size();
        if (model != null && model.code().isPresent()) {
            checkInstructions(model.code().orElseThrow());
        } else {
            RawBytecodeScan.scan(ci.thisClass(), context, mi.bytecode(), violations);
        }
        if (violations.size() == before && model != null && model.code().isPresent()) {
            checkIntRules();
        }
    }

    private void checkModifiers() {
        int flags = mi.accessFlags();
        if ((flags & ACC_NATIVE) != 0) {
            declaration("native methods are not supported (JCVM 3.1 §2.2.1.2: keyword native)");
        }
        if ((flags & ACC_SYNCHRONIZED) != 0) {
            declaration("synchronized methods are not supported (JCVM 3.1 §2.2.1.2, §2.2.1.1.4)");
        }
        if ((flags & ACC_VARARGS) != 0) {
            declaration("variable-length argument lists are not supported (JCVM 3.1 §2.2.1.1.9)");
        }
        if ((flags & ACC_STRICT) != 0 && ci.model() != null && ci.model().majorVersion() < JAVA_17) {
            declaration("strictfp is not supported (JCVM 3.1 §2.2.1.2)");
        }
    }

    private void checkSignature() {
        String reason = ForbiddenTypes.checkDescriptor(mi.descriptor());
        if (reason != null) {
            declaration(reason);
        } else if (!intSupported && ForbiddenTypes.usesInt(mi.descriptor())) {
            declaration("int parameter or return type needs int support (JCVM 3.1 §2.2.3.1)");
        }
    }

    private void checkInstructions(CodeModel code) {
        int bci = 0;
        int line = -1;
        for (CodeElement e : code) {
            if (e instanceof LineNumber ln) {
                line = ln.line();
            } else if (e instanceof Instruction insn) {
                String reason = forbidden(insn);
                if (reason == null) {
                    reason = privateAccess(insn);
                }
                if (reason == null) {
                    reason = blankFinalAssignment(insn);
                }
                if (reason != null) {
                    violations.add(new Violation(ci.thisClass(), context, bci, reason,
                            ci.sourceFile().orElse(null), line));
                }
                bci += insn.sizeInBytes();
            }
        }
        for (ExceptionCatch c : code.exceptionHandlers()) {
            c.catchType().map(t -> ForbiddenTypes.checkInternalName(t.asInternalName()))
                    .ifPresent(reason -> declaration("catch clause: " + reason));
        }
    }

    /** Reason why an instruction is outside the Java Card subset, or {@code null}. */
    private static String forbidden(Instruction insn) {
        String opcodeReason = ForbiddenOpcodes.reason(insn.opcode().bytecode());
        if (opcodeReason != null) {
            return opcodeReason;
        }
        return switch (insn) {
            case ConstantInstruction c -> constantReason(c);
            case NewPrimitiveArrayInstruction n -> primitiveArrayReason(n.typeKind());
            case NewReferenceArrayInstruction n -> n.componentType().asInternalName().startsWith("[")
                    ? "arrays of more than one dimension are not supported (JCVM 3.1 §2.2.1.3)"
                    : ForbiddenTypes.checkInternalName(n.componentType().asInternalName());
            case TypeCheckInstruction t -> ForbiddenTypes.checkDescriptor(t.type().asSymbol().descriptorString());
            case FieldInstruction f -> fieldReason(f);
            case NewObjectInstruction n -> ForbiddenTypes.checkInternalName(n.className().asInternalName());
            case InvokeInstruction inv -> ForbiddenTypes.checkInvocation(inv.owner().asInternalName(),
                    inv.name().stringValue(), inv.typeSymbol().descriptorString());
            default -> null;
        };
    }

    /**
     * Access to a private member of another class of the package that {@link NestmateAccess} left
     * private (javac 11+ nestmate access, JEP 181). The Java Card platform enforces class-private
     * access (JCVM 3.1 §2.2.1.1.6), so such code cannot be converted.
     */
    private String privateAccess(Instruction insn) {
        String owner;
        String name;
        String desc;
        if (insn instanceof FieldInstruction f) {
            owner = f.owner().asInternalName();
            name = f.name().stringValue();
            desc = f.typeSymbol().descriptorString();
        } else if (insn instanceof InvokeInstruction inv) {
            owner = inv.owner().asInternalName();
            name = inv.name().stringValue();
            desc = inv.typeSymbol().descriptorString();
        } else {
            return null;
        }
        if (owner.equals(ci.thisClass()) || !isPrivateIn(owner, name, desc)) {
            return null;
        }
        String subclass = NestmateAccess.overridingSubclass(owner, name, desc, packageClasses);
        String why = subclass == null ? "" : ", and the method cannot be made package-visible because "
                + subclass + " declares the same method and would override it; rename one of the methods"
                + " or";
        return "nestmate access (javac 11+, JEP 181) to the private " + owner + "." + name + desc
                + ": the Java Card platform only allows access to a private member inside its class"
                + " (JCVM 3.1 §2.2.1.1.6)" + why + (why.isEmpty() ? "; " : " ")
                + "compile with javac --release 10 or lower";
    }

    /**
     * A static final field of a primitive type is a compile-time constant on the Java Card platform: its
     * value comes from the ConstantValue attribute, its uses are inlined and it has no Static Field image
     * and no Descriptor entry (JCVM 3.1 §2.2.4.6, §6.11, §6.14). A blank final assigned in
     * {@code <clinit>} has no constant value, so it cannot be represented (Oracle's tools, run as black
     * boxes, reject it as well).
     */
    private String blankFinalAssignment(Instruction insn) {
        if (!mi.isStaticInitializer() || !(insn instanceof FieldInstruction f) || f.opcode() != Opcode.PUTSTATIC
                || !f.owner().asInternalName().equals(ci.thisClass())) {
            return null;
        }
        String name = f.name().stringValue();
        String descriptor = f.typeSymbol().descriptorString();
        boolean blankFinal = descriptor.length() == 1 && ci.fields().stream()
                .anyMatch(fi -> fi.name().equals(name) && fi.descriptor().equals(descriptor) && fi.isStatic()
                        && fi.isFinal() && fi.constantValue() == null);
        if (!blankFinal) {
            return null;
        }
        return "static final " + f.typeSymbol().displayName() + " field " + name + " is assigned in the static"
                + " initializer: static final fields of primitive types must be compile-time constants,"
                + " initialized in their declaration (JCVM 3.1 §2.2.4.6)";
    }

    private boolean isPrivateIn(String owner, String name, String desc) {
        return packageClasses.stream().filter(c -> c.thisClass().equals(owner)).findFirst()
                .map(c -> desc.startsWith("(")
                        ? c.methods().stream().anyMatch(m -> m.isPrivate() && m.name().equals(name)
                                && m.descriptor().equals(desc))
                        : c.fields().stream().anyMatch(f -> (f.accessFlags() & ACC_PRIVATE) != 0
                                && f.name().equals(name) && f.descriptor().equals(desc)))
                .orElse(false);
    }

    private static String fieldReason(FieldInstruction f) {
        if (f.name().equalsString("$assertionsDisabled")) {
            return "assert statements are not supported (JCVM 3.1 §2.2.1.1.11)";
        }
        String reason = ForbiddenTypes.checkInternalName(f.owner().asInternalName());
        return reason != null ? reason : ForbiddenTypes.checkDescriptor(f.typeSymbol().descriptorString());
    }

    private static String primitiveArrayReason(TypeKind kind) {
        return switch (kind) {
            case CHAR, LONG, FLOAT, DOUBLE -> kind.name().toLowerCase(Locale.ROOT)
                    + " arrays are not supported (JCVM 3.1 §2.2.1.3)";
            default -> null;
        };
    }

    private static String constantReason(ConstantInstruction c) {
        if (c.opcode() != Opcode.LDC && c.opcode() != Opcode.LDC_W) {
            return null;
        }
        Object value = c.constantValue();
        if (value instanceof String) {
            return "String constants are not supported (JCVM 3.1 §2.2.1.4)";
        }
        if (value instanceof ClassDesc) {
            return "class literals are not supported: java/lang/Class is not part of the Java Card"
                    + " platform (JCVM 3.1 §2.2.1.4)";
        }
        if (!(value instanceof Integer)) {
            return "constants of type " + value.getClass().getSimpleName()
                    + " (method handles, method types, dynamic constants) are not supported"
                    + " (JCVM 3.1 §2.2.1.4)";
        }
        return null;
    }

    private void checkIntRules() {
        List<IntIssue> issues;
        try {
            issues = IntRules.check(model, intSupported);
        } catch (IllegalStateException e) {
            declaration("the method could not be analysed for int values: " + e.getMessage());
            return;
        }
        for (IntIssue issue : issues) {
            violations.add(new Violation(ci.thisClass(), context, issue.bci(), issue.message(),
                    ci.sourceFile().orElse(null), issue.line()));
        }
    }

    private void declaration(String message) {
        violations.add(new Violation(ci.thisClass(), context, -1, message,
                ci.sourceFile().orElse(null), firstLine().orElse(-1)));
    }

    private Optional<Integer> firstLine() {
        if (model == null || model.code().isEmpty()) {
            return Optional.empty();
        }
        for (CodeElement e : model.code().orElseThrow()) {
            if (e instanceof LineNumber ln) {
                return Optional.of(ln.line());
            }
        }
        return Optional.empty();
    }
}
