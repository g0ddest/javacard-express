package name.velikodniy.jcexpress.container;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.StackMapFrameInfo;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.InvokeDynamicEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects the class files an applet needs in the simulator: the applet class and, transitively, every class it
 * references that the simulator does not provide itself (superclasses, interfaces, helper classes, nested and
 * anonymous classes at any depth).
 *
 * <p>References are read from the class file structures the JVM resolves when it loads, verifies and runs a class
 * (see {@link #referencedClasses(byte[])}), so classes that only appear in descriptors, which the verifier may load,
 * are included too, while reflection-only metadata (enclosing class of a nested applet) is not. Classes of the Java
 * runtime and classes whose class file is not visible to the applet's class loader are skipped.</p>
 */
final class AppletClasses {

    /** Packages the simulator provides: the JDK and jCardSim with its {@code javacard.*} API implementation. */
    private static final List<String> PROVIDED_PACKAGES = List.of(
            "java.", "javax.", "jdk.", "sun.", "com.sun.", "javacard.", "javacardx.", "com.licel.");

    private AppletClasses() {
    }

    /**
     * Collects the class files of an applet.
     *
     * @param appletClass the applet class
     * @return class files by binary name, the applet class first
     * @throws IOException if the class file of the applet class itself cannot be read
     */
    static Map<String, byte[]> of(Class<?> appletClass) throws IOException {
        ClassLoader loader = appletClass.getClassLoader() != null
                ? appletClass.getClassLoader() : ClassLoader.getSystemClassLoader();
        Map<String, byte[]> classes = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(List.of(appletClass.getName()));
        while (!pending.isEmpty()) {
            String name = pending.poll();
            if (!seen.add(name) || isProvidedBySimulator(name)) {
                continue;
            }
            byte[] classFile = read(loader, name);
            if (classFile == null) {
                if (classes.isEmpty()) {
                    throw new IOException("Class file of " + name + " not found by " + loader);
                }
                continue;
            }
            classes.put(name, classFile);
            pending.addAll(referencedClasses(classFile));
        }
        return classes;
    }

    /**
     * Tells whether the simulator provides a class itself (so it must not be shipped).
     *
     * @param binaryName binary class name
     * @return whether the class belongs to the JDK or jCardSim
     */
    static boolean isProvidedBySimulator(String binaryName) {
        return PROVIDED_PACKAGES.stream().anyMatch(binaryName::startsWith);
    }

    /**
     * Lists the binary names of the classes a class file needs to be loaded, verified and run.
     *
     * <p>Only references the JVM resolves are followed: the superclass and interfaces (JVMS 5.3.5), field and
     * method descriptors (JVMS 4.3), the symbolic references of the instructions (JVMS 5.4.3: member owners and
     * types, {@code new}/{@code checkcast}/{@code instanceof}/array creation, {@code ldc} and
     * {@code invokedynamic} constants), exception handler catch types and the class types of the
     * {@code StackMapTable} (JVMS 4.7.4, used by the verifier). The {@code InnerClasses}, {@code EnclosingMethod},
     * {@code NestHost}/{@code NestMembers} and {@code Signature} attributes are deliberately ignored: they are
     * reflection metadata, so an applet declared inside a test class does not drag in the test class and, through
     * it, the whole test class path.</p>
     *
     * @param classFile the class file
     * @return referenced class names (including the class itself)
     */
    static Set<String> referencedClasses(byte[] classFile) {
        ClassModel model = ClassFile.of().parse(classFile);
        Set<String> names = new HashSet<>();
        addClass(names, model.thisClass());
        model.superclass().ifPresent(c -> addClass(names, c));
        model.interfaces().forEach(c -> addClass(names, c));
        model.fields().forEach(f -> addDescriptor(names, f.fieldType().stringValue()));
        for (MethodModel method : model.methods()) {
            addDescriptor(names, method.methodType().stringValue());
            method.code().ifPresent(code -> addCodeReferences(names, code));
        }
        return names;
    }

    private static void addCodeReferences(Set<String> names, CodeModel code) {
        for (CodeElement element : code) {
            switch (element) {
                case FieldInstruction i -> addMember(names, i.owner(), i.type());
                case InvokeInstruction i -> addMember(names, i.owner(), i.type());
                case InvokeDynamicInstruction i -> addInvokeDynamic(names, i.invokedynamic());
                case TypeCheckInstruction i -> addClass(names, i.type());
                case NewObjectInstruction i -> addClass(names, i.className());
                case NewReferenceArrayInstruction i -> addClass(names, i.componentType());
                case NewMultiArrayInstruction i -> addClass(names, i.arrayType());
                case ConstantInstruction.LoadConstantInstruction i -> addConstant(names, i.constantEntry());
                case ExceptionCatch c -> c.catchType().ifPresent(t -> addClass(names, t));
                default -> { }
            }
        }
        code.findAttribute(Attributes.stackMapTable()).ifPresent(table -> table.entries().forEach(frame -> {
            frame.locals().forEach(type -> addVerificationType(names, type));
            frame.stack().forEach(type -> addVerificationType(names, type));
        }));
    }

    private static void addVerificationType(Set<String> names, StackMapFrameInfo.VerificationTypeInfo type) {
        if (type instanceof StackMapFrameInfo.ObjectVerificationTypeInfo object) {
            addClass(names, object.className());
        }
    }

    private static void addMember(Set<String> names, ClassEntry owner, Utf8Entry descriptor) {
        addClass(names, owner);
        addDescriptor(names, descriptor.stringValue());
    }

    private static void addInvokeDynamic(Set<String> names, InvokeDynamicEntry entry) {
        addDescriptor(names, entry.type().stringValue());
        addConstant(names, entry.bootstrap().bootstrapMethod());
        entry.bootstrap().arguments().forEach(argument -> addConstant(names, argument));
    }

    private static void addConstant(Set<String> names, LoadableConstantEntry constant) {
        switch (constant) {
            case ClassEntry c -> addClass(names, c);
            case MethodTypeEntry t -> addDescriptor(names, t.descriptor().stringValue());
            case MethodHandleEntry h -> addMember(names, h.reference().owner(), h.reference().type());
            case ConstantDynamicEntry d -> {
                addDescriptor(names, d.type().stringValue());
                addConstant(names, d.bootstrap().bootstrapMethod());
                d.bootstrap().arguments().forEach(argument -> addConstant(names, argument));
            }
            default -> { }
        }
    }

    private static void addClass(Set<String> names, ClassEntry entry) {
        addClass(names, entry.asInternalName());
    }

    private static void addClass(Set<String> names, String internalName) {
        if (internalName.startsWith("[")) {
            addDescriptor(names, internalName);
        } else {
            names.add(internalName.replace('/', '.'));
        }
    }

    private static void addDescriptor(Set<String> names, String descriptor) {
        try {
            if (descriptor.startsWith("(")) {
                MethodTypeDesc type = MethodTypeDesc.ofDescriptor(descriptor);
                addType(names, type.returnType());
                type.parameterList().forEach(p -> addType(names, p));
            } else {
                addType(names, ClassDesc.ofDescriptor(descriptor));
            }
        } catch (IllegalArgumentException e) {
            // not a valid descriptor: nothing to ship
        }
    }

    private static void addType(Set<String> names, ClassDesc type) {
        ClassDesc element = type;
        while (element.isArray()) {
            element = element.componentType();
        }
        if (element.isClassOrInterface()) {
            String descriptor = element.descriptorString();
            names.add(descriptor.substring(1, descriptor.length() - 1).replace('/', '.'));
        }
    }

    private static byte[] read(ClassLoader loader, String binaryName) throws IOException {
        URL resource = loader.getResource(binaryName.replace('.', '/') + ".class");
        if (resource == null || "jrt".equals(resource.getProtocol())) {
            return null; // not visible, or part of the Java runtime (which the simulator has itself)
        }
        try (InputStream in = resource.openStream()) {
            return in.readAllBytes();
        }
    }
}
