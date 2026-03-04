package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.check.Violation;

import java.util.List;

/**
 * Thrown when the {@link Converter} encounters errors during the conversion pipeline.
 *
 * <p>A {@code ConverterException} may arise from two categories of failures:
 *
 * <ul>
 *   <li><b>Subset violations</b> -- The input classes use Java features that are not supported
 *       by the JavaCard platform (e.g., {@code long}, {@code float}, {@code double}, threads,
 *       multidimensional arrays). In this case, the {@link #violations()} list contains one or
 *       more {@link Violation} records describing each problem with its class name, context
 *       (method or field), bytecode index, and human-readable message.</li>
 *   <li><b>General conversion errors</b> -- I/O failures, missing classes, unresolvable
 *       references, or other unexpected errors. In this case, {@link #violations()} returns
 *       an empty list and the exception message and {@linkplain #getCause() cause} describe
 *       the failure.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * try {
 *     ConverterResult result = converter.convert();
 * } catch (ConverterException e) {
 *     if (!e.violations().isEmpty()) {
 *         // Subset check failures — show each violation to the user
 *         for (Violation v : e.violations()) {
 *             System.err.println(v);
 *         }
 *     } else {
 *         // General conversion error
 *         System.err.println("Conversion failed: " + e.getMessage());
 *     }
 * }
 * }</pre>
 *
 * @see Converter#convert()
 * @see Violation
 */
public class ConverterException extends Exception {

    private final List<Violation> violations;

    /**
     * Creates an exception with the given message and no violations.
     *
     * @param message description of the error
     */
    public ConverterException(String message) {
        super(message);
        this.violations = List.of();
    }

    /**
     * Creates an exception caused by one or more JavaCard subset violations.
     *
     * <p>The exception message is formed by appending a formatted list of all violations
     * to the given message.
     *
     * @param message    summary description (e.g., "JavaCard subset violations found")
     * @param violations non-empty list of violations that caused the failure
     */
    public ConverterException(String message, List<Violation> violations) {
        super(message + "\n" + formatViolations(violations));
        this.violations = List.copyOf(violations);
    }

    /**
     * Creates an exception wrapping a lower-level cause (e.g., {@link java.io.IOException}).
     *
     * @param message description of the error
     * @param cause   the underlying exception
     */
    public ConverterException(String message, Throwable cause) {
        super(message, cause);
        this.violations = List.of();
    }

    /**
     * Returns the list of JavaCard subset violations that caused this exception.
     *
     * <p>If the exception was caused by subset-check failures, this list contains one or more
     * {@link Violation} records. For general conversion errors (I/O, missing classes, etc.),
     * this returns an empty list.
     *
     * @return unmodifiable list of violations; never {@code null}
     */
    public List<Violation> violations() {
        return violations;
    }

    private static String formatViolations(List<Violation> violations) {
        var sb = new StringBuilder();
        for (Violation v : violations) {
            sb.append("  - ").append(v).append('\n');
        }
        return sb.toString();
    }
}
