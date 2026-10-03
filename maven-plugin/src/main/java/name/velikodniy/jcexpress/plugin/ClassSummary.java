package name.velikodniy.jcexpress.plugin;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * What the plugin needs to know about one class file: its name, supertypes, access flags, whether
 * it declares the static {@code install(byte[], short, byte)} method of an applet (JCVM 3.1
 * &sect;6.6), and which packages it references.
 *
 * @param name               internal name, e.g. {@code com/example/WalletApplet}
 * @param superName          internal name of the superclass, {@code null} for {@code java.lang.Object}
 * @param interfaces         internal names of the directly implemented or extended interfaces
 * @param accessFlags        class access flags of the class file
 * @param declaresInstall    {@code true} if the class declares {@code static void install(byte[], short, byte)}
 * @param sourceFile         the SourceFile attribute (e.g. {@code WalletApplet.java}), or {@code null}
 * @param referencedPackages packages (dot notation) of the classes this class refers to: constant
 *                           pool class entries and the types in member descriptors, sorted
 */
record ClassSummary(String name, String superName, List<String> interfaces, int accessFlags,
                    boolean declaresInstall, String sourceFile, Set<String> referencedPackages) {

    /** Descriptor of {@code install(byte[], short, byte)} returning void. */
    static final String INSTALL_DESCRIPTOR = "([BSB)V";

    /**
     * Creates a summary, copying the collections.
     */
    ClassSummary {
        interfaces = List.copyOf(interfaces);
        referencedPackages = Set.copyOf(referencedPackages);
    }

    /**
     * Reads a class file.
     *
     * @param bytes the class file bytes
     * @return the summary
     * @throws IllegalArgumentException if the bytes are not a valid class file
     */
    static ClassSummary parse(byte[] bytes) {
        ClassModel model = ClassFile.of().parse(bytes);
        return new ClassSummary(
                model.thisClass().asInternalName(),
                model.superclass().map(ClassEntry::asInternalName).orElse(null),
                model.interfaces().stream().map(ClassEntry::asInternalName).toList(),
                model.flags().flagsMask(),
                model.methods().stream().anyMatch(ClassSummary::isInstallMethod),
                model.findAttribute(Attributes.sourceFile())
                        .map(a -> a.sourceFile().stringValue()).orElse(null),
                referencedPackages(model));
    }

    private static boolean isInstallMethod(MethodModel method) {
        return "install".equals(method.methodName().stringValue())
                && INSTALL_DESCRIPTOR.equals(method.methodType().stringValue())
                && method.flags().has(AccessFlag.STATIC);
    }

    private static Set<String> referencedPackages(ClassModel model) {
        Set<String> packages = new TreeSet<>();
        for (PoolEntry entry : model.constantPool()) {
            if (entry instanceof ClassEntry c) {
                add(packages, c.asSymbol());
            } else if (entry instanceof NameAndTypeEntry nt) {
                addDescriptor(packages, nt.type().stringValue());
            }
        }
        for (FieldModel field : model.fields()) {
            addDescriptor(packages, field.fieldType().stringValue());
        }
        for (MethodModel method : model.methods()) {
            addDescriptor(packages, method.methodType().stringValue());
        }
        return packages;
    }

    private static void addDescriptor(Set<String> packages, String descriptor) {
        if (descriptor.startsWith("(")) {
            MethodTypeDesc type = MethodTypeDesc.ofDescriptor(descriptor);
            type.parameterList().forEach(p -> add(packages, p));
            add(packages, type.returnType());
        } else {
            add(packages, ClassDesc.ofDescriptor(descriptor));
        }
    }

    private static void add(Set<String> packages, ClassDesc type) {
        ClassDesc element = type;
        while (element.isArray()) {
            element = element.componentType();
        }
        if (element.isClassOrInterface()) {
            packages.add(element.packageName());
        }
    }

    /** @return the package in dot notation, empty for the default package */
    String packageName() {
        int slash = name.lastIndexOf('/');
        return slash < 0 ? "" : name.substring(0, slash).replace('/', '.');
    }

    /** @return the fully qualified class name in dot notation */
    String javaName() {
        return name.replace('/', '.');
    }

    /** @return {@code true} for interfaces (ACC_INTERFACE) */
    boolean isInterface() {
        return has(ClassFile.ACC_INTERFACE);
    }

    /** @return {@code true} for abstract classes and interfaces (ACC_ABSTRACT) */
    boolean isAbstract() {
        return has(ClassFile.ACC_ABSTRACT);
    }

    /** @return {@code true} for public classes and interfaces (ACC_PUBLIC) */
    boolean isPublic() {
        return has(ClassFile.ACC_PUBLIC);
    }

    /** @return {@code true} for compiler-generated classes such as {@code package-info} (ACC_SYNTHETIC) */
    boolean isSynthetic() {
        return has(ClassFile.ACC_SYNTHETIC);
    }

    private boolean has(int flag) {
        return (accessFlags & flag) != 0;
    }
}
