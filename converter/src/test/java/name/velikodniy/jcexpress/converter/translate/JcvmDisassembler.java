package name.velikodniy.jcexpress.converter.translate;

import java.util.ArrayList;
import java.util.List;

/**
 * Clean-room disassembler for JCVM bytecode, written from the instruction formats of
 * JCVM 3.1 specification Chapter 7 ("Java Card Virtual Machine Instruction Set").
 *
 * <p>Test helper: turns translated method bytecode into readable instruction lines so that tests
 * can assert exact, spec-derived instruction sequences instead of scanning for byte values.
 * Branch and switch targets are printed as absolute bytecode offsets ({@code -> 12}),
 * constant pool operands as {@code #n}.
 */
public final class JcvmDisassembler {

    /** One decoded instruction. */
    public record Insn(int pc, int opcode, String mnemonic, String operands, int length) {
        /** Returns {@code "mnemonic operands"} (or just the mnemonic). */
        public String text() {
            return operands.isEmpty() ? mnemonic : mnemonic + " " + operands;
        }
    }

    private static final String[] NAMES = new String[256];

    static {
        String[] simple = {
            "nop", "aconst_null", "sconst_m1", "sconst_0", "sconst_1", "sconst_2", "sconst_3",
            "sconst_4", "sconst_5", "iconst_m1", "iconst_0", "iconst_1", "iconst_2", "iconst_3",
            "iconst_4", "iconst_5", "bspush", "sspush", "bipush", "sipush", "iipush", "aload",
            "sload", "iload", "aload_0", "aload_1", "aload_2", "aload_3", "sload_0", "sload_1",
            "sload_2", "sload_3", "iload_0", "iload_1", "iload_2", "iload_3", "aaload", "baload",
            "saload", "iaload", "astore", "sstore", "istore", "astore_0", "astore_1", "astore_2",
            "astore_3", "sstore_0", "sstore_1", "sstore_2", "sstore_3", "istore_0", "istore_1",
            "istore_2", "istore_3", "aastore", "bastore", "sastore", "iastore", "pop", "pop2",
            "dup", "dup2", "dup_x", "swap_x", "sadd", "iadd", "ssub", "isub", "smul", "imul",
            "sdiv", "idiv", "srem", "irem", "sneg", "ineg", "sshl", "ishl", "sshr", "ishr",
            "sushr", "iushr", "sand", "iand", "sor", "ior", "sxor", "ixor", "sinc", "iinc", "s2b",
            "s2i", "i2b", "i2s", "icmp", "ifeq", "ifne", "iflt", "ifge", "ifgt", "ifle", "ifnull",
            "ifnonnull", "if_acmpeq", "if_acmpne", "if_scmpeq", "if_scmpne", "if_scmplt",
            "if_scmpge", "if_scmpgt", "if_scmple", "goto", "jsr", "ret", "stableswitch",
            "itableswitch", "slookupswitch", "ilookupswitch", "areturn", "sreturn", "ireturn",
            "return", "getstatic_a", "getstatic_b", "getstatic_s", "getstatic_i", "putstatic_a",
            "putstatic_b", "putstatic_s", "putstatic_i", "getfield_a", "getfield_b", "getfield_s",
            "getfield_i", "putfield_a", "putfield_b", "putfield_s", "putfield_i", "invokevirtual",
            "invokespecial", "invokestatic", "invokeinterface", "new", "newarray", "anewarray",
            "arraylength", "athrow", "checkcast", "instanceof", "sinc_w", "iinc_w", "ifeq_w",
            "ifne_w", "iflt_w", "ifge_w", "ifgt_w", "ifle_w", "ifnull_w", "ifnonnull_w",
            "if_acmpeq_w", "if_acmpne_w", "if_scmpeq_w", "if_scmpne_w", "if_scmplt_w",
            "if_scmpge_w", "if_scmpgt_w", "if_scmple_w", "goto_w", "getfield_a_w", "getfield_b_w",
            "getfield_s_w", "getfield_i_w", "getfield_a_this", "getfield_b_this",
            "getfield_s_this", "getfield_i_this", "putfield_a_w", "putfield_b_w", "putfield_s_w",
            "putfield_i_w", "putfield_a_this", "putfield_b_this", "putfield_s_this",
            "putfield_i_this"
        };
        System.arraycopy(simple, 0, NAMES, 0, simple.length); // opcodes 0x00..0xB8 are contiguous
        NAMES[0xFE] = "impdep1";
        NAMES[0xFF] = "impdep2";
    }

    private JcvmDisassembler() {}

    /** Mnemonic of a JCVM opcode (JCVM 3.1 Table 8-1), or {@code null} if undefined. */
    public static String mnemonic(int opcode) {
        return NAMES[opcode & 0xFF];
    }

    /**
     * Decodes a whole bytecode array.
     *
     * @throws IllegalArgumentException on undefined opcodes or truncated operands
     */
    public static List<Insn> disassemble(byte[] code) {
        List<Insn> out = new ArrayList<>();
        int pc = 0;
        while (pc < code.length) {
            Insn insn = decode(code, pc);
            out.add(insn);
            pc += insn.length();
        }
        return out;
    }

    /** Returns {@code "mnemonic operands"} lines for the whole bytecode array. */
    public static List<String> lines(byte[] code) {
        return disassemble(code).stream().map(Insn::text).toList();
    }

    /** Returns only the mnemonics of the whole bytecode array. */
    public static List<String> mnemonics(byte[] code) {
        return disassemble(code).stream().map(Insn::mnemonic).toList();
    }

    private static Insn decode(byte[] c, int pc) {
        int op = c[pc] & 0xFF;
        String name = NAMES[op];
        if (name == null) {
            throw new IllegalArgumentException("undefined JCVM opcode 0x" + Integer.toHexString(op)
                    + " at pc " + pc);
        }
        try {
            return decodeOperands(c, pc, op, name);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("truncated operands of " + name + " at pc " + pc, e);
        }
    }

    @SuppressWarnings("java:S1479") // one case per instruction format of Chapter 7
    private static Insn decodeOperands(byte[] c, int pc, int op, String name) {
        return switch (op) {
            case 0x10, 0x12 -> insn(pc, op, name, String.valueOf(c[pc + 1]), 2);      // bspush, bipush
            case 0x11, 0x13 -> insn(pc, op, name, String.valueOf(s2(c, pc + 1)), 3);  // sspush, sipush
            case 0x14 -> insn(pc, op, name, String.valueOf(s4(c, pc + 1)), 5);         // iipush
            case 0x15, 0x16, 0x17, 0x28, 0x29, 0x2A, 0x72 ->                           // [asi]load/store, ret
                    insn(pc, op, name, String.valueOf(u1(c, pc + 1)), 2);
            case 0x3F, 0x40 -> insn(pc, op, name, String.format("0x%02x", u1(c, pc + 1)), 2);
            case 0x59, 0x5A -> insn(pc, op, name, u1(c, pc + 1) + " " + c[pc + 2], 3); // sinc, iinc
            case 0x96, 0x97 -> insn(pc, op, name, u1(c, pc + 1) + " " + s2(c, pc + 2), 4);
            case 0x71 -> insn(pc, op, name, "-> " + (pc + s2(c, pc + 1)), 3);          // jsr
            case 0x73 -> tableSwitch(c, pc, op, name, 2);
            case 0x74 -> tableSwitch(c, pc, op, name, 4);
            case 0x75 -> lookupSwitch(c, pc, op, name, 2);
            case 0x76 -> lookupSwitch(c, pc, op, name, 4);
            case 0x8E -> insn(pc, op, name,
                    u1(c, pc + 1) + " #" + u2(c, pc + 2) + " " + u1(c, pc + 4), 5);    // invokeinterface
            case 0x90 -> insn(pc, op, name, String.valueOf(u1(c, pc + 1)), 2);         // newarray
            case 0x94, 0x95 -> insn(pc, op, name, u1(c, pc + 1) + " #" + u2(c, pc + 2), 4);
            default -> decodeRegular(c, pc, op, name);
        };
    }

    private static Insn decodeRegular(byte[] c, int pc, int op, String name) {
        if (op >= 0x60 && op <= 0x70) {                       // narrow branches, goto
            return insn(pc, op, name, "-> " + (pc + c[pc + 1]), 2);
        }
        if (op >= 0x98 && op <= 0xA8) {                       // wide branches, goto_w
            return insn(pc, op, name, "-> " + (pc + s2(c, pc + 1)), 3);
        }
        if ((op >= 0x7B && op <= 0x82) || (op >= 0x8B && op <= 0x8D)
                || op == 0x8F || op == 0x91 || (op >= 0xA9 && op <= 0xAC)
                || (op >= 0xB1 && op <= 0xB4)) {               // u2 CP index operands
            return insn(pc, op, name, "#" + u2(c, pc + 1), 3);
        }
        if ((op >= 0x83 && op <= 0x8A) || (op >= 0xAD && op <= 0xB0)
                || (op >= 0xB5 && op <= 0xB8)) {               // u1 CP index operands
            return insn(pc, op, name, "#" + u1(c, pc + 1), 2);
        }
        return insn(pc, op, name, "", 1);
    }

    private static Insn tableSwitch(byte[] c, int pc, int op, String name, int keySize) {
        int def = pc + s2(c, pc + 1);
        int low = keySize == 2 ? s2(c, pc + 3) : s4(c, pc + 3);
        int high = keySize == 2 ? s2(c, pc + 3 + keySize) : s4(c, pc + 3 + keySize);
        int p = pc + 3 + 2 * keySize;
        List<Integer> targets = new ArrayList<>();
        for (int k = low; k <= high; k++, p += 2) {
            targets.add(pc + s2(c, p));
        }
        return insn(pc, op, name, "default->" + def + " low=" + low + " high=" + high
                + " " + targets, p - pc);
    }

    private static Insn lookupSwitch(byte[] c, int pc, int op, String name, int keySize) {
        int def = pc + s2(c, pc + 1);
        int npairs = u2(c, pc + 3);
        int p = pc + 5;
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < npairs; i++) {
            int key = keySize == 2 ? s2(c, p) : s4(c, p);
            pairs.add(key + "->" + (pc + s2(c, p + keySize)));
            p += keySize + 2;
        }
        return insn(pc, op, name, "default->" + def + " " + pairs, p - pc);
    }

    private static Insn insn(int pc, int op, String name, String operands, int length) {
        return new Insn(pc, op, name, operands, length);
    }

    private static int u1(byte[] c, int i) {
        return c[i] & 0xFF;
    }

    private static int u2(byte[] c, int i) {
        return ((c[i] & 0xFF) << 8) | (c[i + 1] & 0xFF);
    }

    private static int s2(byte[] c, int i) {
        return (short) u2(c, i);
    }

    private static int s4(byte[] c, int i) {
        return (u2(c, i) << 16) | u2(c, i + 2);
    }
}
