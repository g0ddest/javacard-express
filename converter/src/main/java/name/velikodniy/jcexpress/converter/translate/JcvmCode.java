package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.translate.TranslatedMethod.JcvmExceptionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.IntUnaryOperator;

/**
 * Symbolic JCVM code of one method: instructions with unresolved branch widths and constant pool
 * indices that may still be renumbered, plus the exception handlers as label ranges.
 *
 * <p>Keeping the symbolic form lets the converter reorder the constant pool after all methods have
 * been translated (JCVM 3.1 §6.8 places instance field references first so that the 1-byte
 * {@code getfield_<t>} forms can be used) and then re-encode every method with its final
 * constant pool indices, instruction widths, branch offsets and handler ranges.
 */
public final class JcvmCode {

    /**
     * An exception handler of the source method in symbolic form.
     *
     * @param start     first instruction of the active range
     * @param end       first instruction after the active range
     * @param handler   handler entry point
     * @param catchType constant pool index of the caught class, or {@link #FINALLY}
     */
    record Handler(JcvmLabel start, JcvmLabel end, JcvmLabel handler, int catchType) {}

    /** Catch type of a handler that catches every exception (a {@code finally} block). */
    static final int FINALLY = -1;

    private final List<JcvmInsn> insns;
    private final List<Handler> handlers;
    private final int maxStack;
    private final int maxLocals;
    private final int nargs;
    private final String origin;

    JcvmCode(List<JcvmInsn> insns, List<Handler> handlers, int maxStack, int maxLocals, int nargs,
             String origin) {
        this.insns = List.copyOf(insns);
        this.handlers = List.copyOf(handlers);
        this.maxStack = maxStack;
        this.maxLocals = maxLocals;
        this.nargs = nargs;
        this.origin = origin;
    }

    /**
     * Returns the source method of this code for diagnostics, e.g.
     * {@code com/example/Wallet.debit([BS)V (Wallet.java)}.
     *
     * @return class, method name and descriptor, and the source file when known
     */
    public String origin() {
        return origin;
    }

    /**
     * Encodes this code with the given constant pool renumbering.
     *
     * @param cpRemap {@code cpRemap[old] = new}; an empty array means the identity mapping
     * @return the translated method with final bytes, offsets and handler table
     * @throws IllegalStateException if a JCVM limit is exceeded
     */
    public TranslatedMethod assemble(int[] cpRemap) {
        IntUnaryOperator map = cpRemap.length == 0 ? i -> i : i -> cpRemap[i];
        JcvmAssembler.Result result = JcvmAssembler.assemble(insns, map);
        List<JcvmExceptionHandler> table = new ArrayList<>();
        for (Handler h : handlers) {
            int start = result.offset(h.start());
            int end = result.offset(h.end());
            if (start < end) { // a range without any JCVM instruction cannot throw
                table.add(h.catchType() == FINALLY
                        ? JcvmExceptionHandler.catchAll(start, end, result.offset(h.handler()))
                        : new JcvmExceptionHandler(start, end, result.offset(h.handler()),
                                map.applyAsInt(h.catchType()), false));
            }
        }
        return new TranslatedMethod(result.code(), maxStack, maxLocals, nargs, table,
                false, result.cpReferences(), this);
    }

    /**
     * Returns the constant pool indices used as catch types (JCVM 3.1 §6.10.3), as recorded at
     * translation time (before any renumbering).
     *
     * @return sorted set of constant pool indices
     */
    public Set<Integer> catchTypeIndices() {
        Set<Integer> types = new TreeSet<>();
        for (Handler h : handlers) {
            if (h.catchType() != FINALLY) {
                types.add(h.catchType());
            }
        }
        return types;
    }

    /**
     * Returns whether this code contains "an instruction of type int" or "of type int array" in
     * the sense of JCVM 3.1 §6.4 (ACC_INT). Int parameters, fields and local variables are
     * declarations; {@link name.velikodniy.jcexpress.converter.cap.HeaderComponent#usesInt}
     * checks those.
     *
     * @return {@code true} if an int instruction is used
     */
    public boolean usesInt() {
        for (JcvmInsn insn : insns) {
            if (JcvmOpcode.isIntInstruction(insn)) {
                return true;
            }
        }
        return false;
    }
}
