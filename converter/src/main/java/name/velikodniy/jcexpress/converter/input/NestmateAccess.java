package name.velikodniy.jcexpress.converter.input;

import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps the nestmate access of class files compiled by javac 11 or later to package access.
 *
 * <p>Since JEP 181 (Java 11) a nested class and its enclosing class read and write each other's
 * private fields and call each other's private methods and constructors directly
 * ({@code getfield}, {@code invokevirtual}, {@code invokespecial} naming the other class), where
 * javac 10 and earlier generated package-visible {@code access$NNN} bridge methods. The Java Card
 * virtual machine has no nests: a private member is accessible only inside its own class, and the
 * off-card verifier rejects any other access (JCVM 3.1 §2.2.1.1.6 supports the package access
 * control of the Java language). Every member of the package's classes that another class of the
 * package accesses is therefore made package-visible (its {@code ACC_PRIVATE} flag is cleared),
 * which keeps the same set of possible callers as the bridge methods of javac 10 did.
 *
 * <p>An instance method stays private if a subclass in the package declares a method with the
 * same name and descriptor: as a package-visible method it would be overridden by that method,
 * while a private method is never overridden. The subset check reports the remaining nestmate
 * access to such a method.
 */
public final class NestmateAccess {

    private static final int ACC_PRIVATE = 0x0002;

    private NestmateAccess() {}

    /**
     * Returns the classes with every nestmate-accessed private member made package-visible.
     * Classes without a parsed class file are returned unchanged.
     *
     * @param classes all classes of the package
     * @return the classes in the same order
     */
    public static List<ClassInfo> widen(List<ClassInfo> classes) {
        Map<String, ClassInfo> byName = new HashMap<>();
        classes.forEach(ci -> byName.put(ci.thisClass(), ci));
        Map<String, Set<String>> accessed = accessedFromOtherClasses(classes, byName);
        if (accessed.isEmpty()) {
            return classes;
        }
        List<ClassInfo> result = new ArrayList<>();
        for (ClassInfo ci : classes) {
            Set<String> members = accessed.getOrDefault(ci.thisClass(), Set.of());
            result.add(members.isEmpty() ? ci : widen(ci, members, classes));
        }
        return List.copyOf(result);
    }

    /**
     * Returns the subclass in the package that declares an instance method with the given name
     * and descriptor below {@code owner}, or {@code null}.
     *
     * @param owner      internal name of the class declaring the private method
     * @param name       method name
     * @param descriptor method descriptor
     * @param classes    all classes of the package
     * @return the internal name of the overriding subclass, or {@code null}
     */
    public static String overridingSubclass(String owner, String name, String descriptor,
                                            List<ClassInfo> classes) {
        Map<String, ClassInfo> byName = new HashMap<>();
        classes.forEach(ci -> byName.put(ci.thisClass(), ci));
        for (ClassInfo ci : classes) {
            if (!ci.thisClass().equals(owner) && isSubclass(ci, owner, byName)
                    && ci.methods().stream().anyMatch(m -> !m.isStatic() && m.name().equals(name)
                    && m.descriptor().equals(descriptor))) {
                return ci.thisClass();
            }
        }
        return null;
    }

    /** owner class -> "name descriptor" of its private members accessed by other package classes. */
    private static Map<String, Set<String>> accessedFromOtherClasses(List<ClassInfo> classes,
                                                                     Map<String, ClassInfo> byName) {
        Map<String, Set<String>> accessed = new HashMap<>();
        for (ClassInfo ci : classes) {
            if (ci.model() == null) {
                continue;
            }
            for (MethodModel m : ci.model().methods()) {
                m.code().ifPresent(code -> code.forEach(e -> {
                    String[] ref = memberRef(e);
                    if (ref != null && !ref[0].equals(ci.thisClass())
                            && isPrivateMember(byName.get(ref[0]), ref[1], ref[2])) {
                        accessed.computeIfAbsent(ref[0], k -> new HashSet<>()).add(ref[1] + " " + ref[2]);
                    }
                }));
            }
        }
        return accessed;
    }

    /** {owner, name, descriptor} of a field or method reference, or {@code null}. */
    private static String[] memberRef(CodeElement e) {
        return switch (e) {
            case FieldInstruction f -> new String[]{f.owner().asInternalName(), f.name().stringValue(),
                    f.typeSymbol().descriptorString()};
            case InvokeInstruction i -> new String[]{i.owner().asInternalName(), i.name().stringValue(),
                    i.typeSymbol().descriptorString()};
            default -> null;
        };
    }

    private static boolean isPrivateMember(ClassInfo owner, String name, String descriptor) {
        if (owner == null) {
            return false;
        }
        boolean isMethod = descriptor.startsWith("(");
        return isMethod
                ? owner.methods().stream().anyMatch(m -> m.isPrivate() && m.name().equals(name)
                        && m.descriptor().equals(descriptor))
                : owner.fields().stream().anyMatch(f -> (f.accessFlags() & ACC_PRIVATE) != 0
                        && f.name().equals(name) && f.descriptor().equals(descriptor));
    }

    private static ClassInfo widen(ClassInfo ci, Set<String> members, List<ClassInfo> classes) {
        List<MethodInfo> methods = ci.methods().stream()
                .map(m -> members.contains(m.name() + " " + m.descriptor()) && keepsSemantics(ci, m, classes)
                        ? withoutPrivate(m) : m)
                .toList();
        List<FieldInfo> fields = ci.fields().stream()
                .map(f -> members.contains(f.name() + " " + f.descriptor())
                        ? new FieldInfo(f.name(), f.descriptor(), f.accessFlags() & ~ACC_PRIVATE, f.constantValue())
                        : f)
                .toList();
        return new ClassInfo(ci.thisClass(), ci.superClass(), ci.interfaces(), ci.accessFlags(), methods,
                fields, ci.model());
    }

    /** A package-visible instance method must not become overridable by a subclass method. */
    private static boolean keepsSemantics(ClassInfo ci, MethodInfo m, List<ClassInfo> classes) {
        return m.isStatic() || m.isConstructor()
                || overridingSubclass(ci.thisClass(), m.name(), m.descriptor(), classes) == null;
    }

    private static MethodInfo withoutPrivate(MethodInfo m) {
        return new MethodInfo(m.name(), m.descriptor(), m.accessFlags() & ~ACC_PRIVATE, m.maxStack(),
                m.maxLocals(), m.bytecode(), m.exceptionHandlers());
    }

    private static boolean isSubclass(ClassInfo ci, String ancestor, Map<String, ClassInfo> byName) {
        Set<String> seen = new HashSet<>();
        String s = ci.superClass();
        while (s != null && seen.add(s)) {
            if (s.equals(ancestor)) {
                return true;
            }
            ClassInfo next = byName.get(s);
            s = next == null ? null : next.superClass();
        }
        return false;
    }
}
