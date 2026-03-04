package name.velikodniy.jcexpress.converter.check;

/**
 * Represents a single JavaCard subset violation detected during
 * <strong>Stage 2: Subset Check</strong> of the converter pipeline.
 *
 * <p>A violation is produced by {@link SubsetChecker} whenever a class uses a JVM
 * feature that is not part of the Java Card Virtual Machine specification (JCVM
 * 3.0.5, Section 3.1--3.2). Violations may originate from:
 * <ul>
 *   <li><strong>Descriptor-level checks</strong> -- forbidden types in field or
 *       method descriptors, or in superclass/interface references. These violations
 *       have a {@link #bci()} of {@code -1}.</li>
 *   <li><strong>Bytecode-level checks</strong> -- forbidden opcodes encountered in
 *       a method body. These violations include the bytecode index ({@link #bci()})
 *       of the offending instruction.</li>
 * </ul>
 *
 * <p>The {@link #toString()} method produces a human-readable message suitable for
 * error reporting, for example:
 * <pre>
 * com/example/MyApplet -> getFloat()F [bci 3]: float type not supported in JavaCard
 * com/example/MyApplet -> field doubleField: double type not supported in JavaCard
 * </pre>
 *
 * <p>Two constructors are provided: a full four-argument canonical constructor and a
 * convenience three-argument constructor that sets {@code bci} to {@code -1} for
 * descriptor-level violations.
 *
 * @param className the internal class name where the violation was found
 *                  (e.g., {@code "com/example/MyApplet"})
 * @param context   a description of where in the class the violation occurred --
 *                  typically a method signature (e.g., {@code "getFloat()F"}), a
 *                  field reference (e.g., {@code "field doubleField"}), or a
 *                  structural element (e.g., {@code "superclass"},
 *                  {@code "interface java/lang/Thread"})
 * @param bci       the bytecode index of the offending instruction within the
 *                  method body, or {@code -1} if the violation is at the descriptor
 *                  level rather than the bytecode level
 * @param message   a human-readable description of the restriction that was violated
 *                  (e.g., {@code "float type not supported in JavaCard"})
 *
 * @see SubsetChecker
 * @see ForbiddenOpcodes
 * @see ForbiddenTypes
 */
public record Violation(String className, String context, int bci, String message) {

    /**
     * Convenience constructor for descriptor-level violations that do not have an
     * associated bytecode index.
     *
     * <p>Sets {@link #bci()} to {@code -1}.
     *
     * @param className the internal class name where the violation was found
     * @param context   where in the class the violation occurred
     * @param message   a human-readable description of the violation
     */
    public Violation(String className, String context, String message) {
        this(className, context, -1, message);
    }

    /**
     * Returns a human-readable representation of this violation.
     *
     * <p>If a bytecode index is present ({@code bci >= 0}), it is included in the
     * output as {@code [bci N]}. Otherwise, only the class name, context, and
     * message are shown.
     *
     * @return a formatted string describing the violation
     */
    @Override
    public String toString() {
        if (bci >= 0) {
            return className + " → " + context + " [bci " + bci + "]: " + message;
        }
        return className + " → " + context + ": " + message;
    }
}
