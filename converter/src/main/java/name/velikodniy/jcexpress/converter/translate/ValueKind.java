package name.velikodniy.jcexpress.converter.translate;

/**
 * What the converter knows about a JVM operand stack or local variable value.
 *
 * <p>javac evaluates byte, short and boolean expressions in 32-bit int arithmetic. JCVM 3.1
 * §2.2.3.1 requires that "the result of an arithmetic expression produced by a Java Card virtual
 * machine must be equal to the result produced by a Java virtual machine, regardless of the input
 * values", and that a virtual machine without int support rejects expressions that could produce
 * a different result. The kinds below track exactly how much of the 32-bit JVM value a 16-bit
 * JCVM computation reproduces:
 * <ul>
 *   <li>{@link #SHORT}: the JVM value is in the short range, so a short computes it exactly;</li>
 *   <li>{@link #LOW16}: only the low 16 bits are reproduced (e.g. {@code a + b} of two shorts);
 *       fine for consumers that truncate (i2s, i2b, bastore, sastore, short and byte field
 *       stores), wrong for consumers that see the whole value (comparisons, division, right
 *       shifts, array indices, arguments, returns);</li>
 *   <li>{@link #INT}: a 32-bit value (int fields, arrays, parameters, constants outside the
 *       short range, and results computed from them);</li>
 *   <li>{@link #REFERENCE}: an object or array reference;</li>
 *   <li>{@link #UNUSABLE}: incompatible values merged at a join point (never used by
 *       verifiable code).</li>
 * </ul>
 */
enum ValueKind {
    SHORT,
    LOW16,
    INT,
    REFERENCE,
    UNUSABLE;

    /** Whether this is one of the numeric kinds {@link #SHORT}, {@link #LOW16}, {@link #INT}. */
    boolean isNumeric() {
        return this == SHORT || this == LOW16 || this == INT;
    }

    /**
     * Least upper bound; {@code null} stands for "no information yet" (bottom).
     *
     * @param a first kind or {@code null}
     * @param b second kind or {@code null}
     * @return the join
     */
    static ValueKind join(ValueKind a, ValueKind b) {
        if (a == null) {
            return b;
        }
        if (b == null || a == b) {
            return a;
        }
        if (a.isNumeric() && b.isNumeric()) {
            return a.ordinal() > b.ordinal() ? a : b;
        }
        return UNUSABLE;
    }
}
