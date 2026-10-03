package name.velikodniy.jcexpress.converter.capcheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Test-only parser of the compact Descriptor component (JCVM 3.1 §6.14).
 *
 * @param classes  class_descriptor_info entries in component order
 * @param cpTypes  constant_pool_types (offsets into type_descriptor_info, 0xFFFF for classes)
 * @param typeInfo the raw type_descriptor_info item (offsets in {@code cpTypes} index into it)
 */
public record DescriptorView(List<ClassDescriptor> classes, List<Integer> cpTypes, byte[] typeInfo) {

    /** Class descriptor flag ACC_PUBLIC (Table 6-17). */
    public static final int CLASS_ACC_PUBLIC = 0x01;
    /** Class descriptor flag ACC_INTERFACE (Table 6-17). */
    public static final int CLASS_ACC_INTERFACE = 0x40;
    /** Field/method descriptor flag ACC_PUBLIC (Tables 6-18, 6-20). */
    public static final int ACC_PUBLIC = 0x01;
    /** Field/method descriptor flag ACC_PRIVATE. */
    public static final int ACC_PRIVATE = 0x02;
    /** Field/method descriptor flag ACC_PROTECTED. */
    public static final int ACC_PROTECTED = 0x04;
    /** Field/method descriptor flag ACC_STATIC. */
    public static final int ACC_STATIC = 0x08;
    /** Method descriptor flag ACC_ABSTRACT. */
    public static final int ACC_ABSTRACT = 0x40;
    /** Method descriptor flag ACC_INIT. */
    public static final int ACC_INIT = 0x80;

    static DescriptorView parse(byte[] b) {
        int count = CapImage.u1(b, 0);
        int o = 1;
        List<ClassDescriptor> classes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ClassDescriptor cd = parseClass(b, o);
            classes.add(cd);
            o = cd.end();
        }
        byte[] typeInfo = java.util.Arrays.copyOfRange(b, o, b.length);
        int cpCount = CapImage.u2(typeInfo, 0);
        List<Integer> cpTypes = new ArrayList<>(cpCount);
        for (int i = 0; i < cpCount; i++) {
            cpTypes.add(CapImage.u2(typeInfo, 2 + 2 * i));
        }
        return new DescriptorView(List.copyOf(classes), List.copyOf(cpTypes), typeInfo);
    }

    private static ClassDescriptor parseClass(byte[] b, int start) {
        int token = CapImage.u1(b, start);
        int flags = CapImage.u1(b, start + 1);
        int thisRef = CapImage.u2(b, start + 2);
        int ifaceCount = CapImage.u1(b, start + 4);
        int fieldCount = CapImage.u2(b, start + 5);
        int methodCount = CapImage.u2(b, start + 7);
        int o = start + 9;
        List<Integer> interfaces = new ArrayList<>();
        for (int i = 0; i < ifaceCount; i++, o += 2) {
            interfaces.add(CapImage.u2(b, o));
        }
        List<FieldDescriptor> fields = new ArrayList<>();
        for (int i = 0; i < fieldCount; i++, o += 7) {
            fields.add(new FieldDescriptor(CapImage.u1(b, o), CapImage.u1(b, o + 1), CapImage.u1(b, o + 2),
                    CapImage.u1(b, o + 3), CapImage.u1(b, o + 4), CapImage.u2(b, o + 5)));
        }
        List<MethodDescriptor> methods = new ArrayList<>();
        for (int i = 0; i < methodCount; i++, o += 12) {
            methods.add(new MethodDescriptor(CapImage.u1(b, o), CapImage.u1(b, o + 1), CapImage.u2(b, o + 2),
                    CapImage.u2(b, o + 4), CapImage.u2(b, o + 6), CapImage.u2(b, o + 8),
                    CapImage.u2(b, o + 10)));
        }
        return new ClassDescriptor(start, o, token, flags, thisRef, List.copyOf(interfaces),
                List.copyOf(fields), List.copyOf(methods));
    }

    /**
     * Finds the class descriptor whose {@code this_class_ref} equals the given offset.
     *
     * @param classRef internal class_ref
     * @return the descriptor, if any
     */
    public Optional<ClassDescriptor> byClassRef(int classRef) {
        return classes.stream().filter(c -> c.thisClassRef() == classRef).findFirst();
    }

    /**
     * Decodes the type descriptor at the given offset of type_descriptor_info into nibbles.
     *
     * @param offset offset into type_descriptor_info
     * @return nibble values
     */
    public int[] nibblesAt(int offset) {
        int count = typeInfo[offset] & 0xFF;
        int[] nibbles = new int[count];
        for (int i = 0; i < count; i++) {
            int b = typeInfo[offset + 1 + i / 2] & 0xFF;
            nibbles[i] = (i % 2 == 0) ? (b >> 4) : (b & 0x0F);
        }
        return nibbles;
    }

    /**
     * class_descriptor_info_compact (§6.14.2).
     *
     * @param start        offset of the entry in the component body (for diagnostics)
     * @param end          offset after the entry
     * @param token        class token or 0xFF
     * @param flags        access flags (Table 6-17)
     * @param thisClassRef this_class_ref
     * @param interfaces   interfaces class_refs
     * @param fields       field descriptors
     * @param methods      method descriptors
     */
    public record ClassDescriptor(int start, int end, int token, int flags, int thisClassRef,
                                  List<Integer> interfaces, List<FieldDescriptor> fields,
                                  List<MethodDescriptor> methods) {
        /** @return {@code true} if this descriptor describes an interface */
        public boolean isInterface() {
            return (flags & CLASS_ACC_INTERFACE) != 0;
        }

        /** @return {@code true} if the class is public */
        public boolean isPublic() {
            return (flags & CLASS_ACC_PUBLIC) != 0;
        }
    }

    /**
     * field_descriptor_info (§6.14.3).
     *
     * @param token token or 0xFF
     * @param flags access flags (Table 6-18)
     * @param b1    first field_ref byte
     * @param b2    second field_ref byte
     * @param b3    third field_ref byte
     * @param type  primitive_type (high bit set) or reference_type offset
     */
    public record FieldDescriptor(int token, int flags, int b1, int b2, int b3, int type) {
        /** @return {@code true} if ACC_STATIC */
        public boolean isStatic() {
            return (flags & ACC_STATIC) != 0;
        }

        /** @return {@code true} if public or protected */
        public boolean isExternallyVisible() {
            return (flags & (ACC_PUBLIC | ACC_PROTECTED)) != 0;
        }

        /** @return static_field_ref internal offset ({@code b2 b3}) */
        public int staticOffset() {
            return (b2 << 8) | b3;
        }

        /** @return instance_field class_ref ({@code b1 b2}) */
        public int instanceClassRef() {
            return (b1 << 8) | b2;
        }

        /** @return instance_field token ({@code b3}) */
        public int instanceToken() {
            return b3;
        }

        /** @return {@code true} if the field has a reference (non-primitive) type */
        public boolean isReference() {
            return (type & 0x8000) == 0;
        }

        /** @return {@code true} if the field is of primitive type int */
        public boolean isInt() {
            return type == 0x8005;
        }
    }

    /**
     * method_descriptor_info_compact (§6.14.4).
     *
     * @param token         token or 0xFF
     * @param flags         access flags (Table 6-20)
     * @param methodOffset  method_offset
     * @param typeOffset    type_offset
     * @param bytecodeCount bytecode_count
     * @param handlerCount  exception_handler_count
     * @param handlerIndex  exception_handler_index
     */
    public record MethodDescriptor(int token, int flags, int methodOffset, int typeOffset,
                                   int bytecodeCount, int handlerCount, int handlerIndex) {
        /** @return {@code true} if ACC_STATIC */
        public boolean isStatic() {
            return (flags & ACC_STATIC) != 0;
        }

        /** @return {@code true} if ACC_INIT (constructor) */
        public boolean isInit() {
            return (flags & ACC_INIT) != 0;
        }

        /** @return {@code true} if ACC_PRIVATE */
        public boolean isPrivate() {
            return (flags & ACC_PRIVATE) != 0;
        }

        /** @return {@code true} if a virtual method (not static, not constructor, not private) */
        public boolean isVirtual() {
            return !isStatic() && !isInit() && !isPrivate();
        }
    }
}
