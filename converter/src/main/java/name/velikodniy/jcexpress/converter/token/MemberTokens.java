package name.velikodniy.jcexpress.converter.token;

import name.velikodniy.jcexpress.converter.check.Violation;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Field, static method and constructor tokens of one class (JCVM 3.1 §4.3.7.3 - §4.3.7.5).
 */
final class MemberTokens {

    /** Largest static field, static method and instance field token (JCVM 3.1 Table 4-2). */
    static final int MAX_MEMBER_TOKEN = 255;
    /** declared_instance_size is a u1 item counting 16-bit cells (JCVM 3.1 §6.9.2.3). */
    static final int MAX_INSTANCE_CELLS = 255;

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_PROTECTED = 0x0004;

    private MemberTokens() {}

    static boolean isExternallyVisible(int accessFlags) {
        return (accessFlags & (ACC_PUBLIC | ACC_PROTECTED)) != 0;
    }

    /**
     * §4.3.7.4: externally visible static methods and constructors are numbered consecutively
     * from zero; package-visible and private ones get no token. {@code <clinit>} is never a
     * method of a CAP file (§6.10).
     */
    static List<TokenMap.MethodEntry> staticMethods(ClassInfo ci, List<Violation> violations) {
        List<TokenMap.MethodEntry> result = new ArrayList<>();
        for (MethodInfo mi : ci.methods()) {
            boolean statik = mi.isConstructor() || (mi.isStatic() && !mi.isStaticInitializer());
            if (statik && isExternallyVisible(mi.accessFlags())) {
                result.add(new TokenMap.MethodEntry(mi.name(), mi.descriptor(), result.size()));
            }
        }
        if (result.size() > MAX_MEMBER_TOKEN + 1) {
            violations.add(new Violation(ci.thisClass(), "static methods",
                    "more than 256 public or protected static methods and constructors (JCVM 3.1 §4.3.7.4)"));
        }
        return List.copyOf(result);
    }

    /**
     * §4.3.7.3: externally visible static fields are numbered consecutively from zero; fields of
     * compile-time constant value are not represented in a CAP file and get no token.
     */
    static List<TokenMap.FieldEntry> staticFields(ClassInfo ci, List<Violation> violations) {
        List<TokenMap.FieldEntry> result = new ArrayList<>();
        for (FieldInfo fi : ci.fields()) {
            if (fi.isStatic() && !fi.isCompileTimeConstant() && isExternallyVisible(fi.accessFlags())) {
                result.add(new TokenMap.FieldEntry(fi.name(), fi.descriptor(), result.size()));
            }
        }
        if (result.size() > MAX_MEMBER_TOKEN + 1) {
            violations.add(new Violation(ci.thisClass(), "static fields",
                    "more than 256 public or protected static fields (JCVM 3.1 §4.3.7.3)"));
        }
        return List.copyOf(result);
    }

    /**
     * §4.3.7.5: instance field tokens are consecutive from zero, the token after an {@code int}
     * field is skipped; public/protected fields come first (primitives, then references),
     * followed by package/private fields (references, then primitives). Within a group the
     * declaration order is kept.
     */
    static List<TokenMap.FieldEntry> instanceFields(ClassInfo ci, List<Violation> violations) {
        List<List<FieldInfo>> groups = List.of(new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
        for (FieldInfo fi : ci.fields()) {
            if (!fi.isStatic()) {
                boolean visible = isExternallyVisible(fi.accessFlags());
                boolean reference = isReference(fi.descriptor());
                groups.get(visible ? (reference ? 1 : 0) : (reference ? 2 : 3)).add(fi);
            }
        }
        List<TokenMap.FieldEntry> result = new ArrayList<>();
        int cell = 0;
        for (List<FieldInfo> group : groups) {
            for (FieldInfo fi : group) {
                result.add(new TokenMap.FieldEntry(fi.name(), fi.descriptor(), cell));
                cell += cells(fi.descriptor());
            }
        }
        if (cell > MAX_INSTANCE_CELLS) {
            violations.add(new Violation(ci.thisClass(), "instance fields", "instance fields need " + cell
                    + " 16-bit cells, at most 255 are supported (JCVM 3.1 §4.3.7.5, §6.9.2.3)"));
        }
        return List.copyOf(result);
    }

    /** Number of 16-bit cells of an instance field: two for {@code int}, one otherwise (§6.9.2.3). */
    static int cells(String descriptor) {
        return "I".equals(descriptor) ? 2 : 1;
    }

    static boolean isReference(String descriptor) {
        return descriptor.startsWith("L") || descriptor.startsWith("[");
    }
}
