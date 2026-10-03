package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;
import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SequencedSet;

/**
 * The type hierarchy seen by the export file of a package: the package's own classes and
 * interfaces plus the classes of imported packages as described by their export files
 * (JCVM 3.1 §5.4: "all public superclasses or superinterfaces are listed").
 *
 * <p>An imported class is always public (export files only describe public types) and its
 * export entry already lists all of its public superclasses, superinterfaces and inherited
 * virtual methods (§5.7), so the hierarchy is never followed into other packages.
 */
final class ExportHierarchy {

    static final String OBJECT = "java/lang/Object";
    static final String SHAREABLE = "javacard/framework/Shareable";
    static final String REMOTE = "java/rmi/Remote";

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_PRIVATE = 0x0002;
    private static final int ACC_PROTECTED = 0x0004;
    private static final int ACC_STATIC = 0x0008;

    private final Map<String, ClassInfo> own = new LinkedHashMap<>();
    private final Map<String, ExportFile> imports = new HashMap<>();

    /**
     * @param classes classes and interfaces of the package being exported
     * @param imports imported packages (their export files describe external types)
     */
    ExportHierarchy(List<ClassInfo> classes, List<ImportedPackage> imports) {
        for (ClassInfo ci : classes) own.put(ci.thisClass(), ci);
        for (ImportedPackage imp : imports) {
            this.imports.putIfAbsent(imp.exportFile().packageName(), imp.exportFile());
        }
    }

    /** Returns the own class or interface with this internal name, if any. */
    Optional<ClassInfo> own(String name) {
        return Optional.ofNullable(own.get(name));
    }

    /** Returns {@code true} if the type is public: own types by their flags, imported types always. */
    boolean isPublic(String name) {
        ClassInfo ci = own.get(name);
        return ci == null || (ci.accessFlags() & ACC_PUBLIC) != 0;
    }

    /**
     * Returns the export entry of an imported class or interface.
     *
     * @throws IllegalStateException if no imported export file describes it
     */
    ExportFile.ClassExport external(String name) {
        int slash = name.lastIndexOf('/');
        ExportFile ef = imports.get(slash < 0 ? "" : name.substring(0, slash));
        if (ef != null) {
            for (ExportFile.ClassExport ce : ef.classes()) {
                if (ce.name().equals(name) || ce.name().equals(name.substring(slash + 1))) return ce;
            }
        }
        throw new IllegalStateException(name + " is neither a class of this package nor exported by an"
                + " imported package; its export file is needed to describe the hierarchy (JCVM 3.1 §5.4)");
    }

    /**
     * Public superclasses of a class, root first (JCVM 3.1 §5.7 supers[]: every public superclass,
     * no package-visible ones).
     */
    List<String> publicSuperclasses(ClassInfo ci) {
        List<String> ownChain = new ArrayList<>();
        List<String> root = List.of();
        for (String s = ci.superClass(); s != null; ) {
            ClassInfo sup = own.get(s);
            if (sup == null) {
                root = new ArrayList<>(external(s).supers());
                root.add(s);
                break;
            }
            if ((sup.accessFlags() & ACC_PUBLIC) != 0) ownChain.addFirst(s);
            s = sup.superClass();
        }
        List<String> result = new ArrayList<>(root);
        result.addAll(ownChain);
        return result;
    }

    /**
     * Every interface implemented by a class (including through its superclasses) or extended
     * by an interface, directly or indirectly, supertypes first; package-visible interfaces of
     * this package are included.
     */
    SequencedSet<String> allInterfaces(ClassInfo ci) {
        SequencedSet<String> result = new LinkedHashSet<>();
        if (!ci.isInterface()) {
            addSuperclassInterfaces(ci.superClass(), result);
        }
        for (String i : ci.interfaces()) addInterface(i, result);
        return result;
    }

    private void addSuperclassInterfaces(String superName, SequencedSet<String> result) {
        if (superName == null) return;
        ClassInfo sup = own.get(superName);
        if (sup == null) {
            result.addAll(external(superName).interfaces());
            return;
        }
        addSuperclassInterfaces(sup.superClass(), result);
        for (String i : sup.interfaces()) addInterface(i, result);
    }

    private void addInterface(String name, SequencedSet<String> result) {
        if (result.contains(name)) return;
        ClassInfo ci = own.get(name);
        if (ci != null) {
            for (String s : ci.interfaces()) addInterface(s, result);
        } else {
            result.addAll(external(name).interfaces());
        }
        result.add(name);
    }

    /** Returns {@code true} if the type is {@code target} or implements or extends it. */
    boolean isSubtypeOf(ClassInfo ci, String target) {
        return ci.thisClass().equals(target) || allInterfaces(ci).contains(target);
    }

    /**
     * Returns the access flags of the most specific declaration of a public or protected
     * instance method visible in a class (declared by it or inherited from a superclass).
     */
    OptionalInt instanceMethodFlags(ClassInfo ci, String name, String descriptor) {
        for (String c = ci.thisClass(); c != null; ) {
            ClassInfo cls = own.get(c);
            if (cls == null) {
                return externalMethodFlags(external(c), name, descriptor);
            }
            Optional<MethodInfo> m = declared(cls, name, descriptor);
            if (m.isPresent()) {
                int flags = m.get().accessFlags();
                return (flags & (ACC_PUBLIC | ACC_PROTECTED)) != 0 ? OptionalInt.of(flags) : OptionalInt.empty();
            }
            c = cls.superClass();
        }
        return OptionalInt.empty();
    }

    /**
     * Returns the access flags of an interface method declared by an interface or one of its
     * superinterfaces (JCVM 3.1 §5.7 methods[]).
     */
    OptionalInt interfaceMethodFlags(ClassInfo iface, String name, String descriptor) {
        Optional<MethodInfo> m = declared(iface, name, descriptor);
        if (m.isPresent()) return OptionalInt.of(m.get().accessFlags());
        for (String s : allInterfaces(iface)) {
            ClassInfo sup = own.get(s);
            OptionalInt flags = sup != null
                    ? declared(sup, name, descriptor).stream().mapToInt(MethodInfo::accessFlags).findFirst()
                    : externalMethodFlags(external(s), name, descriptor);
            if (flags.isPresent()) return flags;
        }
        return OptionalInt.empty();
    }

    /**
     * Lists the public and protected instance methods of a class (declared or inherited), or
     * the methods of an interface and its superinterfaces, as name/descriptor keys in
     * discovery order.
     */
    SequencedSet<String> visibleInstanceMethods(ClassInfo ci) {
        SequencedSet<String> keys = new LinkedHashSet<>();
        List<ClassInfo> chain = new ArrayList<>();
        String externalRoot = null;
        if (ci.isInterface()) {
            chain.add(ci);
            for (String s : allInterfaces(ci)) {
                if (own.containsKey(s)) chain.add(own.get(s));
                else addExternalInstanceMethods(external(s), keys);
            }
        } else {
            for (String c = ci.thisClass(); c != null; ) {
                ClassInfo cls = own.get(c);
                if (cls == null) {
                    externalRoot = c;
                    break;
                }
                chain.add(cls);
                c = cls.superClass();
            }
        }
        if (externalRoot != null) addExternalInstanceMethods(external(externalRoot), keys);
        for (ClassInfo cls : chain.reversed()) {
            for (MethodInfo m : cls.methods()) {
                if (isInstanceMethod(m)) keys.add(m.name() + m.descriptor());
            }
        }
        return keys;
    }

    private static void addExternalInstanceMethods(ExportFile.ClassExport ce, SequencedSet<String> keys) {
        for (ExportFile.MethodExport m : ce.methods()) {
            if (!m.isStaticOrConstructor()) keys.add(m.name() + m.descriptor());
        }
    }

    private static boolean isInstanceMethod(MethodInfo m) {
        return (m.accessFlags() & (ACC_STATIC | ACC_PRIVATE)) == 0 && !m.name().startsWith("<");
    }

    private static Optional<MethodInfo> declared(ClassInfo ci, String name, String descriptor) {
        return ci.methods().stream()
                .filter(ExportHierarchy::isInstanceMethod)
                .filter(m -> m.name().equals(name) && m.descriptor().equals(descriptor))
                .findFirst();
    }

    private static OptionalInt externalMethodFlags(ExportFile.ClassExport ce, String name, String descriptor) {
        return ce.methods().stream()
                .filter(m -> !m.isStaticOrConstructor())
                .filter(m -> m.name().equals(name) && m.descriptor().equals(descriptor))
                .mapToInt(ExportFile.MethodExport::accessFlags)
                .findFirst();
    }
}
