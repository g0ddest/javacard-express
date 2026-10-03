package name.velikodniy.jcexpress.converter.translate;

import java.lang.classfile.Instruction;
import java.lang.classfile.Opcode;

/**
 * One-to-one JVM to JCVM opcode mappings of JCVM 3.1 Chapter 7 for instructions whose JCVM form
 * does not depend on the constant pool. Every method throws for an opcode it cannot map exactly,
 * so a missing mapping can never be dropped silently.
 */
final class OpcodeMap {

    private OpcodeMap() {}

    /**
     * Stack manipulation (§7.5.17-§7.5.19, §7.5.73, §7.5.74, §7.5.108) for one-word (16-bit)
     * values. {@code dup_x mn}: m = words copied, n = words down to the insertion point including
     * the m copied words (§7.5.18); {@code swap_x mn}: swap the top m words with the n below.
     */
    static int stack(Opcode op) {
        return switch (op) {
            case POP -> JcvmOpcode.POP;
            case POP2 -> JcvmOpcode.POP2;
            case DUP -> JcvmOpcode.DUP;
            case DUP2 -> JcvmOpcode.DUP2;
            case DUP_X1, DUP_X2, DUP2_X1, DUP2_X2 -> JcvmOpcode.DUP_X;
            case SWAP -> JcvmOpcode.SWAP_X;
            default -> throw new IllegalStateException("unsupported stack instruction " + op);
        };
    }

    /** Operand bytes of {@link #stack(Opcode)} for one-word values. */
    static int[] stackOperands(Opcode op) {
        return switch (op) {
            case DUP_X1 -> new int[]{0x12};
            case DUP_X2 -> new int[]{0x13};
            case DUP2_X1 -> new int[]{0x23};
            case DUP2_X2 -> new int[]{0x24};
            case SWAP -> new int[]{0x11};
            default -> new int[0];
        };
    }

    /**
     * Arithmetic, logical and shift operators (§7.5.28 ff.) and arraylength (§7.5.8; the JDK
     * ClassFile API models ARRAYLENGTH as an operator).
     *
     * @param op     JVM opcode
     * @param intOps {@code true} for the int form, {@code false} for the short form
     */
    @SuppressWarnings("java:S1479") // one case per JVM operator
    static int operator(Opcode op, boolean intOps) {
        return switch (op) {
            case IADD -> intOps ? JcvmOpcode.IADD : JcvmOpcode.SADD;
            case ISUB -> intOps ? JcvmOpcode.ISUB : JcvmOpcode.SSUB;
            case IMUL -> intOps ? JcvmOpcode.IMUL : JcvmOpcode.SMUL;
            case IDIV -> intOps ? JcvmOpcode.IDIV : JcvmOpcode.SDIV;
            case IREM -> intOps ? JcvmOpcode.IREM : JcvmOpcode.SREM;
            case INEG -> intOps ? JcvmOpcode.INEG : JcvmOpcode.SNEG;
            case ISHL -> intOps ? JcvmOpcode.ISHL : JcvmOpcode.SSHL;
            case ISHR -> intOps ? JcvmOpcode.ISHR : JcvmOpcode.SSHR;
            case IUSHR -> intOps ? JcvmOpcode.IUSHR : JcvmOpcode.SUSHR;
            case IAND -> intOps ? JcvmOpcode.IAND : JcvmOpcode.SAND;
            case IOR -> intOps ? JcvmOpcode.IOR : JcvmOpcode.SOR;
            case IXOR -> intOps ? JcvmOpcode.IXOR : JcvmOpcode.SXOR;
            case ARRAYLENGTH -> JcvmOpcode.ARRAYLENGTH;
            default -> throw new IllegalStateException("unsupported operator " + op
                    + " (JCVM 3.1 §2.2.1.3: no long, float or double arithmetic)");
        };
    }

    /**
     * Branches in their 1-byte offset form (§7.5.24, §7.5.35, §7.5.37, §7.5.39, §7.5.41,
     * §7.5.43); the assembler widens them when needed. if_icmp&lt;cond&gt; compares one-word
     * values with if_scmp&lt;cond&gt;.
     */
    static int branch(Opcode op) {
        return switch (op) {
            case GOTO, GOTO_W -> JcvmOpcode.GOTO;
            case IFEQ -> JcvmOpcode.IFEQ;
            case IFNE -> JcvmOpcode.IFNE;
            case IFLT -> JcvmOpcode.IFLT;
            case IFGE -> JcvmOpcode.IFGE;
            case IFGT -> JcvmOpcode.IFGT;
            case IFLE -> JcvmOpcode.IFLE;
            case IFNULL -> JcvmOpcode.IFNULL;
            case IFNONNULL -> JcvmOpcode.IFNONNULL;
            case IF_ACMPEQ -> JcvmOpcode.IF_ACMPEQ;
            case IF_ACMPNE -> JcvmOpcode.IF_ACMPNE;
            case IF_ICMPEQ -> JcvmOpcode.IF_SCMPEQ;
            case IF_ICMPNE -> JcvmOpcode.IF_SCMPNE;
            case IF_ICMPLT -> JcvmOpcode.IF_SCMPLT;
            case IF_ICMPGE -> JcvmOpcode.IF_SCMPGE;
            case IF_ICMPGT -> JcvmOpcode.IF_SCMPGT;
            case IF_ICMPLE -> JcvmOpcode.IF_SCMPLE;
            default -> throw new IllegalStateException("unsupported branch instruction " + op);
        };
    }

    /** Describes a JVM instruction that has no JCVM counterpart, with the governing rule. */
    static String unsupported(Instruction insn) {
        Opcode op = insn.opcode();
        String reason = switch (op) {
            case MONITORENTER, MONITOREXIT -> "JCVM 3.1 §2.2.1.1.4: threads and synchronized are not supported";
            case MULTIANEWARRAY -> "JCVM 3.1 §2.2.1.3: arrays of more than one dimension are not supported";
            case INVOKEDYNAMIC -> "JCVM 3.1 Chapter 7 has no invokedynamic (lambdas, string concatenation)";
            case JSR, JSR_W, RET, RET_W -> "jsr/ret subroutines are inlined when the class files are read"
                    + " (JCVM 3.1 §2.3.2.2), and this method's were not";
            case I2C -> "JCVM 3.1 §2.2.1.3: char is not supported";
            default -> "JCVM 3.1 §2.2.1.3: long, float and double are not supported";
        };
        return "unsupported instruction " + op.name().toLowerCase(java.util.Locale.ROOT) + " (" + reason + ")";
    }
}
