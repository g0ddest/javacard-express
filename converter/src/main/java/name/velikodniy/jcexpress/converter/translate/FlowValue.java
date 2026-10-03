package name.velikodniy.jcexpress.converter.translate;

import java.util.ArrayList;
import java.util.List;

/**
 * A value in the data flow of one JVM method: the result of an instruction, a parameter, a
 * merge (phi) of the values reaching a join point, or an unassigned local variable.
 *
 * <p>The {@link ValueRange} of a value is computed from its operation and its inputs
 * ({@link ValueTransfer}); {@code null} means "not known yet" or, for unassigned locals,
 * "contributes nothing".
 */
final class FlowValue {

    /** How the range of a value follows from its inputs (JVM int operations, JVMS §6.5). */
    enum Op {
        /** Range fixed at creation (constant, load from a typed field or array, parameter, call). */
        SOURCE,
        /** Merge of the values reaching a join point. */
        PHI,
        /**
         * The value of a local variable read by a load, or the value a store writes into a local
         * variable: the same JVM value, but its own value, so that the int support can keep it in
         * a different representation than the operand stack value (JCVM 3.1 §7.5.82 s2i,
         * §7.5.27 i2s).
         */
        COPY,
        /** iadd. */
        ADD,
        /** isub. */
        SUB,
        /** imul. */
        MUL,
        /** ineg. */
        NEG,
        /** idiv. */
        DIV,
        /** irem. */
        REM,
        /** iand. */
        AND,
        /** ior. */
        OR,
        /** ixor. */
        XOR,
        /** ishl (only the low five bits of the distance are used). */
        SHL,
        /** ishr. */
        SHR,
        /** iushr. */
        USHR,
        /** iinc: local variable plus constant. */
        INC,
        /** i2b. */
        TO_BYTE,
        /** i2s. */
        TO_SHORT
    }

    final int id;
    final Op op;
    final List<FlowValue> inputs;
    final Integer constant;
    final int producer;
    private ValueRange range;

    private FlowValue(int id, Op op, List<FlowValue> inputs, Integer constant, int producer,
                      ValueRange range) {
        this.id = id;
        this.op = op;
        this.inputs = inputs;
        this.constant = constant;
        this.producer = producer;
        this.range = range;
    }

    static FlowValue source(int id, ValueRange range, Integer constant, int producer) {
        return new FlowValue(id, Op.SOURCE, List.of(), constant, producer, range);
    }

    static FlowValue operation(int id, Op op, List<FlowValue> inputs, int producer) {
        return new FlowValue(id, op, List.copyOf(inputs), null, producer, null);
    }

    static FlowValue phi(int id, int producer) {
        return new FlowValue(id, Op.PHI, new ArrayList<>(), null, producer, null);
    }

    /** Range of this value, or {@code null} if not known (yet). */
    ValueRange range() {
        return range;
    }

    void setRange(ValueRange range) {
        this.range = range;
    }

    /** Kind of this value, or {@code null} if not known (yet). */
    ValueKind kind() {
        return range == null ? null : range.kind();
    }

    @Override
    public String toString() {
        return "v" + id + ":" + op + (constant != null ? "=" + constant : "") + ":" + range;
    }
}
