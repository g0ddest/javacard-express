package javacardx.security;

/**
 * Protects security-relevant decisions against fault attacks. Supported API methods record their result here;
 * the applet then asserts that the value it is about to rely on equals the recorded one, and a mismatch (for
 * example caused by a glitch) raises a {@link SecurityException}.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public final class SensitiveResult {

    private SensitiveResult() {
    }

    /**
     * Asserts that the recorded result is the given object reference.
     *
     * @param obj the expected result
     * @throws SecurityException if the recorded result differs or is not available
     */
    public static void assertEquals(Object obj) throws SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Asserts that the recorded boolean result is {@code true}.
     *
     * @throws SecurityException if the recorded result differs or is not available
     */
    public static void assertTrue() throws SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Asserts that the recorded boolean result is {@code false}.
     *
     * @throws SecurityException if the recorded result differs or is not available
     */
    public static void assertFalse() throws SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Asserts that the recorded numeric result is negative.
     *
     * @throws SecurityException if the recorded result differs or is not available
     */
    public static void assertNegative() throws SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Asserts that the recorded numeric result is positive.
     *
     * @throws SecurityException if the recorded result differs or is not available
     */
    public static void assertPositive() throws SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Asserts that the recorded numeric result is zero.
     *
     * @throws SecurityException if the recorded result differs or is not available
     */
    public static void assertZero() throws SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Asserts that the recorded numeric result equals a value.
     *
     * @param value the expected result
     * @throws SecurityException if the recorded result differs or is not available
     */
    public static void assertEquals(short value) throws SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Asserts that the recorded numeric result is greater than a value.
     *
     * @param value the value to compare with
     * @throws SecurityException if the recorded result is not greater or is not available
     */
    public static void assertGreaterThan(short value) throws SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Asserts that the recorded numeric result is less than a value.
     *
     * @param value the value to compare with
     * @throws SecurityException if the recorded result is not less or is not available
     */
    public static void assertLessThan(short value) throws SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Discards the recorded result.
     */
    public static void reset() {
        throw new RuntimeException("stub");
    }
}
