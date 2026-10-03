package name.velikodniy.jcexpress.converter.check;

import java.util.Map;

/**
 * Validates JVM type descriptors, internal class names and method references against the types
 * of the Java Card language subset (JCVM 3.1 §2.2.1.3, §2.2.1.4).
 *
 * <p>This class is used by {@link SubsetChecker} during <strong>Stage 2: Subset
 * Check</strong> of the converter pipeline. It detects:
 *
 * <h2>Unsupported primitive types (§2.2.1.3)</h2>
 * <ul>
 *   <li>{@code C} -- {@code char}</li>
 *   <li>{@code J} -- {@code long}</li>
 *   <li>{@code F} -- {@code float}</li>
 *   <li>{@code D} -- {@code double}</li>
 *   <li>arrays of more than one dimension</li>
 * </ul>
 *
 * <h2>Unsupported classes (§2.2.1.4)</h2>
 * <p>The classes that §2.2.1.4 names as unsupported ("String, Thread (and all thread-related
 * classes), wrapper classes such as Boolean and Integer, and class Class", and System in
 * §2.2.1.4.1), and the classes javac uses to implement unsupported language constructs
 * (string concatenation, enums, records, assertions). Each message names the construct and,
 * where there is one, the Java Card replacement.
 *
 * <p>References to other Java SE API classes and members (for example {@code java.lang.Math},
 * {@code Object.hashCode()} or the {@code java.util.Objects.requireNonNull} null checks that
 * javac inserts) are not language-subset questions: the converter's link check reports them
 * against the export files of the target platform, with the referencing method and line.
 *
 * <p>This class is a stateless utility with no public constructor.
 *
 * @see SubsetChecker
 * @see ForbiddenOpcodes
 */
public final class ForbiddenTypes {

    private ForbiddenTypes() {}

    private static final String SUBSET = " (JCVM 3.1 §2.2.1.4)";
    private static final String WRAPPER = "wrapper classes (and autoboxing) are not supported";
    private static final String THREADS = "threads are not supported (JCVM 3.1 §2.2.1.1.4)";
    private static final String CONCAT = "string concatenation is not supported";

    /** Unsupported classes named by JCVM 3.1 §2.2.1.4 or used by javac for unsupported constructs. */
    private static final Map<String, String> UNSUPPORTED_CLASSES = Map.ofEntries(
            Map.entry("java/lang/String", "String objects and literals are not supported; use byte arrays"),
            Map.entry("java/lang/StringBuilder", CONCAT),
            Map.entry("java/lang/StringBuffer", CONCAT),
            Map.entry("java/lang/System", "use javacard.framework.JCSystem and javacard.framework.Util"
                    + " instead (JCVM 3.1 §2.2.1.4.1)"),
            Map.entry("java/lang/Class", "class literals and reflection are not supported"),
            Map.entry("java/lang/Enum", "enums are not supported (JCVM 3.1 §2.2.1.1.7)"),
            Map.entry("java/lang/Record", "records are not supported"),
            Map.entry("java/lang/AssertionError", "assert statements are not supported"
                    + " (JCVM 3.1 §2.2.1.1.11)"),
            Map.entry("java/lang/Thread", THREADS),
            Map.entry("java/lang/ThreadLocal", THREADS),
            Map.entry("java/lang/Runnable", THREADS),
            Map.entry("java/lang/Boolean", WRAPPER),
            Map.entry("java/lang/Byte", WRAPPER),
            Map.entry("java/lang/Short", WRAPPER),
            Map.entry("java/lang/Character", WRAPPER),
            Map.entry("java/lang/Integer", WRAPPER),
            Map.entry("java/lang/Long", WRAPPER),
            Map.entry("java/lang/Float", WRAPPER),
            Map.entry("java/lang/Double", WRAPPER),
            Map.entry("java/lang/Number", WRAPPER)
    );

    /**
     * Checks whether a JVM type descriptor contains a type the Java Card platform does not have.
     * Works for field descriptors ({@code "J"}, {@code "[Ljava/lang/String;"}) and method
     * descriptors ({@code "(BJ)V"}).
     *
     * @param descriptor a JVM type or method descriptor
     * @return a human-readable reason if a type is not supported, or {@code null} if the
     *         descriptor is clean
     */
    public static String checkDescriptor(String descriptor) {
        if (descriptor.contains("[[")) {
            return "arrays of more than one dimension not supported in JavaCard (JCVM 3.1 §2.2.1.3)";
        }
        for (int i = 0; i < descriptor.length(); i++) {
            char c = descriptor.charAt(i);
            if (c == '(' || c == ')' || c == '[') continue;
            if (c == 'L') {
                int end = descriptor.indexOf(';', i);
                if (end < 0) break;
                String reason = checkInternalName(descriptor.substring(i + 1, end));
                if (reason != null) return reason;
                i = end;
                continue;
            }
            String reason = checkPrimitiveDescriptor(c);
            if (reason != null) return reason;
        }
        return null;
    }

    /**
     * Checks whether a class referenced by its internal name is one of the unsupported classes
     * of JCVM 3.1 §2.2.1.4 (see the class description). Every other class is left to the link
     * check against the export files.
     *
     * @param internalName the internal class name (e.g. {@code "java/lang/String"})
     * @return a human-readable reason if the class is unsupported, or {@code null}
     */
    public static String checkInternalName(String internalName) {
        String hint = UNSUPPORTED_CLASSES.get(internalName);
        if (hint == null) {
            return null;
        }
        // a hint that cites its own section (e.g. System, §2.2.1.4.1) needs no second reference
        return internalName + " is not part of the Java Card platform: " + hint
                + (hint.contains("JCVM 3.1 §") ? "" : SUBSET);
    }

    /**
     * Checks a method invocation for unsupported owner classes and signature types, and for
     * methods invoked on arrays: javac names the array type as the owner of {@code clone()},
     * and the Java Card platform supports no cloning (JCVM 3.1 §2.2.1.1.5).
     *
     * @param owner      internal name of the class (or array type) named by the instruction
     * @param name       method name
     * @param descriptor method descriptor
     * @return a reason if forbidden, otherwise {@code null}
     */
    public static String checkInvocation(String owner, String name, String descriptor) {
        if (owner.startsWith("[")) {
            return "clone".equals(name) ? "cloning is not supported (JCVM 3.1 §2.2.1.1.5)"
                    : "method " + name + descriptor + " of an array type is not supported on the"
                    + " Java Card platform";
        }
        String reason = checkInternalName(owner);
        return reason != null ? reason : checkDescriptor(descriptor);
    }

    /**
     * Returns whether a method descriptor has an int or int[] parameter or return type, which
     * needs int support (JCVM 3.1 §2.2.3.1, §6.4 ACC_INT).
     *
     * @param methodDescriptor method descriptor, e.g. {@code (I)V}
     * @return {@code true} if the int type is used
     */
    public static boolean usesInt(String methodDescriptor) {
        int i = 0;
        while (i < methodDescriptor.length()) {
            char c = methodDescriptor.charAt(i);
            if (c == 'L') {
                i = methodDescriptor.indexOf(';', i) + 1;
            } else if (c == 'I') {
                return true;
            } else {
                i++;
            }
        }
        return false;
    }

    private static String checkPrimitiveDescriptor(char c) {
        return switch (c) {
            case 'C' -> "char type not supported in JavaCard (JCVM 3.1 §2.2.1.3)";
            case 'J' -> "long type not supported in JavaCard (JCVM 3.1 §2.2.1.3)";
            case 'F' -> "float type not supported in JavaCard (JCVM 3.1 §2.2.1.3)";
            case 'D' -> "double type not supported in JavaCard (JCVM 3.1 §2.2.1.3)";
            default -> null;
        };
    }
}
