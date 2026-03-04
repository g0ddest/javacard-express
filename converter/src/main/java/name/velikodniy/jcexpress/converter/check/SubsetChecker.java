package name.velikodniy.jcexpress.converter.check;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that a set of parsed classes only use the JVM subset allowed by the
 * Java Card Virtual Machine (JCVM).
 *
 * <p>This class implements <strong>Stage 2: Subset Check</strong> of the converter
 * pipeline. After Stage 1 (class file parsing via {@code ClassFileReader}) produces
 * {@link ClassInfo} objects, this checker inspects every class, field, method
 * descriptor, and bytecode instruction to ensure nothing falls outside the JCVM
 * subset. If any violations are found, the converter aborts before proceeding to
 * Stage 3 (token assignment).
 *
 * <h2>What is checked</h2>
 * <ul>
 *   <li><strong>Superclass and interface names</strong> -- rejected if they reference
 *       forbidden classes such as {@code java/lang/String} or {@code java/lang/Thread}
 *       (see {@link ForbiddenTypes}).</li>
 *   <li><strong>Field descriptors</strong> -- rejected if they contain forbidden
 *       primitive types ({@code long}, {@code float}, {@code double}) or forbidden
 *       reference types.</li>
 *   <li><strong>Method descriptors</strong> -- parameter and return types are checked
 *       for the same forbidden type set.</li>
 *   <li><strong>Bytecode instructions</strong> -- every opcode in every method body
 *       is checked against the {@link ForbiddenOpcodes} table. Forbidden opcodes
 *       include all {@code long}/{@code float}/{@code double} operations, threading
 *       instructions ({@code monitorenter}/{@code monitorexit}), subroutines
 *       ({@code jsr}/{@code ret}), {@code invokedynamic}, and wide jumps
 *       ({@code goto_w}/{@code jsr_w}).</li>
 * </ul>
 *
 * <h2>JCVM specification references</h2>
 * <ul>
 *   <li>JCVM 3.0.5, Section 3.1 -- "Java Card Platform Subset": defines the
 *       unsupported Java language features (no {@code float}, {@code double},
 *       {@code long}, threads, etc.).</li>
 *   <li>JCVM 3.0.5, Section 3.2 -- "Unsupported Items": lists specific JVM bytecodes
 *       excluded from the Java Card platform.</li>
 *   <li>JCVM 3.0.5, Section 7 -- "Java Card Virtual Machine Instruction Set":
 *       canonical list of supported bytecodes.</li>
 * </ul>
 *
 * <p>This class is a stateless utility with no public constructor. All checking is
 * performed through the static {@link #check(List)} method, which returns an
 * immutable list of {@link Violation} records.
 *
 * @see ForbiddenOpcodes
 * @see ForbiddenTypes
 * @see Violation
 */
public final class SubsetChecker {

    private SubsetChecker() {}

    /**
     * Checks all classes for JavaCard subset violations.
     *
     * <p>Iterates over every class, inspecting superclass references, implemented
     * interfaces, field descriptors, method descriptors, and bytecode instructions.
     * Any construct that falls outside the JCVM-supported subset is recorded as a
     * {@link Violation}.
     *
     * @param classes the list of parsed {@link ClassInfo} objects from Stage 1
     * @return an immutable list of violations; empty if all classes are compliant
     */
    public static List<Violation> check(List<ClassInfo> classes) {
        List<Violation> violations = new ArrayList<>();
        for (ClassInfo ci : classes) {
            checkClass(ci, violations);
        }
        return List.copyOf(violations);
    }

    private static void checkClass(ClassInfo ci, List<Violation> violations) {
        String className = ci.thisClass();

        // Check superclass
        if (ci.superClass() != null) {
            String reason = ForbiddenTypes.checkInternalName(ci.superClass());
            if (reason != null) {
                violations.add(new Violation(className, "superclass", reason));
            }
        }

        // Check interfaces
        for (String iface : ci.interfaces()) {
            String reason = ForbiddenTypes.checkInternalName(iface);
            if (reason != null) {
                violations.add(new Violation(className, "interface " + iface, reason));
            }
        }

        // Check fields
        for (FieldInfo fi : ci.fields()) {
            String reason = ForbiddenTypes.checkDescriptor(fi.descriptor());
            if (reason != null) {
                violations.add(new Violation(className, "field " + fi.name(), reason));
            }
        }

        // Check methods
        for (MethodInfo mi : ci.methods()) {
            String ctx = mi.name() + mi.descriptor();

            // Check method descriptor for forbidden parameter/return types
            String reason = ForbiddenTypes.checkDescriptor(mi.descriptor());
            if (reason != null) {
                violations.add(new Violation(className, ctx, reason));
            }

            // Check bytecode for forbidden opcodes
            checkBytecode(className, ctx, mi.bytecode(), violations);
        }
    }

    private static void checkBytecode(String className, String context,
                                      byte[] bytecode, List<Violation> violations) {
        if (bytecode == null || bytecode.length == 0) return;

        int pc = 0;
        while (pc < bytecode.length) {
            int opcode = bytecode[pc] & 0xFF;

            if (ForbiddenOpcodes.isForbidden(opcode)) {
                violations.add(new Violation(
                        className, context, pc,
                        ForbiddenOpcodes.reason(opcode)
                ));
            }

            int len = ForbiddenOpcodes.instructionLength(opcode);
            if (len > 0) {
                pc += len;
            } else {
                pc = skipVariableLength(bytecode, pc, opcode);
            }
        }
    }

    private static int skipVariableLength(byte[] bytecode, int pc, int opcode) {
        return switch (opcode) {
            case 0xAA -> { // tableswitch
                int padded = (pc + 4) & ~3;
                int low = readInt(bytecode, padded + 4);
                int high = readInt(bytecode, padded + 8);
                yield padded + 12 + (high - low + 1) * 4;
            }
            case 0xAB -> { // lookupswitch
                int padded = (pc + 4) & ~3;
                int npairs = readInt(bytecode, padded + 4);
                yield padded + 8 + npairs * 8;
            }
            case 0xC4 -> { // wide
                int wideOpcode = bytecode[pc + 1] & 0xFF;
                yield (wideOpcode == 0x84) ? pc + 6 : pc + 4;
            }
            default -> pc + 1;
        };
    }

    private static int readInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24)
                | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8)
                | (b[offset + 3] & 0xFF);
    }
}
