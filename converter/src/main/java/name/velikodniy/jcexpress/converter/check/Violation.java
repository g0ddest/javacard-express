package name.velikodniy.jcexpress.converter.check;

/**
 * Represents a single JavaCard subset violation detected during
 * <strong>Stage 2: Subset Check</strong> of the converter pipeline.
 *
 * <p>A violation is produced by {@link SubsetChecker} whenever a class uses a Java feature that
 * is not part of the Java Card platform subset (JCVM 3.1 §2.2). Violations may originate from:
 * <ul>
 *   <li><strong>Declaration-level checks</strong> -- forbidden types or modifiers of classes,
 *       fields and methods. These violations have a {@link #bci()} of {@code -1}.</li>
 *   <li><strong>Bytecode-level checks</strong> -- forbidden instructions or int values in a
 *       method body. These violations include the bytecode index ({@link #bci()}) of the
 *       offending instruction.</li>
 * </ul>
 *
 * <p>When the class file has a LineNumberTable, the source file and line are included, and
 * {@link #toString()} produces for example:
 * <pre>
 * com/example/MyApplet → getFloat()F [bci 3] (MyApplet.java:42): float type not supported in JavaCard
 * com/example/MyApplet → field doubleField: double type not supported in JavaCard
 * </pre>
 *
 * @param className  the internal class name where the violation was found
 *                   (e.g., {@code "com/example/MyApplet"})
 * @param context    a description of where in the class the violation occurred --
 *                   typically a method signature (e.g., {@code "getFloat()F"}), a
 *                   field reference (e.g., {@code "field doubleField"}), or a
 *                   structural element (e.g., {@code "superclass"})
 * @param bci        the bytecode index of the offending instruction within the
 *                   method body, or {@code -1} if the violation is at the declaration level
 * @param message    a human-readable description of the restriction that was violated
 * @param sourceFile the source file name from the SourceFile attribute, or {@code null}
 * @param line       the source line, or {@code -1} if unknown
 *
 * @see SubsetChecker
 * @see ForbiddenOpcodes
 * @see ForbiddenTypes
 */
public record Violation(String className, String context, int bci, String message,
                        String sourceFile, int line) {

    /**
     * Creates a bytecode-level violation without source location.
     *
     * @param className the internal class name where the violation was found
     * @param context   where in the class the violation occurred
     * @param bci       bytecode index of the offending instruction, or -1
     * @param message   a human-readable description of the violation
     */
    public Violation(String className, String context, int bci, String message) {
        this(className, context, bci, message, null, -1);
    }

    /**
     * Convenience constructor for declaration-level violations that do not have an
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
     * Returns a human-readable representation of this violation: class, context, the bytecode
     * index if present ({@code [bci N]}), the source location if known ({@code (File.java:N)}),
     * and the message.
     *
     * @return a formatted string describing the violation
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(className).append(" → ").append(context);
        if (bci >= 0) {
            sb.append(" [bci ").append(bci).append(']');
        }
        if (line >= 0) {
            sb.append(" (").append(sourceFile != null ? sourceFile : "line").append(':')
                    .append(line).append(')');
        }
        return sb.append(": ").append(message).toString();
    }
}
