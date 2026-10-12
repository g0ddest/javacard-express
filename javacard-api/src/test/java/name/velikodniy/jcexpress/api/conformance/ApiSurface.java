package name.velikodniy.jcexpress.api.conformance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.AttributedElement;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.ConstantValueAttribute;
import java.lang.classfile.attribute.InnerClassInfo;
import java.lang.classfile.constantpool.ClassEntry;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The public API surface (public and protected types and members) of API packages found in a class directory or
 * jar: by default the Java Card packages ({@code javacard.*}, {@code javacardx.*}), or the packages under chosen
 * prefixes such as {@code org/globalplatform/}.
 *
 * <p>Class files are parsed with the JDK ClassFile API and never loaded, so two jars that define
 * the same class names (the stubs and a reference implementation) can be compared side by side.
 * Only facts that matter to code compiled against the API are kept: access modifiers, super types,
 * field types with their {@code ConstantValue} (javac inlines those into applets, JLS 13.1) and
 * method descriptors (applets link against name plus descriptor, JCVM 3.1 section 5.9).
 *
 * @param types API types keyed by internal name (for example {@code javacard/framework/APDU})
 */
record ApiSurface(Map<String, Type> types) {

    /** Path prefixes of the Java Card API packages, which {@link #read(Path)} reads. */
    static final List<String> JAVA_CARD_PACKAGES = List.of("javacard/", "javacardx/");
    /** Path prefix of the GlobalPlatform Card API package {@code org.globalplatform}. */
    static final List<String> GLOBALPLATFORM_PACKAGE = List.of("org/globalplatform/");

    /** Modifier bits that are part of a type's API contract. */
    static final int TYPE_FLAGS = ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED | ClassFile.ACC_STATIC
            | ClassFile.ACC_FINAL | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT;
    /** Modifier bits that are part of a field's API contract. */
    static final int FIELD_FLAGS = ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED | ClassFile.ACC_STATIC
            | ClassFile.ACC_FINAL;
    /** Modifier bits that are part of a method's API contract. */
    static final int METHOD_FLAGS = ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED | ClassFile.ACC_STATIC
            | ClassFile.ACC_FINAL | ClassFile.ACC_ABSTRACT;

    /**
     * One API type.
     *
     * @param name       internal name
     * @param flags      modifiers masked with {@link #TYPE_FLAGS}
     * @param superName  internal name of the superclass ({@code null} for interfaces' Object)
     * @param interfaces internal names of the directly implemented or extended interfaces
     * @param fields     public/protected fields keyed by name
     * @param methods    public/protected methods and constructors keyed by name plus descriptor
     */
    record Type(String name, int flags, String superName, Set<String> interfaces,
                Map<String, Field> fields, Map<String, Method> methods) {

        boolean isInterface() {
            return (flags & ClassFile.ACC_INTERFACE) != 0;
        }
    }

    /**
     * One public or protected field.
     *
     * @param name       field name
     * @param descriptor field descriptor
     * @param flags      modifiers masked with {@link #FIELD_FLAGS}
     * @param constant   value of the {@code ConstantValue} attribute, or {@code null}
     */
    record Field(String name, String descriptor, int flags, Object constant) {
    }

    /**
     * One public or protected method or constructor.
     *
     * @param name       method name ({@code <init>} for constructors)
     * @param descriptor method descriptor
     * @param flags      modifiers masked with {@link #METHOD_FLAGS}
     * @param exceptions internal names listed in the {@code Exceptions} attribute
     */
    record Method(String name, String descriptor, int flags, Set<String> exceptions) {

        /** Name plus parameter part of the descriptor, i.e. what Java overload resolution sees. */
        String parameterKey() {
            return name + descriptor.substring(0, descriptor.indexOf(')') + 1);
        }

        String returnDescriptor() {
            return descriptor.substring(descriptor.indexOf(')') + 1);
        }
    }

    /**
     * Reads the API surface of all Java Card packages in a class directory or jar file.
     *
     * @param location class directory or jar
     * @return the surface, only public types are included
     */
    static ApiSurface read(Path location) {
        return read(location, JAVA_CARD_PACKAGES);
    }

    /**
     * Reads the API surface of the packages under the given path prefixes in a class directory or jar file.
     *
     * @param location        class directory or jar
     * @param packagePrefixes path prefixes of the class files to read, for example {@code org/globalplatform/}
     * @return the surface, only public types are included
     */
    static ApiSurface read(Path location, List<String> packagePrefixes) {
        Map<String, Type> types = new TreeMap<>();
        for (ClassModel model : classFiles(location, packagePrefixes)) {
            int flags = typeFlags(model);
            if ((flags & ClassFile.ACC_PUBLIC) != 0) {
                types.put(model.thisClass().asInternalName(), toType(model, flags));
            }
        }
        return new ApiSurface(types);
    }

    /**
     * Lists the deprecated elements of the public API under the given path prefixes: public types and their public
     * or protected members that carry the {@code Deprecated} attribute (JVMS 4.7.15), which javac writes for the
     * {@code @deprecated} documentation tag and for the annotation, or the {@code java.lang.Deprecated}
     * annotation.
     *
     * @param location        class directory or jar
     * @param packagePrefixes path prefixes of the class files to read
     * @return internal type names, {@code type.field} and {@code type.method(descriptor)}, sorted
     */
    static Set<String> deprecated(Path location, List<String> packagePrefixes) {
        Set<String> names = new TreeSet<>();
        for (ClassModel model : classFiles(location, packagePrefixes)) {
            if ((typeFlags(model) & ClassFile.ACC_PUBLIC) == 0) {
                continue;
            }
            String type = model.thisClass().asInternalName();
            if (isDeprecated(model)) {
                names.add(type);
            }
            model.fields().stream().filter(field -> isApiMember(field.flags().flagsMask()))
                    .filter(ApiSurface::isDeprecated)
                    .forEach(field -> names.add(type + "." + field.fieldName().stringValue()));
            model.methods().stream().filter(method -> isApiMember(method.flags().flagsMask()))
                    .filter(ApiSurface::isDeprecated)
                    .forEach(method -> names.add(type + "." + method.methodName().stringValue()
                            + method.methodType().stringValue()));
        }
        return names;
    }

    private static boolean isDeprecated(AttributedElement element) {
        boolean annotated = element.findAttribute(Attributes.runtimeVisibleAnnotations()).stream()
                .flatMap(attribute -> attribute.annotations().stream())
                .anyMatch(annotation -> annotation.className().stringValue().equals("Ljava/lang/Deprecated;"));
        return annotated || element.findAttribute(Attributes.deprecated()).isPresent();
    }

    private static List<ClassModel> classFiles(Path location, List<String> packagePrefixes) {
        try {
            if (Files.isDirectory(location)) {
                return classFilesBelow(location, packagePrefixes);
            }
            try (FileSystem jar = FileSystems.newFileSystem(location)) {
                return classFilesBelow(jar.getPath("/"), packagePrefixes);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read class files from " + location, e);
        }
    }

    private static List<ClassModel> classFilesBelow(Path root, List<String> packagePrefixes) throws IOException {
        List<ClassModel> models = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(candidate -> isApiClassFile(root.relativize(candidate), packagePrefixes))
                    .sorted().toList()) {
                models.add(ClassFile.of().parse(Files.readAllBytes(file)));
            }
        }
        return models;
    }

    private static boolean isApiClassFile(Path relative, List<String> packagePrefixes) {
        String path = relative.toString().replace('\\', '/');
        return path.endsWith(".class") && packagePrefixes.stream().anyMatch(path::startsWith);
    }

    /** For member types the InnerClasses entry carries the declared modifiers (JVMS 4.7.6). */
    private static int typeFlags(ClassModel model) {
        String self = model.thisClass().asInternalName();
        return model.findAttribute(Attributes.innerClasses()).stream()
                .flatMap(attribute -> attribute.classes().stream())
                .filter(info -> info.innerClass().asInternalName().equals(self))
                .mapToInt(InnerClassInfo::flagsMask)
                .findFirst()
                .orElse(model.flags().flagsMask()) & TYPE_FLAGS;
    }

    private static Type toType(ClassModel model, int flags) {
        Map<String, Field> fields = new TreeMap<>();
        for (FieldModel field : model.fields()) {
            if (isApiMember(field.flags().flagsMask())) {
                fields.put(field.fieldName().stringValue(), toField(field));
            }
        }
        Map<String, Method> methods = new TreeMap<>();
        for (MethodModel method : model.methods()) {
            if (isApiMember(method.flags().flagsMask()) && !"<clinit>".equals(method.methodName().stringValue())) {
                Method m = toMethod(method);
                methods.put(m.name() + m.descriptor(), m);
            }
        }
        String superName = model.superclass().map(ClassEntry::asInternalName).orElse(null);
        Set<String> interfaces = model.interfaces().stream().map(ClassEntry::asInternalName)
                .collect(Collectors.toCollection(TreeSet::new));
        return new Type(model.thisClass().asInternalName(), flags, superName, interfaces, fields, methods);
    }

    private static boolean isApiMember(int flags) {
        boolean visible = (flags & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) != 0;
        boolean compilerGenerated = (flags & (ClassFile.ACC_SYNTHETIC | ClassFile.ACC_BRIDGE)) != 0;
        return visible && !compilerGenerated;
    }

    private static Field toField(FieldModel field) {
        Object constant = field.findAttribute(Attributes.constantValue())
                .map(ConstantValueAttribute::constant)
                .map(entry -> (Object) entry.constantValue())
                .orElse(null);
        return new Field(field.fieldName().stringValue(), field.fieldType().stringValue(),
                field.flags().flagsMask() & FIELD_FLAGS, constant);
    }

    private static Method toMethod(MethodModel method) {
        Set<String> exceptions = method.findAttribute(Attributes.exceptions()).stream()
                .flatMap(attribute -> attribute.exceptions().stream())
                .map(ClassEntry::asInternalName)
                .collect(Collectors.toCollection(TreeSet::new));
        return new Method(method.methodName().stringValue(), method.methodType().stringValue(),
                method.flags().flagsMask() & METHOD_FLAGS, exceptions);
    }

    /**
     * Returns the types of this surface that live in one of the given packages.
     *
     * @param packages internal package names such as {@code javacard/framework}
     * @return matching types keyed by internal name
     */
    Map<String, Type> inPackages(List<String> packages) {
        Map<String, Type> result = new TreeMap<>();
        types.forEach((name, type) -> {
            if (packages.contains(name.substring(0, name.lastIndexOf('/')))) {
                result.put(name, type);
            }
        });
        return result;
    }
}
