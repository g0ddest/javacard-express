package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFile.ClassExport;
import name.velikodniy.jcexpress.converter.token.ExportFile.FieldExport;
import name.velikodniy.jcexpress.converter.token.ExportFile.MethodExport;
import name.velikodniy.jcexpress.converter.token.TokenMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.OptionalInt;
import java.util.SequencedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the export file model of a converted package (JCVM 3.1 Chapter 5) from its class files
 * and the tokens assigned to them.
 *
 * <ul>
 *   <li>Classes (§5.5, §5.6.1): a library package exports every public class and interface, an
 *       applet package only its public shareable interfaces. Package-visible types are never
 *       exported (§4.3.7.2).</li>
 *   <li>Class entries (§5.7): ACC_PUBLIC, ACC_FINAL, ACC_INTERFACE, ACC_ABSTRACT from the class
 *       file, ACC_SHAREABLE for types that are or implement/extend
 *       {@code javacard.framework.Shareable}, ACC_REMOTE for types that are or implement/extend
 *       {@code java.rmi.Remote} (§2.2.6.1); every public superclass (an interface lists only
 *       {@code java.lang.Object}) and every public superinterface, including those of
 *       superclasses.</li>
 *   <li>Fields (§5.8): every public or protected field declared by the class, instance and
 *       static; compile-time constants have token 0xFF and a ConstantValue attribute
 *       (§5.10.1).</li>
 *   <li>Methods (§5.9): public and protected static methods and constructors declared by the
 *       class (static method tokens, constructors without ACC_STATIC), and every public or
 *       protected instance method declared by the class or inherited from its superclasses
 *       (virtual method tokens); for an interface, the methods of the interface and its
 *       superinterfaces (interface method tokens).</li>
 *   <li>Types named in exported descriptors must be public (§5.8, §5.9).</li>
 * </ul>
 * Problems are collected and reported together in one {@link ConverterException}.
 */
public final class ExportModelBuilder {

    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_PROTECTED = 0x0004;
    private static final int ACC_STATIC = 0x0008;
    /** Class flags taken from the class file: PUBLIC, FINAL, INTERFACE, ABSTRACT (§5.7 Table 5-3). */
    private static final int CLASS_FILE_FLAGS = 0x0611;
    /** Field flags: PUBLIC, PROTECTED, STATIC, FINAL (§5.8 Table 5-4). */
    private static final int FIELD_FLAGS = 0x001D;
    /** Method flags: PUBLIC, PROTECTED, STATIC, FINAL, ABSTRACT (§5.9 Table 5-5). */
    private static final int METHOD_FLAGS = 0x041D;
    private static final int FORMAT_WITH_REFERENCED_PACKAGES = 3;
    private static final int MAX_CLASSES = 255;
    private static final int MAX_CLASS_TOKEN = 254;
    private static final Pattern CLASS_IN_DESCRIPTOR = Pattern.compile("L([^;]+);");

    private final ExportInput in;
    private final ExportHierarchy hierarchy;
    private final List<String> problems = new ArrayList<>();

    private ExportModelBuilder(ExportInput in) {
        this.in = in;
        this.hierarchy = new ExportHierarchy(in.classes(), in.imports());
    }

    /**
     * Builds the export file model.
     *
     * @param input the converted package
     * @return the export file content, in the export file format of the target version
     * @throws ConverterException listing every reason why the export file cannot be generated
     */
    public static ExportFile build(ExportInput input) throws ConverterException {
        ExportModelBuilder builder = new ExportModelBuilder(input);
        List<ClassExport> classes;
        try {
            classes = builder.classes();
        } catch (IllegalStateException e) {
            builder.problems.add(e.getMessage());
            classes = List.of();
        }
        int formatMinor = input.javaCardVersion().exportFormatMinor();
        List<ExportFile.PackageReference> referenced = formatMinor >= FORMAT_WITH_REFERENCED_PACKAGES
                ? builder.referencedPackages(classes) : List.of();
        if (!builder.problems.isEmpty()) {
            throw new ConverterException("Cannot generate the export file of package "
                    + input.packageName().replace('/', '.') + ":\n  - " + String.join("\n  - ", builder.problems));
        }
        return new ExportFile(input.packageName(), input.aid(), input.majorVersion(), input.minorVersion(),
                classes, input.library() ? ExportFile.ACC_LIBRARY : 0,
                input.javaCardVersion().exportFormatMajor(), formatMinor, referenced);
    }

    /**
     * §5.5 referenced_packages (format 2.3): every other package whose classes are subclassed,
     * implemented or extended, or used in descriptors, in encounter order, with the AID and
     * version of the export file used for linking.
     */
    private List<ExportFile.PackageReference> referencedPackages(List<ClassExport> classes) {
        SequencedSet<String> names = new LinkedHashSet<>();
        for (ClassExport c : classes) {
            c.supers().forEach(s -> names.add(packageOf(s)));
            c.interfaces().forEach(i -> names.add(packageOf(i)));
            c.fields().forEach(f -> addDescriptorPackages(f.descriptor(), names));
            c.methods().forEach(m -> addDescriptorPackages(m.descriptor(), names));
        }
        names.remove(in.packageName());
        List<ExportFile.PackageReference> result = new ArrayList<>();
        for (String name : names) {
            in.imports().stream().map(ImportedPackage::exportFile).filter(ef -> ef.packageName().equals(name))
                    .findFirst()
                    .ifPresentOrElse(ef -> result.add(new ExportFile.PackageReference(name, ef.aid(),
                                    ef.majorVersion(), ef.minorVersion())),
                            () -> problems.add("the export file references package " + name.replace('/', '.')
                                    + ", but no export file of that package is known (JCVM 3.1 §5.5)"));
        }
        return result;
    }

    private static void addDescriptorPackages(String descriptor, SequencedSet<String> names) {
        Matcher m = CLASS_IN_DESCRIPTOR.matcher(descriptor);
        while (m.find()) names.add(packageOf(m.group(1)));
    }

    private static String packageOf(String className) {
        int slash = className.lastIndexOf('/');
        return slash < 0 ? "" : className.substring(0, slash);
    }

    private List<ClassExport> classes() {
        List<ClassExport> result = new ArrayList<>();
        for (ClassInfo ci : in.classes()) {
            if (isExported(ci)) result.add(classExport(ci));
        }
        if (result.size() > MAX_CLASSES) {
            problems.add(result.size() + " classes would be exported; an export file holds at most 255 (§5.5)");
        }
        result.sort(Comparator.comparingInt(ClassExport::token));
        return result;
    }

    /** §5.5: libraries export public types, applet packages only public shareable interfaces. */
    private boolean isExported(ClassInfo ci) {
        if ((ci.accessFlags() & ACC_PUBLIC) == 0) return false;
        return in.library() || (ci.isInterface() && hierarchy.isSubtypeOf(ci, ExportHierarchy.SHAREABLE));
    }

    private ClassExport classExport(ClassInfo ci) {
        TokenMap.ClassEntry entry = in.tokenMap().findClass(ci.thisClass());
        if (entry.token() < 0 || entry.token() > MAX_CLASS_TOKEN) {
            problems.add(name(ci) + " is public but has no class token (JCVM 3.1 §4.3.7.2)");
        }
        List<String> supers = ci.isInterface()
                ? List.of(ExportHierarchy.OBJECT) : hierarchy.publicSuperclasses(ci);
        List<String> interfaces = hierarchy.allInterfaces(ci).stream().filter(hierarchy::isPublic).toList();
        return new ClassExport(ci.thisClass(), entry.token(), classFlags(ci),
                methods(ci, entry), fields(ci, entry), supers, interfaces);
    }

    private int classFlags(ClassInfo ci) {
        int flags = ci.accessFlags() & CLASS_FILE_FLAGS;
        if (hierarchy.isSubtypeOf(ci, ExportHierarchy.SHAREABLE)) flags |= ExportFile.ACC_SHAREABLE;
        if (hierarchy.isSubtypeOf(ci, ExportHierarchy.REMOTE)) flags |= ExportFile.ACC_REMOTE;
        return flags;
    }

    // ── fields (§5.8) ──

    private List<FieldExport> fields(ClassInfo ci, TokenMap.ClassEntry entry) {
        List<FieldExport> instance = new ArrayList<>();
        List<FieldExport> statics = new ArrayList<>();
        for (FieldInfo f : ci.fields()) {
            if ((f.accessFlags() & (ACC_PUBLIC | ACC_PROTECTED)) == 0) continue;
            checkDescriptor(ci, f.name(), f.descriptor());
            FieldExport export = f.isStatic() ? staticField(ci, entry, f) : instanceField(ci, entry, f);
            if (export != null) (f.isStatic() ? statics : instance).add(export);
        }
        instance.addAll(statics);
        return instance;
    }

    private FieldExport instanceField(ClassInfo ci, TokenMap.ClassEntry entry, FieldInfo f) {
        int token = token(() -> entry.findInstanceField(f.name()).token(), ci, "instance field " + f.name());
        return new FieldExport(f.name(), f.descriptor(), token, f.accessFlags() & FIELD_FLAGS);
    }

    private FieldExport staticField(ClassInfo ci, TokenMap.ClassEntry entry, FieldInfo f) {
        int flags = f.accessFlags() & FIELD_FLAGS;
        boolean primitive = f.descriptor().length() == 1;
        if (f.isFinal() && primitive) {
            if (f.constantValue() instanceof Integer value) {
                return new FieldExport(f.name(), f.descriptor(), ExportFile.CONSTANT_FIELD_TOKEN, flags, value);
            }
            problems.add(name(ci) + "." + f.name() + " is a static final " + f.descriptor() + " field without a"
                    + " compile-time constant value; an export file describes such a field only as a constant"
                    + " with a ConstantValue attribute (JCVM 3.1 §5.8, §5.10.1)");
            return null;
        }
        int token = token(() -> entry.findStaticField(f.name()).token(), ci, "static field " + f.name());
        return new FieldExport(f.name(), f.descriptor(), token, flags);
    }

    // ── methods (§5.9) ──

    private List<MethodExport> methods(ClassInfo ci, TokenMap.ClassEntry entry) {
        List<MethodExport> statics = new ArrayList<>();
        for (MethodInfo m : ci.methods()) {
            boolean staticKind = m.isStatic() || m.isConstructor();
            if (!staticKind || m.isStaticInitializer() || (m.accessFlags() & (ACC_PUBLIC | ACC_PROTECTED)) == 0) {
                continue;
            }
            checkDescriptor(ci, m.name(), m.descriptor());
            int token = token(() -> entry.findStaticMethod(m.name(), m.descriptor()).token(), ci,
                    "static method " + m.name() + m.descriptor());
            int flags = m.accessFlags() & METHOD_FLAGS & (m.isConstructor() ? ~ACC_STATIC : ~0);
            statics.add(new MethodExport(m.name(), m.descriptor(), token, flags));
        }
        statics.sort(Comparator.comparingInt(MethodExport::token));
        List<MethodExport> result = new ArrayList<>(statics);
        result.addAll(instanceMethods(ci, entry));
        return result;
    }

    private List<MethodExport> instanceMethods(ClassInfo ci, TokenMap.ClassEntry entry) {
        List<MethodExport> result = new ArrayList<>();
        for (String key : hierarchy.visibleInstanceMethods(ci)) {
            int paren = key.indexOf('(');
            String name = key.substring(0, paren);
            String descriptor = key.substring(paren);
            OptionalInt flags = ci.isInterface()
                    ? hierarchy.interfaceMethodFlags(ci, name, descriptor)
                    : hierarchy.instanceMethodFlags(ci, name, descriptor);
            if (flags.isEmpty() || (flags.getAsInt() & (ACC_PUBLIC | ACC_PROTECTED)) == 0) continue;
            checkDescriptor(ci, name, descriptor);
            int token = token(() -> entry.findVirtualMethod(name, descriptor).token(), ci,
                    (ci.isInterface() ? "interface method " : "virtual method ") + key);
            if (token > 0x7F) {
                problems.add(name(ci) + "." + key + " has the private virtual method token " + token
                        + " although it is public or protected (JCVM 3.1 §4.3.7.6)");
            }
            result.add(new MethodExport(name, descriptor, token, flags.getAsInt() & METHOD_FLAGS & ~ACC_STATIC));
        }
        result.sort(Comparator.comparingInt(MethodExport::token));
        return result;
    }

    // ── helpers ──

    /** §5.8, §5.9: classes named in the descriptor of an exported member must be public. */
    private void checkDescriptor(ClassInfo ci, String member, String descriptor) {
        Matcher m = CLASS_IN_DESCRIPTOR.matcher(descriptor);
        while (m.find()) {
            String type = m.group(1);
            if (!hierarchy.isPublic(type)) {
                problems.add(name(ci) + "." + member + " is exported but its descriptor " + descriptor
                        + " names the package-visible class " + type.replace('/', '.')
                        + "; exported descriptors may only use public classes (JCVM 3.1 §5.8, §5.9)");
            }
        }
    }

    private interface TokenLookup {
        int token();
    }

    private int token(TokenLookup lookup, ClassInfo ci, String what) {
        try {
            return lookup.token();
        } catch (NoSuchElementException e) {
            problems.add("no token was assigned to the " + what + " of " + name(ci) + " (JCVM 3.1 §4.3.7)");
            return 0;
        }
    }

    private static String name(ClassInfo ci) {
        return ci.thisClass().replace('/', '.');
    }
}
