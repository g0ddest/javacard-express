package name.velikodniy.jcexpress.converter.check;

import java.util.Set;

/**
 * Validates JVM type descriptors and internal class names against the set of types
 * forbidden on the Java Card platform.
 *
 * <p>This class is used by {@link SubsetChecker} during <strong>Stage 2: Subset
 * Check</strong> of the converter pipeline. It detects forbidden types at two levels:
 *
 * <h2>Forbidden primitive types</h2>
 * <p>The following JVM primitive type descriptors are rejected because the JCVM does
 * not support these data types (JCVM 3.0.5, Section 3.1):
 * <ul>
 *   <li>{@code J} -- {@code long} (64-bit integer)</li>
 *   <li>{@code F} -- {@code float} (32-bit IEEE 754)</li>
 *   <li>{@code D} -- {@code double} (64-bit IEEE 754)</li>
 * </ul>
 *
 * <h2>Forbidden reference types</h2>
 * <p>Certain {@code java.lang} classes have no equivalent on the Java Card platform
 * and are rejected when found in field descriptors, method descriptors, superclass
 * references, or implemented interface lists:
 * <ul>
 *   <li>{@code java/lang/String} -- Java Card has no string support</li>
 *   <li>{@code java/lang/Thread} -- Java Card has no threading</li>
 *   <li>{@code java/lang/Float} -- boxed float, unsupported</li>
 *   <li>{@code java/lang/Double} -- boxed double, unsupported</li>
 *   <li>{@code java/lang/Long} -- boxed long, unsupported</li>
 * </ul>
 *
 * <p>The two main entry points are {@link #checkDescriptor(String)} for JVM type and
 * method descriptors (e.g., {@code "(BSS)V"}, {@code "Ljava/lang/String;"}), and
 * {@link #checkInternalName(String)} for raw internal class names (e.g.,
 * {@code "java/lang/Thread"}).
 *
 * <p>This class is a stateless utility with no public constructor.
 *
 * @see SubsetChecker
 * @see ForbiddenOpcodes
 */
public final class ForbiddenTypes {

    private ForbiddenTypes() {}

    private static final Set<String> FORBIDDEN_CLASSES = Set.of(
            "java/lang/String",
            "java/lang/Thread",
            "java/lang/Float",
            "java/lang/Double",
            "java/lang/Long"
    );

    /**
     * Checks a JVM type descriptor for forbidden primitive or reference types.
     *
     * <p>The descriptor may be a field descriptor (e.g., {@code "I"}, {@code "[B"},
     * {@code "Ljava/lang/String;"}) or a method descriptor (e.g., {@code "(BSS)V"}).
     * The method parses the descriptor character-by-character, checking each
     * primitive type character ({@code J}, {@code F}, {@code D}) and each object
     * reference ({@code L...;}) against the forbidden sets.
     *
     * @param descriptor the JVM type or method descriptor string
     * @return a human-readable reason if a forbidden type is found, or {@code null}
     *         if the descriptor is clean
     */
    public static String checkDescriptor(String descriptor) {
        for (int i = 0; i < descriptor.length(); i++) {
            char c = descriptor.charAt(i);
            if (c == '(' || c == ')' || c == '[') continue;
            if (c == 'L') {
                int semi = descriptor.indexOf(';', i);
                if (semi > i) {
                    String className = descriptor.substring(i + 1, semi);
                    if (FORBIDDEN_CLASSES.contains(className)) {
                        return className + " not supported in JavaCard";
                    }
                    i = semi;
                }
                continue;
            }
            String reason = checkPrimitiveDescriptor(c);
            if (reason != null) return reason;
        }
        return null;
    }

    /**
     * Checks an internal class name against the forbidden class list.
     *
     * <p>Internal names use slash-separated format (e.g., {@code "java/lang/Thread"})
     * as found in the class file constant pool, superclass entries, and interface
     * tables.
     *
     * @param internalName the JVM internal class name to check
     * @return a human-readable reason if the class is forbidden, or {@code null}
     *         if the class is allowed
     */
    public static String checkInternalName(String internalName) {
        if (FORBIDDEN_CLASSES.contains(internalName)) {
            return internalName + " not supported in JavaCard";
        }
        return null;
    }

    private static String checkPrimitiveDescriptor(char c) {
        return switch (c) {
            case 'J' -> "long type not supported in JavaCard";
            case 'F' -> "float type not supported in JavaCard";
            case 'D' -> "double type not supported in JavaCard";
            default -> null;
        };
    }
}
