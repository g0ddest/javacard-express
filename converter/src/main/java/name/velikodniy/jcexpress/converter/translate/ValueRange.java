package name.velikodniy.jcexpress.converter.translate;

/**
 * What the converter knows about one JVM value: its {@link ValueKind} and, for numeric values,
 * an interval {@code [lo, hi]} that contains every value the Java virtual machine can compute for
 * it (32-bit int semantics, JVMS §2.11.1).
 *
 * <p>The interval decides whether a 16-bit Java Card computation is exact (JCVM 3.1 §2.2.3.1):
 * a value whose operations are reproduced modulo 2<sup>16</sup> ({@link ValueKind#SHORT} or
 * {@link ValueKind#LOW16}) is exact when its interval lies in the short range. For example the
 * sum of two bytes is in {@code [-256, 254]} and therefore exact, while the sum of two shorts
 * may need 17 bits.
 *
 * @param kind kind of the value
 * @param lo   lowest possible JVM value (numeric kinds only)
 * @param hi   highest possible JVM value (numeric kinds only)
 */
record ValueRange(ValueKind kind, long lo, long hi) {

    static final long INT_MIN = Integer.MIN_VALUE;
    static final long INT_MAX = Integer.MAX_VALUE;

    /** Any value of type short. */
    static final ValueRange SHORT = new ValueRange(ValueKind.SHORT, Short.MIN_VALUE, Short.MAX_VALUE);
    /** Any value of type byte. */
    static final ValueRange BYTE = new ValueRange(ValueKind.SHORT, Byte.MIN_VALUE, Byte.MAX_VALUE);
    /** A boolean, or the result of instanceof. */
    static final ValueRange BOOLEAN = new ValueRange(ValueKind.SHORT, 0, 1);
    /** An array length: Java Card arrays have at most 32767 components (JCVM 3.1 §2.2.4.3.3). */
    static final ValueRange ARRAY_LENGTH = new ValueRange(ValueKind.SHORT, 0, Short.MAX_VALUE);
    /** Any value of type int. */
    static final ValueRange INT = new ValueRange(ValueKind.INT, INT_MIN, INT_MAX);
    /** An object or array reference. */
    static final ValueRange REFERENCE = new ValueRange(ValueKind.REFERENCE, 0, 0);
    /** A value outside the Java Card subset (long, float, double) or a mix of incompatible values. */
    static final ValueRange UNUSABLE = new ValueRange(ValueKind.UNUSABLE, 0, 0);

    /**
     * Returns the range of an int constant: exact if it fits in a short, otherwise of kind
     * {@link ValueKind#INT} (JCVM 3.1 §2.2.3.1: such a constant needs the int type).
     *
     * @param value the constant
     * @return its range
     */
    static ValueRange constant(int value) {
        boolean fits = value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
        return new ValueRange(fits ? ValueKind.SHORT : ValueKind.INT, value, value);
    }

    /**
     * Returns the range of a value of the given field descriptor type (JVMS §4.3.2).
     *
     * @param descriptor field descriptor, e.g. {@code B}, {@code I}, {@code [B}
     * @return its range; {@code char} is modelled as its unsigned 16-bit range
     */
    static ValueRange ofType(String descriptor) {
        return switch (descriptor.charAt(0)) {
            case 'B' -> BYTE;
            case 'Z' -> BOOLEAN;
            case 'S' -> SHORT;
            case 'C' -> new ValueRange(ValueKind.LOW16, Character.MIN_VALUE, Character.MAX_VALUE);
            case 'I' -> INT;
            case 'L', '[' -> REFERENCE;
            default -> UNUSABLE;
        };
    }

    /**
     * Returns a numeric range whose kind follows from the interval: {@link ValueKind#SHORT} if it
     * lies in the short range, otherwise {@link ValueKind#LOW16}. An interval that leaves the int
     * range wraps around in the JVM and becomes the whole int range.
     *
     * @param lo lowest value (may lie outside the int range)
     * @param hi highest value (may lie outside the int range)
     * @return the range
     */
    static ValueRange low16(long lo, long hi) {
        if (lo < INT_MIN || hi > INT_MAX) {
            return new ValueRange(ValueKind.LOW16, INT_MIN, INT_MAX);
        }
        boolean exact = lo >= Short.MIN_VALUE && hi <= Short.MAX_VALUE;
        return new ValueRange(exact ? ValueKind.SHORT : ValueKind.LOW16, lo, hi);
    }

    /**
     * Returns a range of kind {@link ValueKind#INT} (a value that needs 32-bit arithmetic).
     *
     * @param lo lowest value (may lie outside the int range)
     * @param hi highest value (may lie outside the int range)
     * @return the range
     */
    static ValueRange intValue(long lo, long hi) {
        if (lo < INT_MIN || hi > INT_MAX) {
            return INT;
        }
        return new ValueRange(ValueKind.INT, lo, hi);
    }

    /** Whether this is a numeric range ({@link ValueKind#isNumeric()}). */
    boolean isNumeric() {
        return kind.isNumeric();
    }

    /** Whether the interval lies within {@code [min, max]}. */
    boolean within(long min, long max) {
        return lo >= min && hi <= max;
    }

    /**
     * Least upper bound; {@code null} stands for "no information yet".
     *
     * @param a first range or {@code null}
     * @param b second range or {@code null}
     * @return the join
     */
    static ValueRange join(ValueRange a, ValueRange b) {
        if (a == null) {
            return b;
        }
        if (b == null || a.equals(b)) {
            return a;
        }
        if (a.isNumeric() && b.isNumeric()) {
            ValueKind kind = ValueKind.join(a.kind, b.kind);
            return new ValueRange(kind, Math.min(a.lo, b.lo), Math.max(a.hi, b.hi));
        }
        return a.kind == b.kind ? a : UNUSABLE;
    }

    /**
     * Widens {@code next} against the previous range of the same join point so that loops reach
     * a fixpoint: a bound that moved is pushed to the next of a few thresholds (byte, short and
     * int limits), which keeps exactness decisions unchanged.
     *
     * @param previous range computed in the previous iteration, or {@code null}
     * @param next     newly joined range
     * @return the widened range
     */
    static ValueRange widen(ValueRange previous, ValueRange next) {
        if (previous == null || next == null || !previous.isNumeric() || !next.isNumeric()) {
            return next;
        }
        long lo = next.lo < previous.lo ? lowerThreshold(next.lo) : previous.lo;
        long hi = next.hi > previous.hi ? upperThreshold(next.hi) : previous.hi;
        return new ValueRange(ValueKind.join(previous.kind, next.kind), lo, hi);
    }

    private static long lowerThreshold(long value) {
        for (long t : new long[]{0, Byte.MIN_VALUE, Short.MIN_VALUE}) {
            if (value >= t) {
                return t;
            }
        }
        return INT_MIN;
    }

    private static long upperThreshold(long value) {
        for (long t : new long[]{1, Byte.MAX_VALUE, 0xFF, Short.MAX_VALUE}) {
            if (value <= t) {
                return t;
            }
        }
        return INT_MAX;
    }
}
