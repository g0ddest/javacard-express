package name.velikodniy.jcexpress.converter.check;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The access control restrictions of the Java Card language subset (JCVM 3.1 §2.2.1.1.6): the
 * public API of a package must not expose its package-visible classes and interfaces.
 * <ul>
 *   <li>"A public class cannot contain a public or protected field of type reference to a
 *       package-visible class."</li>
 *   <li>"A public class cannot contain a public or protected method with a return type of type
 *       reference to a package-visible class."</li>
 *   <li>"A public or protected method in a public class cannot contain a formal parameter of type
 *       reference to a package-visible class."</li>
 *   <li>"A package-visible class that is extended by a public class cannot define any public or
 *       protected methods or fields."</li>
 *   <li>"A package-visible interface that is implemented by a public class cannot define any
 *       fields."</li>
 *   <li>"A package-visible interface cannot be extended by an interface with public access
 *       visibility."</li>
 * </ul>
 * Arrays of a package-visible type count as references to it. Constructors are methods
 * ({@code <init>}) of the public API like any other, as export files list them (§5.9); class
 * initializers and members javac generates (synthetic, bridge) are not part of the declared API.
 * The first restriction, about overriding a package-visible method with a public or protected
 * one, is checked during token assignment.
 */
final class AccessRules {

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_PROTECTED = 0x0004;
    private static final int ACC_BRIDGE = 0x0040;
    private static final int ACC_SYNTHETIC = 0x1000;
    private static final String SPEC = " (JCVM 3.1 §2.2.1.1.6)";

    private final Map<String, ClassInfo> byName = new HashMap<>();
    private final List<Violation> violations;

    private AccessRules(List<ClassInfo> classes, List<Violation> violations) {
        classes.forEach(ci -> byName.put(ci.thisClass(), ci));
        this.violations = violations;
    }

    /**
     * Checks the classes of one package.
     *
     * @param classes    all classes of the package
     * @param violations receives the violations
     */
    static void check(List<ClassInfo> classes, List<Violation> violations) {
        AccessRules rules = new AccessRules(classes, violations);
        for (ClassInfo ci : classes) {
            if (isPublic(ci.accessFlags())) {
                rules.publicSignatures(ci);
                rules.packageVisibleSupertypes(ci);
            }
        }
    }

    /** Public and protected fields and methods must not name package-visible types. */
    private void publicSignatures(ClassInfo ci) {
        for (FieldInfo f : ci.fields()) {
            String hidden = exposedTypes(f.descriptor());
            if (isApi(f.accessFlags()) && hidden != null) {
                add(ci, "field " + f.name(), "the " + visibility(f.accessFlags()) + " field " + f.name()
                        + " of public " + kind(ci) + " " + ci.thisClass() + " has the package-visible type "
                        + hidden);
            }
        }
        for (MethodInfo m : ci.methods()) {
            String hidden = exposedTypes(m.descriptor());
            if (isApi(m.accessFlags()) && !m.isStaticInitializer() && hidden != null) {
                String what = m.isConstructor() ? " constructor" : " method " + m.name();
                add(ci, m.name() + m.descriptor(), "the signature of the " + visibility(m.accessFlags())
                        + what + " of public " + kind(ci) + " " + ci.thisClass()
                        + " uses the package-visible type " + hidden);
            }
        }
    }

    /** Package-visible superclasses and interfaces of a public class or interface. */
    private void packageVisibleSupertypes(ClassInfo ci) {
        if (ci.isInterface()) {
            for (String itf : ci.interfaces()) {
                ClassInfo sup = byName.get(itf);
                if (sup != null && !isPublic(sup.accessFlags())) {
                    add(ci, "interface " + itf, "public interface " + ci.thisClass()
                            + " extends the package-visible interface " + itf);
                }
            }
            return;
        }
        for (ClassInfo sup = byName.get(ci.superClass()); sup != null; sup = byName.get(sup.superClass())) {
            if (!isPublic(sup.accessFlags()) && declaresApi(sup)) {
                add(ci, "superclass", "package-visible class " + sup.thisClass() + ", extended by public class "
                        + ci.thisClass() + ", declares public or protected methods or fields");
            }
        }
        for (String itf : allInterfaces(ci)) {
            ClassInfo i = byName.get(itf);
            if (i != null && !isPublic(i.accessFlags()) && !i.fields().isEmpty()) {
                add(ci, "interface " + itf, "package-visible interface " + itf + ", implemented by public class "
                        + ci.thisClass() + ", declares fields");
            }
        }
    }

    private boolean declaresApi(ClassInfo ci) {
        return ci.fields().stream().anyMatch(f -> isApi(f.accessFlags()))
                || ci.methods().stream().anyMatch(m -> isApi(m.accessFlags()) && !m.isStaticInitializer());
    }

    /** The interfaces of a class, its superclasses in the package and their superinterfaces. */
    private Set<String> allInterfaces(ClassInfo ci) {
        Set<String> result = new HashSet<>();
        Deque<String> work = new ArrayDeque<>(ci.interfaces());
        for (ClassInfo sup = byName.get(ci.superClass()); sup != null; sup = byName.get(sup.superClass())) {
            work.addAll(sup.interfaces());
        }
        while (!work.isEmpty()) {
            String itf = work.removeFirst();
            ClassInfo i = byName.get(itf);
            if (result.add(itf) && i != null) {
                work.addAll(i.interfaces());
            }
        }
        return result;
    }

    /** The first package-visible class or interface of the package that a descriptor names. */
    private String exposedTypes(String descriptor) {
        int i = 0;
        while (i < descriptor.length()) {
            if (descriptor.charAt(i) == 'L') {
                int end = descriptor.indexOf(';', i);
                ClassInfo type = byName.get(descriptor.substring(i + 1, end));
                if (type != null && !isPublic(type.accessFlags())) {
                    return type.thisClass();
                }
                i = end + 1;
            } else {
                i++;
            }
        }
        return null;
    }

    private static boolean isPublic(int flags) {
        return (flags & ACC_PUBLIC) != 0;
    }

    /** Public or protected, and declared in the source (not synthetic or a bridge). */
    private static boolean isApi(int flags) {
        return (flags & (ACC_PUBLIC | ACC_PROTECTED)) != 0 && (flags & (ACC_SYNTHETIC | ACC_BRIDGE)) == 0;
    }

    private static String visibility(int flags) {
        return isPublic(flags) ? "public" : "protected";
    }

    private static String kind(ClassInfo ci) {
        return ci.isInterface() ? "interface" : "class";
    }

    private void add(ClassInfo ci, String context, String message) {
        violations.add(new Violation(ci.thisClass(), context, -1, message + SPEC, ci.sourceFile().orElse(null), -1));
    }
}
