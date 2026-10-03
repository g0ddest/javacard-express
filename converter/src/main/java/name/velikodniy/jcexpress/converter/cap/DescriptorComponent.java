package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.token.ImportedTypes;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import name.velikodniy.jcexpress.converter.translate.JcvmConstantPool;
import name.velikodniy.jcexpress.converter.translate.TranslatedMethod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Generates the CAP Descriptor component (tag 11) as defined in JCVM 3.1 §6.14.
 *
 * <p>The component describes every class and interface of the package (in Class component order)
 * with its fields and methods, and ends with the {@code type_descriptor_info} table holding the
 * type of every constant pool entry and of every described field and method:
 * <pre>
 * u1 class_count
 * class_descriptor_info { u1 token; u1 access_flags; class_ref this_class_ref; u1 interface_count;
 *                         u2 field_count; u2 method_count; class_ref interfaces[];
 *                         field_descriptor_info fields[]; method_descriptor_info methods[] }
 * field_descriptor_info  { u1 token; u1 access_flags; field_ref (3 bytes); u2 type }
 * method_descriptor_info { u1 token; u1 access_flags; u2 method_offset; u2 type_offset;
 *                          u2 bytecode_count; u2 exception_handler_count; u2 exception_handler_index }
 * type_descriptor_info   { u2 constant_pool_count; u2 constant_pool_types[]; type_descriptor[] }
 * </pre>
 *
 * <p>Rules applied (§6.14.2 - §6.14.4): package-visible classes have token 0xFF; an interface has
 * no interfaces and no fields, and lists all of its methods including inherited ones with
 * method_offset 0; a class lists the same interface hierarchy as its Class component entry and
 * the methods it declares ({@code <clinit>} is not a CAP method, §6.10); static field_refs hold
 * the offset into the static field image; exception_handler_index points at the method's first
 * handler. Static fields are listed in image order, instance fields in token order.
 *
 * @see ClassComponent
 * @see MethodComponent
 * @see ConstantPoolComponent
 */
public final class DescriptorComponent {

    public static final int TAG = 11;

    /** Token value of members without a token (§6.14.2 - §6.14.4). */
    private static final int NO_TOKEN = 0xFF;

    private DescriptorComponent() {}

    /**
     * Everything the Descriptor component describes.
     *
     * @param classes            classes and interfaces in Class component order
     * @param tokenMap           token assignment
     * @param classOffsets       internal class name to its Class component offset
     * @param methodOffsets      Method component offset of each translated method (global index)
     * @param methodIndexMap     {@code "class:name:descriptor"} to global method index
     * @param allMethods         translated methods in Method component order
     * @param cp                 the constant pool
     * @param cpTypeDescriptors  CP index to the JVM descriptor of the referenced field or method
     * @param classRefResolver   class name to the {@code class_ref} used in type descriptors
     * @param staticFieldOffsets {@code "class:field"} to the offset in the static field image
     * @param imported           information about imported interfaces
     */
    public record Input(List<ClassInfo> classes, TokenMap tokenMap, Map<String, Integer> classOffsets,
                        int[] methodOffsets, Map<String, Integer> methodIndexMap,
                        List<TranslatedMethod> allMethods, JcvmConstantPool cp,
                        Map<Integer, String> cpTypeDescriptors, Function<String, Integer> classRefResolver,
                        Map<String, Integer> staticFieldOffsets, ImportedTypes imported) {}

    /**
     * Generates the Descriptor component (§6.14).
     *
     * @param in the described package
     * @return complete component bytes including tag and size
     * @throws IllegalStateException if a described element has no location in its component
     */
    public static byte[] generate(Input in) {
        TypeHierarchy hierarchy = new TypeHierarchy(in.classes(), in.tokenMap(), in.imported());
        Writer w = new Writer(in, hierarchy, buildTypeTable(in, hierarchy));
        w.info.u1(in.classes().size()); // class_count
        in.classes().forEach(w::writeClass);
        w.info.bytes(w.types.toBytes());
        return HeaderComponent.wrapComponent(TAG, w.info.toByteArray());
    }

    /**
     * Generates the Descriptor component; class offsets are given in Class component order and
     * static field offsets are recomputed from the class declarations (no {@code <clinit>}
     * information).
     *
     * @param classes           classes and interfaces in Class component order
     * @param tokenMap          token assignment
     * @param methodOffsets     Method component offset of each translated method
     * @param methodIndexMap    {@code "class:name:descriptor"} to global method index
     * @param classOffsets      Class component offset of each class, in the order of {@code classes}
     * @param allMethods        translated methods in Method component order
     * @param cp                the constant pool
     * @param cpTypeDescriptors CP index to JVM descriptor
     * @param classRefResolver  class name to {@code class_ref}
     * @return complete component bytes including tag and size
     * @deprecated use {@link #generate(Input)}, which takes the static field image offsets
     */
    @Deprecated(since = "0.4.0")
    public static byte[] generate(List<ClassInfo> classes, TokenMap tokenMap, int[] methodOffsets,
                                  Map<String, Integer> methodIndexMap, int[] classOffsets,
                                  List<TranslatedMethod> allMethods, JcvmConstantPool cp,
                                  Map<Integer, String> cpTypeDescriptors,
                                  Function<String, Integer> classRefResolver) {
        Map<String, Integer> byName = new HashMap<>();
        for (int i = 0; i < classes.size() && i < classOffsets.length; i++) {
            byName.put(classes.get(i).thisClass(), classOffsets[i]);
        }
        return generate(new Input(classes, tokenMap, byName, methodOffsets, methodIndexMap, allMethods, cp,
                cpTypeDescriptors, classRefResolver, StaticFieldComponent.generate(classes).fieldOffsetMap(),
                ImportedTypes.of(name -> List.of())));
    }

    /** Nibble of a single type (see {@link TypeDescriptors#nibble(String)}). */
    static int typeNibble(String descriptor) {
        return TypeDescriptors.nibble(descriptor);
    }

    /** Nibbles of a type or method descriptor (see {@link TypeDescriptors#nibbles}). */
    static int[] descriptorToNibbles(String descriptor, Function<String, Integer> classRefResolver) {
        return TypeDescriptors.nibbles(descriptor, classRefResolver);
    }

    /**
     * Registers the types in the order: constant pool entries, method signatures (classes in
     * component order, methods in declaration order; inherited methods for interfaces), then
     * reference field types.
     */
    private static TypeDescriptorTable buildTypeTable(Input in, TypeHierarchy hierarchy) {
        Function<String, Integer> refs = in.classRefResolver();
        TypeDescriptorTable table = new TypeDescriptorTable(in.cp().size());
        for (int i = 0; i < in.cp().size(); i++) {
            String desc = in.cpTypeDescriptors().get(i);
            if (desc != null) {
                table.registerCpType(i, TypeDescriptors.nibbles(desc, refs));
            }
        }
        for (ClassInfo ci : in.classes()) {
            describedSignatures(ci, hierarchy).forEach(d -> table.register(TypeDescriptors.nibbles(d, refs)));
        }
        for (ClassInfo ci : in.classes()) {
            for (FieldInfo fi : describedFields(ci)) {
                if (!fi.descriptor().startsWith("L") && !fi.descriptor().startsWith("[")) {
                    continue;
                }
                table.register(TypeDescriptors.typeNibbles(fi.descriptor(), refs));
            }
        }
        table.seal();
        return table;
    }

    private static List<String> describedSignatures(ClassInfo ci, TypeHierarchy hierarchy) {
        if (ci.isInterface()) {
            return hierarchy.interfaceMethods(ci.thisClass()).stream().map(TokenMap.MethodEntry::descriptor).toList();
        }
        return describedMethods(ci).stream().map(MethodInfo::descriptor).toList();
    }

    /** Methods of a class described in the component: all but {@code <clinit>} (§6.10, §6.14.2). */
    private static List<MethodInfo> describedMethods(ClassInfo ci) {
        return ci.methods().stream().filter(m -> !m.isStaticInitializer()).toList();
    }

    /** Fields described for a class: all but compile-time constants; none for an interface. */
    private static List<FieldInfo> describedFields(ClassInfo ci) {
        if (ci.isInterface()) {
            return List.of();
        }
        return ci.fields().stream().filter(f -> !f.isCompileTimeConstant()).toList();
    }

    /** Index of each method's first exception handler in the Method component (§6.14.4). */
    private static int[] firstHandlerIndices(List<TranslatedMethod> methods) {
        int[] first = new int[methods.size()];
        int running = 0;
        for (int i = 0; i < methods.size(); i++) {
            first[i] = running;
            running += methods.get(i).exceptionHandlers().size();
        }
        return first;
    }

    /** Serializes the class descriptors. */
    private static final class Writer {
        private final BinaryWriter info = new BinaryWriter();
        private final Input in;
        private final TypeHierarchy hierarchy;
        private final TypeDescriptorTable types;
        private final int[] firstHandler;

        Writer(Input in, TypeHierarchy hierarchy, TypeDescriptorTable types) {
            this.in = in;
            this.hierarchy = hierarchy;
            this.types = types;
            this.firstHandler = firstHandlerIndices(in.allMethods());
        }

        /** §6.14.2 class_descriptor_info_compact. */
        void writeClass(ClassInfo ci) {
            TokenMap.ClassEntry entry = in.tokenMap().findClass(ci.thisClass());
            int classRef = required(in.classOffsets(), ci.thisClass(), "Class component entry");
            List<String> interfaces = ci.isInterface() ? List.of() : hierarchy.interfaceClosure(ci.interfaces());
            List<FieldInfo> fields = orderedFields(ci, entry);
            int methodCount = ci.isInterface() ? hierarchy.interfaceMethods(ci.thisClass()).size()
                    : describedMethods(ci).size();
            info.u1(entry.token());
            info.u1(classFlags(ci));
            info.u2(classRef);
            info.u1(interfaces.size());
            info.u2(fields.size());
            info.u2(methodCount);
            interfaces.forEach(i -> info.u2(in.classRefResolver().apply(i)));
            fields.forEach(f -> writeField(ci, entry, classRef, f));
            if (ci.isInterface()) {
                hierarchy.interfaceMethods(ci.thisClass()).forEach(this::writeInterfaceMethod);
            } else {
                describedMethods(ci).forEach(m -> writeMethod(ci, entry, m));
            }
        }

        /** Static fields in static field image order, then instance fields in token order. */
        private List<FieldInfo> orderedFields(ClassInfo ci, TokenMap.ClassEntry entry) {
            List<FieldInfo> statics = new ArrayList<>(describedFields(ci).stream().filter(FieldInfo::isStatic).toList());
            statics.sort(Comparator.comparingInt(f -> staticOffset(ci, f)));
            List<FieldInfo> instance = new ArrayList<>(describedFields(ci).stream().filter(f -> !f.isStatic()).toList());
            instance.sort(Comparator.comparingInt(f -> entry.findInstanceField(f.name()).token()));
            statics.addAll(instance);
            return statics;
        }

        /** §6.14.3 field_descriptor_info. */
        private void writeField(ClassInfo ci, TokenMap.ClassEntry entry, int classRef, FieldInfo fi) {
            if (fi.isStatic()) {
                info.u1(staticFieldToken(entry, fi));
                info.u1(memberFlags(fi.accessFlags()));
                info.u1(0);                      // static_field_ref: padding
                info.u2(staticOffset(ci, fi));   // offset into the static field image
            } else {
                int token = entry.findInstanceField(fi.name()).token();
                info.u1(token);
                info.u1(memberFlags(fi.accessFlags()));
                info.u2(classRef);               // instance_field: class
                info.u1(token);                  // instance_field: token
            }
            info.u2(TypeDescriptors.fieldType(fi.descriptor(), types, in.classRefResolver()));
        }

        private int staticOffset(ClassInfo ci, FieldInfo fi) {
            return required(in.staticFieldOffsets(), ci.thisClass() + ":" + fi.name(), "static field image offset");
        }

        /** §6.14.4 method_descriptor_info of a class method. */
        private void writeMethod(ClassInfo ci, TokenMap.ClassEntry entry, MethodInfo mi) {
            int index = required(in.methodIndexMap(), ci.thisClass() + ":" + mi.name() + ":" + mi.descriptor(),
                    "method_info");
            TranslatedMethod tm = in.allMethods().get(index);
            int handlers = tm.exceptionHandlers().size();
            info.u1(methodToken(entry, mi));
            info.u1(memberFlags(mi.accessFlags()) | (mi.isAbstract() ? 0x40 : 0) | (mi.isConstructor() ? 0x80 : 0));
            info.u2(in.methodOffsets()[index]);
            info.u2(types.offsetOf(TypeDescriptors.nibbles(mi.descriptor(), in.classRefResolver())));
            info.u2(tm.bytecode().length);
            info.u2(handlers);
            info.u2(handlers == 0 ? 0 : firstHandler[index]);
        }

        /** §6.14.4: interface methods are public abstract, method_offset 0, no bytecodes. */
        private void writeInterfaceMethod(TokenMap.MethodEntry m) {
            info.u1(m.token());
            info.u1(0x01 | 0x40); // ACC_PUBLIC | ACC_ABSTRACT
            info.u2(0);
            info.u2(types.offsetOf(TypeDescriptors.nibbles(m.descriptor(), in.classRefResolver())));
            info.u2(0);
            info.u2(0);
            info.u2(0);
        }
    }

    /** §6.14.2 Table 6-17: ACC_PUBLIC 0x01, ACC_FINAL 0x10, ACC_INTERFACE 0x40, ACC_ABSTRACT 0x80. */
    private static int classFlags(ClassInfo ci) {
        int flags = (ci.accessFlags() & 0x0001) | (ci.accessFlags() & 0x0010);
        if (ci.isInterface()) {
            flags |= 0x40;
        }
        if (ci.isAbstract()) {
            flags |= 0x80;
        }
        return flags;
    }

    /** Tables 6-18/6-20: ACC_PUBLIC, ACC_PRIVATE, ACC_PROTECTED, ACC_STATIC, ACC_FINAL as in class files. */
    private static int memberFlags(int jvmFlags) {
        return jvmFlags & 0x001F;
    }

    private static int staticFieldToken(TokenMap.ClassEntry entry, FieldInfo fi) {
        return entry.staticFields().stream().filter(f -> f.name().equals(fi.name()))
                .mapToInt(TokenMap.FieldEntry::token).findFirst().orElse(NO_TOKEN);
    }

    /**
     * §6.14.4: static methods and constructors carry their static token, virtual methods their
     * (public or private) virtual token; private methods and package-visible static methods and
     * constructors have none.
     */
    private static int methodToken(TokenMap.ClassEntry entry, MethodInfo mi) {
        if (mi.isPrivate()) {
            return NO_TOKEN;
        }
        if (mi.isStatic() || mi.isConstructor()) {
            return entry.staticMethods().stream()
                    .filter(m -> m.name().equals(mi.name()) && m.descriptor().equals(mi.descriptor()))
                    .mapToInt(TokenMap.MethodEntry::token).findFirst().orElse(NO_TOKEN);
        }
        return entry.findVirtualMethod(mi.name(), mi.descriptor()).token();
    }

    private static int required(Map<String, Integer> map, String key, String what) {
        Integer value = map.get(key);
        if (value == null) {
            throw new IllegalStateException("No " + what + " for " + key);
        }
        return value;
    }
}
