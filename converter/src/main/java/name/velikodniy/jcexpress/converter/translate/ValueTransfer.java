package name.velikodniy.jcexpress.converter.translate;

import java.util.List;

/**
 * Transfer functions of the int data flow: the {@link ValueRange} of an operation's result from
 * the ranges of its inputs.
 *
 * <p>Intervals are those of the 32-bit JVM computation (JVMS §6.5). The kind says how much of
 * that value the 16-bit Java Card instructions reproduce (JCVM 3.1 §2.2.3.1):
 * <ul>
 *   <li>add, sub, mul, neg, and, or, xor, shl and iinc depend only on the low 16 bits of their
 *       operands, so their 16-bit result always equals the low 16 bits of the JVM result
 *       (§7.5.83 sadd: "the result is the low-order bits of the true mathematical result");</li>
 *   <li>div, rem (both operands) and shr, ushr (the shifted value) need exact operands; their
 *       16-bit result then equals the low 16 bits of the JVM result (§7.5.88 sdiv, §7.5.98
 *       srem, §7.5.101 sshr, §7.5.107 sushr shifts the sign-extended 32-bit value);</li>
 *   <li>i2b and i2s produce exact values.</li>
 * </ul>
 * A result is exact ({@link ValueKind#SHORT}) when it is reproduced and its interval lies in the
 * short range. A result computed from a value of kind {@link ValueKind#INT} is an int.
 */
final class ValueTransfer {

    private ValueTransfer() {}

    /**
     * Recomputes the range of a value from its inputs.
     *
     * @param v a value that is not a {@link FlowValue.Op#SOURCE}
     * @return {@code true} if the range changed
     */
    static boolean update(FlowValue v) {
        ValueRange next = v.op == FlowValue.Op.PHI ? phi(v) : ValueRange.join(v.range(), operation(v));
        if (next == null || next.equals(v.range())) {
            return false;
        }
        v.setRange(next);
        return true;
    }

    private static ValueRange phi(FlowValue v) {
        ValueRange joined = null;
        for (FlowValue in : v.inputs) {
            joined = ValueRange.join(joined, in.range());
        }
        return ValueRange.widen(v.range(), joined);
    }

    private static ValueRange operation(FlowValue v) {
        if (v.op == FlowValue.Op.COPY) {
            return v.inputs.getFirst().range();
        }
        return apply(v.op, v.inputs.stream().map(FlowValue::range).toList());
    }

    /**
     * Range of the result of an arithmetic or conversion operation for the given input ranges.
     *
     * @param op     an operation other than {@link FlowValue.Op#SOURCE}, {@link FlowValue.Op#PHI}
     *               and {@link FlowValue.Op#COPY}
     * @param inputs input ranges in operand order; an element may be {@code null} (not known)
     * @return the result range, or {@code null} if an input is not known
     */
    static ValueRange apply(FlowValue.Op op, List<ValueRange> inputs) {
        for (ValueRange in : inputs) {
            if (in == null) {
                return null;
            }
            if (!in.isNumeric()) {
                return ValueRange.UNUSABLE;
            }
        }
        ValueRange a = inputs.getFirst();
        ValueRange b = inputs.size() > 1 ? inputs.get(1) : null;
        boolean anyInt = inputs.stream().anyMatch(in -> in.kind() == ValueKind.INT);
        return switch (op) {
            case TO_BYTE -> narrow(a, Byte.MIN_VALUE, Byte.MAX_VALUE);
            case TO_SHORT -> narrow(a, Short.MIN_VALUE, Short.MAX_VALUE);
            case DIV, REM -> needsExact(anyInt, a, b, interval(op, a, b));
            case SHR, USHR -> needsExact(anyInt, a, null, interval(op, a, b));
            default -> result(anyInt, interval(op, a, b));
        };
    }

    /** i2b / i2s: the JVM value itself if it already fits, otherwise any value of the type. */
    private static ValueRange narrow(ValueRange a, long min, long max) {
        return a.within(min, max) ? new ValueRange(ValueKind.SHORT, a.lo(), a.hi())
                : new ValueRange(ValueKind.SHORT, min, max);
    }

    private static ValueRange needsExact(boolean anyInt, ValueRange a, ValueRange b, long[] interval) {
        boolean inexact = a.kind() == ValueKind.LOW16 || (b != null && b.kind() == ValueKind.LOW16);
        if (inexact && !anyInt) {
            return new ValueRange(ValueKind.LOW16, ValueRange.INT_MIN, ValueRange.INT_MAX);
        }
        return result(anyInt, interval);
    }

    private static ValueRange result(boolean anyInt, long[] interval) {
        return anyInt ? ValueRange.intValue(interval[0], interval[1])
                : ValueRange.low16(interval[0], interval[1]);
    }

    /** Interval {@code {lo, hi}} of the JVM result (bounds may leave the int range). */
    @SuppressWarnings("java:S1479") // one case per operation
    static long[] interval(FlowValue.Op op, ValueRange a, ValueRange b) {
        return switch (op) {
            case ADD, INC -> new long[]{a.lo() + b.lo(), a.hi() + b.hi()};
            case SUB -> new long[]{a.lo() - b.hi(), a.hi() - b.lo()};
            case MUL -> multiply(a, b);
            case NEG -> new long[]{-a.hi(), -a.lo()};
            case DIV -> divide(a, b);
            case REM -> remainder(a, b);
            case AND -> and(a, b);
            case OR -> or(a, b);
            case XOR -> xor(a, b);
            case SHL -> shiftLeft(a, b);
            case SHR -> shiftRight(a, b);
            case USHR -> unsignedShiftRight(a, b);
            default -> throw new IllegalStateException("no interval for " + op);
        };
    }

    private static long[] multiply(ValueRange a, ValueRange b) {
        long p1 = a.lo() * b.lo();
        long p2 = a.lo() * b.hi();
        long p3 = a.hi() * b.lo();
        long p4 = a.hi() * b.hi();
        return new long[]{Math.min(Math.min(p1, p2), Math.min(p3, p4)),
                Math.max(Math.max(p1, p2), Math.max(p3, p4))};
    }

    /**
     * |a / b| &lt;= |a|; if b cannot be -1, the quotient is a itself (b = 1) or at most |a| / 2 in
     * magnitude, so -32768 / -1, the only overflow, is excluded.
     */
    private static long[] divide(ValueRange a, ValueRange b) {
        long m = Math.max(Math.abs(a.lo()), Math.abs(a.hi()));
        if (b.lo() > -1 || b.hi() < -1) {
            return new long[]{Math.min(a.lo(), -(m / 2)), Math.max(a.hi(), m / 2)};
        }
        return new long[]{-m, m};
    }

    /** The remainder has the sign of a and a smaller magnitude than both a and b. */
    private static long[] remainder(ValueRange a, ValueRange b) {
        long bound = Math.max(Math.abs(b.lo()), Math.abs(b.hi())) - 1;
        if (bound < 0) {
            return new long[]{0, 0};
        }
        long lo = a.lo() < 0 ? Math.max(a.lo(), -bound) : 0;
        long hi = a.hi() > 0 ? Math.min(a.hi(), bound) : 0;
        return new long[]{lo, hi};
    }

    private static long[] and(ValueRange a, ValueRange b) {
        if (a.lo() >= 0 || b.lo() >= 0) {
            long hi = Long.MAX_VALUE;
            if (a.lo() >= 0) {
                hi = a.hi();
            }
            if (b.lo() >= 0) {
                hi = Math.min(hi, b.hi());
            }
            return new long[]{0, hi};
        }
        // both may be negative: the result keeps the common sign bits
        return new long[]{-powerOfTwoAtLeast(-Math.min(a.lo(), b.lo())), Math.max(a.hi(), b.hi())};
    }

    private static long[] or(ValueRange a, ValueRange b) {
        boolean negative = a.lo() < 0 || b.lo() < 0;
        long lo = negative ? Math.min(a.lo(), b.lo()) : Math.max(a.lo(), b.lo());
        long hi = a.hi() < 0 || b.hi() < 0 ? -1 : powerOfTwoAtLeast(Math.max(a.hi(), b.hi()) + 1) - 1;
        return new long[]{lo, hi};
    }

    /** Values in [-2^k, 2^k - 1] stay there under xor (the bits above k are sign copies). */
    private static long[] xor(ValueRange a, ValueRange b) {
        if (a.lo() >= 0 && b.lo() >= 0) {
            return new long[]{0, powerOfTwoAtLeast(Math.max(a.hi(), b.hi()) + 1) - 1};
        }
        long magnitude = Math.max(Math.max(-a.lo(), a.hi() + 1), Math.max(-b.lo(), b.hi() + 1));
        long bound = powerOfTwoAtLeast(magnitude);
        return new long[]{-bound, bound - 1};
    }

    private static long[] shiftLeft(ValueRange a, ValueRange b) {
        if (a.lo() == 0 && a.hi() == 0) {
            return new long[]{0, 0};
        }
        if (b.lo() != b.hi()) {
            return new long[]{ValueRange.INT_MIN, ValueRange.INT_MAX};
        }
        int s = (int) (b.lo() & 0x1F);
        return new long[]{a.lo() << s, a.hi() << s};
    }

    private static long[] shiftRight(ValueRange a, ValueRange b) {
        if (b.lo() == b.hi()) {
            int s = (int) (b.lo() & 0x1F);
            return new long[]{a.lo() >> s, a.hi() >> s};
        }
        return new long[]{Math.min(a.lo(), 0), Math.max(a.hi(), -1)};
    }

    private static long[] unsignedShiftRight(ValueRange a, ValueRange b) {
        if (a.lo() >= 0) {
            return shiftRight(a, b);
        }
        if (b.lo() != b.hi()) {
            return new long[]{ValueRange.INT_MIN, ValueRange.INT_MAX};
        }
        int s = (int) (b.lo() & 0x1F);
        if (s == 0) {
            return new long[]{a.lo(), a.hi()};
        }
        // negative values become large positive values: (2^32 + x) >>> s, monotone in x
        long lo = a.hi() >= 0 ? 0 : ((a.lo() & 0xFFFFFFFFL) >>> s);
        long hi = ((Math.min(a.hi(), -1) & 0xFFFFFFFFL) >>> s);
        if (a.hi() >= 0) {
            hi = Math.max(hi, a.hi() >>> s);
        }
        return new long[]{lo, hi};
    }

    /** Smallest power of two that is at least {@code n} (n &gt;= 1). */
    private static long powerOfTwoAtLeast(long n) {
        long p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }
}
