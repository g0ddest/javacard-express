package name.velikodniy.jcexpress.converter.translate;

import java.util.ArrayList;
import java.util.List;

/**
 * Peephole optimization of symbolic JCVM code: the short increment instruction.
 *
 * <p>javac compiles {@code s++}, {@code s--}, {@code s += k} and {@code s -= k} on a short local
 * variable as load, push, add or subtract, i2s and store; the translator turns that into
 * {@code sload n; <push k>; sadd|ssub; sstore n}. The Java Card instruction set has a dedicated
 * instruction for it: {@code sinc} (JCVM 3.1 §7.5.89) increments a local variable that contains
 * a short by a sign-extended byte constant, the same 16-bit sum as {@code sadd} (§7.5.83). So the
 * four instructions, with no label in between, become {@code sinc n ±k} when the constant is
 * pushed by {@code sconst_<k>} or {@code bspush} and the increment fits a signed byte. Larger
 * constants keep the four instructions, as the Oracle converter (observed as a black box) does;
 * {@code sinc_w} is not used.
 */
final class JcvmPeephole {

    private static final int WINDOW = 4;

    private JcvmPeephole() {}

    /**
     * Folds short increments of local variables into {@code sinc}.
     *
     * @param insns symbolic instructions of one method
     * @return the instructions with every matching four-instruction sequence replaced
     */
    static List<JcvmInsn> shortIncrements(List<JcvmInsn> insns) {
        List<JcvmInsn> out = new ArrayList<>(insns.size());
        int i = 0;
        while (i < insns.size()) {
            JcvmInsn sinc = i + WINDOW <= insns.size() ? sinc(insns.subList(i, i + WINDOW)) : null;
            if (sinc != null) {
                out.add(sinc);
                i += WINDOW;
            } else {
                out.add(insns.get(i));
                i++;
            }
        }
        return out;
    }

    /** {@code sinc n delta} for {@code sload n; push k; sadd|ssub; sstore n}, or {@code null}. */
    private static JcvmInsn sinc(List<JcvmInsn> window) {
        int load = localIndex(window.get(0), JcvmOpcode.SLOAD, JcvmOpcode.SLOAD_0);
        Integer constant = byteConstant(window.get(1));
        int op = window.get(2) instanceof JcvmInsn.Plain p ? p.opcode() : -1;
        int store = localIndex(window.get(3), JcvmOpcode.SSTORE, JcvmOpcode.SSTORE_0);
        if (load < 0 || load != store || constant == null
                || (op != JcvmOpcode.SADD && op != JcvmOpcode.SSUB)) {
            return null;
        }
        int delta = op == JcvmOpcode.SADD ? constant : -constant;
        if (delta < Byte.MIN_VALUE || delta > Byte.MAX_VALUE) {
            return null;
        }
        return new JcvmInsn.Plain(JcvmOpcode.SINC, new byte[]{(byte) load, (byte) delta});
    }

    /** Local variable index of {@code <op> index} or {@code <op>_<n>}, or -1 for anything else. */
    private static int localIndex(JcvmInsn insn, int indexedOpcode, int firstShortForm) {
        if (!(insn instanceof JcvmInsn.Plain p)) {
            return -1;
        }
        if (p.opcode() == indexedOpcode) {
            return p.operands()[0] & 0xFF;
        }
        int n = p.opcode() - firstShortForm;
        return n >= 0 && n <= 3 ? n : -1;
    }

    /** Value pushed by {@code sconst_<k>} (§7.5.87) or {@code bspush} (§7.5.15), or {@code null}. */
    private static Integer byteConstant(JcvmInsn insn) {
        if (!(insn instanceof JcvmInsn.Plain p)) {
            return null;
        }
        if (p.opcode() >= JcvmOpcode.SCONST_M1 && p.opcode() <= JcvmOpcode.SCONST_5) {
            return p.opcode() - JcvmOpcode.SCONST_0;
        }
        return p.opcode() == JcvmOpcode.BSPUSH ? (int) p.operands()[0] : null;
    }
}
