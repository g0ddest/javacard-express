package name.velikodniy.jcexpress.converter.check;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that a set of parsed classes only uses the Java language subset supported by the
 * Java Card platform (JCVM 3.1 §2.2).
 *
 * <p>This class implements <strong>Stage 2: Subset Check</strong> of the converter pipeline.
 * After Stage 1 (class file parsing via {@code ClassFileReader}) produces {@link ClassInfo}
 * objects, this checker inspects every class, field, method and instruction. If any violations
 * are found, the converter aborts before Stage 3 (token assignment); every violation names the
 * class, the member and, when the class file has a LineNumberTable, the source line.
 *
 * <h2>What is checked</h2>
 * <ul>
 *   <li><strong>Types</strong> (§2.2.1.3, §2.2.1.4): char, long, float, double, arrays of more
 *       than one dimension, and the unsupported classes named by §2.2.1.4 (String, Thread,
 *       wrapper classes, Class, System) or used by javac for unsupported constructs, in
 *       superclasses, interfaces, field and method signatures, instructions and catch clauses
 *       (see {@link ForbiddenTypes}). Other Java SE API references are reported by the link
 *       check against the export files of the target platform.</li>
 *   <li><strong>Class kinds</strong>: enums (§2.2.1.1.7), annotation types (§2.2.1.1.10) and
 *       records.</li>
 *   <li><strong>Keywords</strong> (§2.2.1.2): native, synchronized (methods and blocks),
 *       volatile, transient, strictfp; varargs (§2.2.1.1.9), assert (§2.2.1.1.11) and cloning
 *       (§2.2.1.1.5).</li>
 *   <li><strong>Instructions</strong>: long, float and double operations, monitors,
 *       invokedynamic (lambdas, string concatenation), multianewarray, char arrays, String and
 *       class literal constants; jsr/ret subroutines that were not inlined first (the converter
 *       inlines them when it reads the class files, §2.3.2.2).</li>
 *   <li><strong>Interfaces</strong>: methods with a body (default, static or private interface
 *       methods) cannot be represented (§6.10).</li>
 *   <li><strong>Static final constants</strong> (§2.2.4.6): a static final field of a primitive
 *       type must be a compile-time constant; a blank final assigned in {@code <clinit>} is
 *       rejected at the assignment.</li>
 *   <li><strong>Access control</strong> (§2.2.1.1.6): the public API of the package must not
 *       expose package-visible classes and interfaces ({@link AccessRules}); no access to a
 *       private member of another class that could not be made package-visible
 *       ({@link name.velikodniy.jcexpress.converter.input.NestmateAccess}).</li>
 *   <li><strong>Integer data type</strong> (§2.2.3.1): without int support, int fields,
 *       parameters, locals, constants and 32-bit intermediate values that could change a result
 *       (see {@link name.velikodniy.jcexpress.converter.translate.IntRules}); in any case,
 *       array indices and sizes must be short values (§2.2.1.1.8).</li>
 * </ul>
 *
 * <p>This class is a stateless utility. All checking is performed through the static
 * {@link #check(List, boolean)} method, which returns an immutable list of {@link Violation}s.
 *
 * @see ForbiddenOpcodes
 * @see ForbiddenTypes
 * @see Violation
 */
public final class SubsetChecker {

    private static final int ACC_VOLATILE = 0x0040;
    private static final int ACC_TRANSIENT = 0x0080;
    private static final int ACC_ANNOTATION = 0x2000;
    private static final int ACC_ENUM = 0x4000;

    private SubsetChecker() {}

    /**
     * Checks all classes for a target without int support (the converter default).
     *
     * @param classes the parsed classes from Stage 1
     * @return an immutable list of violations; empty if all classes are compliant
     * @see #check(List, boolean)
     */
    public static List<Violation> check(List<ClassInfo> classes) {
        return check(classes, false);
    }

    /**
     * Checks all classes for JavaCard subset violations.
     *
     * @param classes      the parsed {@link ClassInfo} objects from Stage 1
     * @param intSupported whether the target supports the optional int type (JCVM 3.1
     *                     §2.2.3.1); without it every use of int is a violation
     * @return an immutable list of violations; empty if all classes are compliant
     */
    public static List<Violation> check(List<ClassInfo> classes, boolean intSupported) {
        List<Violation> violations = new ArrayList<>();
        for (ClassInfo ci : classes) {
            checkClass(ci, intSupported, violations, classes);
        }
        AccessRules.check(classes, violations);
        return List.copyOf(violations);
    }

    private static void checkClass(ClassInfo ci, boolean intSupported, List<Violation> violations,
                                   List<ClassInfo> packageClasses) {
        String unsupportedKind = unsupportedKind(ci);
        if (unsupportedKind != null) {
            // one clear message instead of the many consequences (java.lang.Enum, clone(), ...)
            violations.add(classViolation(ci, "class", unsupportedKind));
            return;
        }
        if (ci.superClass() != null) {
            String reason = ForbiddenTypes.checkInternalName(ci.superClass());
            if (reason != null) {
                violations.add(classViolation(ci, "superclass", reason));
            }
        }
        for (String iface : ci.interfaces()) {
            String reason = ForbiddenTypes.checkInternalName(iface);
            if (reason != null) {
                violations.add(classViolation(ci, "interface " + iface, reason));
            }
        }
        for (FieldInfo fi : ci.fields()) {
            checkField(ci, fi, intSupported, violations);
        }
        for (MethodInfo mi : ci.methods()) {
            new MethodChecker(ci, mi, intSupported, violations, packageClasses).check();
        }
    }

    /** Class kinds that the Java Card language subset does not have, or {@code null}. */
    private static String unsupportedKind(ClassInfo ci) {
        if ((ci.accessFlags() & ACC_ENUM) != 0) {
            return "enum types are not supported (JCVM 3.1 §2.2.1.1.7)";
        }
        if ((ci.accessFlags() & ACC_ANNOTATION) != 0) {
            return "annotation types are not supported (JCVM 3.1 §2.2.1.1.10)";
        }
        if ("java/lang/Record".equals(ci.superClass())) {
            return "records are not supported: java/lang/Record is not part of the Java Card platform"
                    + " (JCVM 3.1 §2.2.1.4)";
        }
        return null;
    }

    private static void checkField(ClassInfo ci, FieldInfo fi, boolean intSupported,
                                   List<Violation> violations) {
        String context = "field " + fi.name();
        String reason = ForbiddenTypes.checkDescriptor(fi.descriptor());
        if (reason != null) {
            violations.add(classViolation(ci, context, reason));
        } else if (!intSupported && (fi.descriptor().equals("I") || fi.descriptor().equals("[I"))) {
            violations.add(classViolation(ci, context, "field of type "
                    + (fi.descriptor().equals("I") ? "int" : "int[]")
                    + " needs int support (JCVM 3.1 §2.2.3.1)"));
        }
        if ((fi.accessFlags() & ACC_VOLATILE) != 0) {
            violations.add(classViolation(ci, context, "volatile fields are not supported (JCVM 3.1 §2.2.1.2)"));
        }
        if ((fi.accessFlags() & ACC_TRANSIENT) != 0) {
            violations.add(classViolation(ci, context, "transient fields are not supported (JCVM 3.1 §2.2.1.2);"
                    + " use JCSystem.makeTransient*Array for transient data"));
        }
    }

    private static Violation classViolation(ClassInfo ci, String context, String message) {
        return new Violation(ci.thisClass(), context, -1, message, ci.sourceFile().orElse(null), -1);
    }
}
