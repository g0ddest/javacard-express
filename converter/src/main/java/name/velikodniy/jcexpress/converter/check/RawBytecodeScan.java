package name.velikodniy.jcexpress.converter.check;

import java.util.List;

/**
 * Opcode scan of raw JVM bytecode, used for methods whose parsed class file is not available
 * (records built in code). Methods read from class files are checked instruction by instruction
 * with the JDK ClassFile API instead ({@link MethodChecker}).
 */
final class RawBytecodeScan {

    private static final int NEWARRAY = 0xBC;
    /** JVMS §6.5 newarray atype of char. */
    private static final int T_CHAR = 5;

    private RawBytecodeScan() {}

    static void scan(String className, String context, byte[] bytecode, List<Violation> violations) {
        if (bytecode == null || bytecode.length == 0) {
            return;
        }
        int pc = 0;
        while (pc < bytecode.length) {
            int opcode = bytecode[pc] & 0xFF;
            String reason = ForbiddenOpcodes.reason(opcode);
            if (reason == null && opcode == NEWARRAY && pc + 1 < bytecode.length
                    && bytecode[pc + 1] == T_CHAR) {
                reason = "char arrays are not supported (JCVM 3.1 §2.2.1.3)";
            }
            if (reason != null) {
                violations.add(new Violation(className, context, pc, reason));
            }
            int len = ForbiddenOpcodes.instructionLength(opcode);
            pc = len > 0 ? pc + len : skipVariableLength(bytecode, pc, opcode);
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
