package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import name.velikodniy.jcexpress.converter.translate.JcvmConstantPool;
import name.velikodniy.jcexpress.converter.translate.TranslatedMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Generates the CAP Descriptor component (tag 11) as defined in JCVM 3.0.5 spec section 6.14.
 *
 * <p>The Descriptor component provides rich type metadata used for <em>off-card
 * verification</em>. An off-card verifier (such as the one in a card manager or
 * development tool) uses this information to type-check bytecodes, verify field
 * access patterns, and validate method signatures without needing the original
 * Java class files.
 *
 * <p>The component contains three major sections:
 * <ol>
 *   <li><b>Class descriptors</b> ({@code class_descriptor_info}): One per class/interface,
 *       containing the class token, access flags, a back-reference to the Class component
 *       offset, and counts of fields and methods. Each class descriptor is followed by
 *       its field and method descriptors.</li>
 *   <li><b>Field descriptors</b> ({@code field_descriptor_info}): 7 bytes each, containing
 *       the field token, access flags, a 3-byte field_ref (class_ref + token for instance
 *       fields, zero-padded for static fields), and a 2-byte type encoding (primitive types
 *       use {@code 0x8000 | nibble_code}, reference types use an offset into the type
 *       descriptor table).</li>
 *   <li><b>Method descriptors</b> ({@code method_descriptor_info}): 12 bytes each, containing
 *       the method token, access flags, offset into the Method component, offset into the
 *       type descriptor table (for the method signature), bytecode length, and exception
 *       handler count.</li>
 * </ol>
 *
 * <p>At the end of the component is the <b>type_descriptor_info</b> table, which contains:
 * <ul>
 *   <li>A per-CP-entry type mapping array ({@code constant_pool_types[]}), where each
 *       entry points to the type descriptor for that CP entry, or {@code 0xFFFF} if
 *       no type information is available (e.g., for ClassRef entries).</li>
 *   <li>Deduplicated type descriptors encoded as nibble arrays, where each nibble
 *       represents a JCVM type (1=void, 2=boolean, 3=byte, 4=short, 5=int, 6=reference,
 *       10-14=array types). Reference types (nibble 6 or 14) are followed by 4 nibbles
 *       encoding the u2 class_ref value. Method signatures are encoded as parameter types
 *       followed by the return type (per JCVM spec §6.14.2 Table 6-42).</li>
 * </ul>
 *
 * <p>Compile-time constants ({@code static final} primitives with ConstantValue) are
 * excluded from the field descriptors since javac inlines them at use sites.
 *
 * <p>Binary format (JCVM 3.0.5 spec section 6.14, Tables 6-36 through 6-42):
 * <pre>
 * u1  tag = 11
 * u2  size
 * u1  class_count
 * class_descriptor_info[class_count]:
 *   u1  token
 *   u1  access_flags          (ACC_PUBLIC=0x01, ACC_FINAL=0x10, ACC_INTERFACE=0x40, ACC_ABSTRACT=0x80)
 *   u2  this_class_ref        (byte offset into ClassComponent)
 *   u1  interface_count
 *   u2  field_count
 *   u2  method_count
 *   u2  interfaces[interface_count]
 *   field_descriptor_info[field_count]:
 *     u1  token
 *     u1  access_flags        (ACC_PUBLIC/PRIVATE/PROTECTED/STATIC/FINAL)
 *     field_ref (3 bytes):
 *       instance: u2 class_ref + u1 token
 *       static:   u2 0x0000 + u1 0x00
 *     u2  type                (primitive: 0x8000|nibble, reference: type table offset)
 *   method_descriptor_info[method_count]:
 *     u1  token
 *     u1  access_flags        (ACC_PUBLIC/.../ACC_ABSTRACT/ACC_INIT)
 *     u2  method_offset       (byte offset into Method component)
 *     u2  type_offset         (offset into type_descriptor_info)
 *     u2  bytecode_count      (number of bytecode bytes)
 *     u2  exception_handler_count
 *     u2  exception_handler_index
 * type_descriptor_info:
 *   u2  constant_pool_count
 *   u2  constant_pool_types[constant_pool_count]  (CP index to type offset, 0xFFFF=none)
 *   type_descriptor[]:                             (deduplicated, nibble-packed)
 *     u1  nibble_count
 *     u1  type_nibbles[(nibble_count+1)/2]         (2 nibbles per byte, high first)
 * </pre>
 *
 * @see ClassComponent
 * @see MethodComponent
 * @see ConstantPoolComponent
 */
public final class DescriptorComponent {

    public static final int TAG = 11;

    private DescriptorComponent() {}

    /**
     * Generates the Descriptor component bytes.
     *
     * @param classes          all classes in token order
     * @param tokenMap         token assignments
     * @param methodOffsets    offsets of each method in the Method component (global index)
     * @param methodIndexMap   maps "className:methodName:methodDesc" → global method index
     * @param classOffsets     byte offset of each class in ClassComponent, indexed by class token
     * @param allMethods       translated JCVM methods (for bytecodeCount and handler count)
     * @param cp               the JCVM constant pool (for type_descriptor_info)
     * @param cpTypeDescriptors maps CP index → JVM type descriptor (from ReferenceResolver)
     * @param classRefResolver  resolves class internal name → u2 class_ref value for type nibbles
     * @return complete component bytes including tag and size
     */
    @SuppressWarnings("java:S3776") // Inherently complex descriptor_component binary generation
    public static byte[] generate(List<ClassInfo> classes, TokenMap tokenMap,
                                   int[] methodOffsets,
                                   Map<String, Integer> methodIndexMap,
                                   int[] classOffsets,
                                   List<TranslatedMethod> allMethods,
                                   JcvmConstantPool cp,
                                   Map<Integer, String> cpTypeDescriptors,
                                   Function<String, Integer> classRefResolver) {
        // Phase 1: Build the type descriptor table first so we know offsets
        TypeTable typeTable = buildTypeTable(classes, cp, cpTypeDescriptors, classRefResolver);

        // --- descriptor_component (§6.14 Table 6-36) ---
        var info = new BinaryWriter();
        info.u1(classes.size()); // §6.14: u1 class_count

        for (ClassInfo ci : classes) {
            TokenMap.ClassEntry entry = tokenMap.findClass(ci.thisClass());

            // --- class_descriptor_info (§6.14 Table 6-37) ---
            info.u1(entry.token()); // §6.14 Table 6-37: u1 token

            // §6.14 Table 6-37: u1 access_flags
            // JCVM spec uses compact bit positions distinct from JVM class flags:
            // ACC_PUBLIC=0x01, ACC_FINAL=0x10, ACC_INTERFACE=0x40, ACC_ABSTRACT=0x80
            int flags = 0;
            if ((ci.accessFlags() & 0x0001) != 0) flags |= 0x01; // ACC_PUBLIC
            if ((ci.accessFlags() & 0x0010) != 0) flags |= 0x10; // ACC_FINAL
            if (ci.isInterface()) flags |= 0x40;                  // ACC_INTERFACE
            if (ci.isAbstract()) flags |= 0x80;                   // ACC_ABSTRACT
            info.u1(flags);

            // §6.14 Table 6-37: u2 this_class_ref (offset into ClassComponent)
            int classOffset = (entry.token() < classOffsets.length) ? classOffsets[entry.token()] : 0;
            info.u2(classOffset);

            info.u1(ci.interfaces().size()); // §6.14 Table 6-37: u1 interface_count

            // Filter out compile-time constant fields (static final primitives with known values)
            List<FieldInfo> includedFields = ci.fields().stream()
                    .filter(f -> !f.isCompileTimeConstant())
                    .toList();
            int fieldCount = includedFields.size();
            int methodCount = ci.methods().size();
            info.u2(fieldCount);  // §6.14 Table 6-37: u2 field_count
            info.u2(methodCount); // §6.14 Table 6-37: u2 method_count

            // §6.14 Table 6-37: u2[] interfaces (class_ref for each implemented interface)
            for (String iface : ci.interfaces()) {
                int ifaceRef = classRefResolver.apply(iface);
                info.u2(ifaceRef);
            }

            // --- field_descriptor_info (§6.14 Table 6-38, 7 bytes each) ---
            for (FieldInfo fi : includedFields) {
                int fieldToken;
                if (fi.isStatic()) {
                    boolean exported = (fi.accessFlags() & 0x0001) != 0
                            || (fi.accessFlags() & 0x0004) != 0;
                    fieldToken = exported
                            ? findStaticFieldToken(entry, fi.name())
                            : 0xFF;
                } else {
                    fieldToken = findInstanceFieldToken(entry, fi.name());
                }
                info.u1(fieldToken); // §6.14 Table 6-38: u1 token

                // §6.14 Table 6-38: u1 access_flags
                int fFlags = 0;
                if ((fi.accessFlags() & 0x0001) != 0) fFlags |= 0x01; // ACC_PUBLIC
                if ((fi.accessFlags() & 0x0002) != 0) fFlags |= 0x02; // ACC_PRIVATE
                if ((fi.accessFlags() & 0x0004) != 0) fFlags |= 0x04; // ACC_PROTECTED
                if ((fi.accessFlags() & 0x0008) != 0) fFlags |= 0x08; // ACC_STATIC
                if ((fi.accessFlags() & 0x0010) != 0) fFlags |= 0x10; // ACC_FINAL
                info.u1(fFlags);

                // §6.14 Table 6-38: field_ref (3 bytes)
                if (fi.isStatic()) {
                    // §6.14: static field_ref — zero-padded (no class_ref needed)
                    info.u1(0);
                    info.u1(0);
                    info.u1(0);
                } else {
                    // §6.14: instance field_ref — u2 class_ref + u1 token
                    info.u2(classOffset);
                    info.u1(fieldToken);
                }

                // §6.14 Table 6-38: u2 type (primitive: 0x8000|nibble, reference: type table offset)
                info.u2(encodeFieldType(fi.descriptor(), typeTable, classRefResolver));
            }

            // --- method_descriptor_info (§6.14 Table 6-39, 12 bytes each) ---
            for (MethodInfo mi : ci.methods()) {
                int mToken = 0;
                int mFlags = 0;
                // §6.14 Table 6-39: u1 access_flags
                if ((mi.accessFlags() & 0x0001) != 0) mFlags |= 0x01; // ACC_PUBLIC
                if ((mi.accessFlags() & 0x0002) != 0) mFlags |= 0x02; // ACC_PRIVATE
                if ((mi.accessFlags() & 0x0004) != 0) mFlags |= 0x04; // ACC_PROTECTED
                if ((mi.accessFlags() & 0x0008) != 0) mFlags |= 0x08; // ACC_STATIC
                if ((mi.accessFlags() & 0x0010) != 0) mFlags |= 0x10; // ACC_FINAL
                if (mi.isAbstract()) mFlags |= 0x40;                   // ACC_ABSTRACT
                if (mi.isConstructor()) mFlags |= 0x80;                // ACC_INIT

                if (mi.isStaticInitializer()) {
                    mToken = 0xFF;
                } else if (mi.isPrivate()) {
                    // All private methods: token 0xFF (not visible outside class)
                    mToken = 0xFF;
                } else if (mi.isConstructor() || mi.isStatic()) {
                    if ((mi.accessFlags() & 0x0001) == 0 && (mi.accessFlags() & 0x0004) == 0) {
                        // Package-private constructors and static methods: token 0xFF
                        mToken = 0xFF;
                    } else {
                        mToken = findStaticMethodToken(entry, mi.name(), mi.descriptor());
                    }
                } else {
                    mToken = findVirtualMethodToken(entry, mi.name(), mi.descriptor());
                }

                info.u1(mToken); // §6.14 Table 6-39: u1 token
                info.u1(mFlags); // §6.14 Table 6-39: u1 access_flags

                // §6.14 Table 6-39: u2 method_offset (into Method component)
                String key = ci.thisClass() + ":" + mi.name() + ":" + mi.descriptor();
                Integer globalIdx = methodIndexMap.get(key);
                if (globalIdx != null && globalIdx < methodOffsets.length) {
                    info.u2(methodOffsets[globalIdx]);
                } else {
                    info.u2(0);
                }

                // §6.14 Table 6-39: u2 type_offset (into type_descriptor_info)
                int typeOffset = typeTable.getMethodTypeOffset(mi.descriptor());
                info.u2(typeOffset);

                // §6.14 Table 6-39: u2 bytecode_count, u2 exception_handler_count
                if (globalIdx != null && globalIdx < allMethods.size()) {
                    TranslatedMethod tm = allMethods.get(globalIdx);
                    info.u2(tm.bytecode().length);           // §6.14: u2 bytecode_count
                    info.u2(tm.exceptionHandlers().size());  // §6.14: u2 exception_handler_count
                } else {
                    info.u2(0);
                    info.u2(0);
                }

                info.u2(0); // §6.14 Table 6-39: u2 exception_handler_index
            }
        }

        // --- type_descriptor_info (§6.14 Table 6-40) ---
        info.bytes(typeTable.toBytes());

        return HeaderComponent.wrapComponent(TAG, info.toByteArray());
    }

    /**
     * Encodes a field type as u2 for the field_descriptor_info.
     * Primitives: high bit set + type code. References: offset into type table.
     */
    private static int encodeFieldType(String descriptor, TypeTable typeTable,
                                        Function<String, Integer> classRefResolver) {
        char c = descriptor.charAt(0);
        return switch (c) {
            case 'Z' -> 0x8002; // boolean
            case 'B' -> 0x8003; // byte
            case 'S', 'C' -> 0x8004; // short/char
            case 'I' -> 0x8005; // int
            case 'V' -> 0x8001; // void (shouldn't appear for fields)
            default -> {
                // Reference or array type — use type table offset
                int[] nibbles = descriptorToNibbles(descriptor, classRefResolver);
                yield typeTable.getTypeOffset(nibbles);
            }
        };
    }

    private static int findStaticFieldToken(TokenMap.ClassEntry entry, String name) {
        for (TokenMap.FieldEntry fe : entry.staticFields()) {
            if (fe.name().equals(name)) return fe.token();
        }
        return 0;
    }

    private static int findInstanceFieldToken(TokenMap.ClassEntry entry, String name) {
        for (TokenMap.FieldEntry fe : entry.instanceFields()) {
            if (fe.name().equals(name)) return fe.token();
        }
        return 0;
    }

    private static int findStaticMethodToken(TokenMap.ClassEntry entry, String name, String desc) {
        for (TokenMap.MethodEntry me : entry.staticMethods()) {
            if (me.name().equals(name) && me.descriptor().equals(desc)) return me.token();
        }
        return 0;
    }

    private static int findVirtualMethodToken(TokenMap.ClassEntry entry, String name, String desc) {
        for (TokenMap.MethodEntry me : entry.virtualMethods()) {
            if (me.name().equals(name) && me.descriptor().equals(desc)) return me.token();
        }
        return 0;
    }

    /**
     * Returns the JCVM type nibble(s) for a single type descriptor.
     *
     * <p>Per JCVM spec §6.14.2 Table 6-42:
     * 1=void, 2=boolean, 3=byte, 4=short, 5=int,
     * 6=reference (followed by 4 nibbles of u2 class_ref),
     * 10=boolean[], 11=byte[], 12=short[], 13=int[],
     * 14=reference[] (followed by 4 nibbles of u2 class_ref)
     *
     * @param descriptor       JVM type descriptor (e.g., "Ljavacard/framework/APDU;")
     * @param classRefResolver resolves class internal name → u2 class_ref
     * @return nibble array (1 element for primitives, 5 for reference types)
     */
    static int[] typeNibbles(String descriptor, Function<String, Integer> classRefResolver) {
        char c = descriptor.charAt(0);
        return switch (c) {
            case 'V' -> new int[]{1};
            case 'Z' -> new int[]{2};
            case 'B' -> new int[]{3};
            case 'S', 'C' -> new int[]{4};
            case 'I' -> new int[]{5};
            case 'L' -> {
                // §6.14.2: reference type = nibble 6 + 4 nibbles of class_ref
                String className = descriptor.substring(1, descriptor.length() - 1);
                int classRef = classRefResolver.apply(className);
                yield new int[]{6,
                        (classRef >> 12) & 0xF, (classRef >> 8) & 0xF,
                        (classRef >> 4) & 0xF, classRef & 0xF};
            }
            case '[' -> {
                char elem = descriptor.charAt(1);
                if (elem == 'L') {
                    // §6.14.2: reference array = nibble 14 + 4 nibbles of class_ref
                    String className = descriptor.substring(2, descriptor.length() - 1);
                    int classRef = classRefResolver.apply(className);
                    yield new int[]{0x0E,
                            (classRef >> 12) & 0xF, (classRef >> 8) & 0xF,
                            (classRef >> 4) & 0xF, classRef & 0xF};
                }
                yield new int[]{arrayNibble(elem)};
            }
            default -> new int[]{6}; // fallback
        };
    }

    /**
     * Returns the JCVM type nibble for a single primitive type (no class_ref resolution needed).
     * Used only for primitive types where the result is always a single nibble.
     */
    static int typeNibble(String descriptor) {
        return switch (descriptor.charAt(0)) {
            case 'V' -> 1;
            case 'Z' -> 2;
            case 'B' -> 3;
            case 'S', 'C' -> 4;
            case 'I' -> 5;
            case 'L' -> 6;
            case '[' -> arrayNibble(descriptor.charAt(1));
            default -> 6;
        };
    }

    private static int arrayNibble(char elementType) {
        return switch (elementType) {
            case 'Z' -> 0x0A; // boolean[]
            case 'B' -> 0x0B; // byte[]
            case 'S', 'C' -> 0x0C; // short[]
            case 'I' -> 0x0D; // int[]
            default -> 0x0E; // reference[] (L...; or [...)
        };
    }

    /**
     * Converts a JVM type descriptor to JCVM nibble array.
     * For fields: single type nibbles. For methods: param types followed by return type.
     * Reference types (nibble 6 or 14) include 4 additional nibbles encoding the class_ref.
     *
     * @param descriptor       JVM type or method descriptor
     * @param classRefResolver resolves class internal name → u2 class_ref
     * @return nibble array per JCVM spec §6.14.2
     */
    static int[] descriptorToNibbles(String descriptor, Function<String, Integer> classRefResolver) {
        if (descriptor.startsWith("(")) {
            return methodDescriptorToNibbles(descriptor, classRefResolver);
        }
        return typeNibbles(descriptor, classRefResolver);
    }

    /**
     * Converts a JVM method descriptor to JCVM nibble array.
     * Per JCVM spec §6.14.2: parameter types first, return type last.
     *
     * @param desc             JVM method descriptor (e.g., "([BSB)V")
     * @param classRefResolver resolves class internal name → u2 class_ref
     * @return nibble array: [param1_nibbles..., param2_nibbles..., ..., return_nibbles...]
     */
    @SuppressWarnings("java:S3776") // Inherently complex JVM descriptor parsing
    private static int[] methodDescriptorToNibbles(String desc,
                                                    Function<String, Integer> classRefResolver) {
        List<Integer> nibbles = new ArrayList<>();

        // §6.14.2: Parameter types FIRST (before return type)
        int i = 1; // skip '('
        while (i < desc.length() && desc.charAt(i) != ')') {
            char c = desc.charAt(i);
            if (c == 'L') {
                int semi = desc.indexOf(';', i);
                String typeDesc = desc.substring(i, semi + 1);
                for (int n : typeNibbles(typeDesc, classRefResolver)) nibbles.add(n);
                i = semi + 1;
            } else if (c == '[') {
                // Array type — find the full array descriptor
                int start = i;
                i++; // skip '['
                if (i < desc.length()) {
                    char elem = desc.charAt(i);
                    if (elem == 'L') {
                        int semi = desc.indexOf(';', i);
                        String typeDesc = desc.substring(start, semi + 1);
                        for (int n : typeNibbles(typeDesc, classRefResolver)) nibbles.add(n);
                        i = semi + 1;
                    } else if (elem == '[') {
                        // Multi-dimensional array — treated as reference array
                        nibbles.add(0x0E);
                        while (i < desc.length() && desc.charAt(i) == '[') i++;
                        if (i < desc.length() && desc.charAt(i) == 'L') {
                            i = desc.indexOf(';', i) + 1;
                        } else if (i < desc.length()) {
                            i++;
                        }
                    } else {
                        nibbles.add(arrayNibble(elem));
                        i++;
                    }
                }
            } else {
                nibbles.add(typeNibble(String.valueOf(c)));
                i++;
            }
        }

        // §6.14.2: Return type LAST
        int retStart = desc.indexOf(')') + 1;
        String retDesc = desc.substring(retStart);
        for (int n : typeNibbles(retDesc, classRefResolver)) nibbles.add(n);

        return nibbles.stream().mapToInt(Integer::intValue).toArray();
    }

    // ── Type descriptor table ──

    /**
     * Builds the type_descriptor_info table from class fields, methods, and CP entries.
     */
    @SuppressWarnings("java:S3776") // Inherently complex type table construction
    private static TypeTable buildTypeTable(List<ClassInfo> classes,
                                             JcvmConstantPool cp,
                                             Map<Integer, String> cpTypeDescriptors,
                                             Function<String, Integer> classRefResolver) {
        TypeTable table = new TypeTable(cp.size(), classRefResolver);

        // Register CP entry types first — Oracle processes these before method/field types,
        // so CP type descriptors determine the initial ordering of the type table.
        for (int i = 0; i < cp.size(); i++) {
            String desc = cpTypeDescriptors.get(i);
            if (desc != null) {
                int[] nibbles = descriptorToNibbles(desc, classRefResolver);
                table.registerCpType(i, nibbles);
            }
            // ClassRef entries without descriptor get 0xFFFF
        }

        // Register method types in class file declaration order (matches Oracle's behavior).
        // Oracle traverses methods in the order they appear in the .class file, not sorted
        // by token or split by static/virtual. Constructors and static methods naturally
        // appear before virtual overrides in standard javac output.
        for (ClassInfo ci : classes) {
            for (MethodInfo mi : ci.methods()) {
                int[] nibbles = methodDescriptorToNibbles(mi.descriptor(), classRefResolver);
                table.registerTypeDescriptor(nibbles);
            }
        }

        // Register field types last — for reference/array fields not already registered
        // via CP types (e.g., unused fields with no CP entry). Most field types are already
        // registered from CP InstanceFieldRef entries above, so this is a fallback.
        for (ClassInfo ci : classes) {
            for (FieldInfo fi : ci.fields()) {
                if (fi.isCompileTimeConstant()) continue;
                String desc = fi.descriptor();
                if (desc.startsWith("L") || desc.startsWith("[")) {
                    int[] nibbles = descriptorToNibbles(desc, classRefResolver);
                    table.registerTypeDescriptor(nibbles);
                }
            }
        }

        table.computeOffsets();
        return table;
    }

    /**
     * Manages the type_descriptor_info table appended at the end of the Descriptor component.
     *
     * <p>This table serves two purposes: (1) it maps each constant pool index to the
     * type descriptor of the referenced entity, and (2) it stores deduplicated,
     * nibble-packed type descriptors for fields and method signatures.
     *
     * <p>Type descriptors are registered during a build phase, then {@link #computeOffsets()}
     * assigns byte offsets within the table. The offsets are absolute from the start of
     * the type_descriptor_info section and are used in field and method descriptor entries.
     */
    private static final class TypeTable {
        private final int cpCount;
        private final Function<String, Integer> classRefResolver;
        // Unique type descriptors, keyed by nibble signature
        private final Map<String, int[]> uniqueTypes = new LinkedHashMap<>();
        // CP index → nibble key
        private final Map<Integer, String> cpToKey = new LinkedHashMap<>();
        // Nibble key → offset within the type descriptor entries (after CP types array)
        private final Map<String, Integer> typeOffsets = new LinkedHashMap<>();
        // Total header size: 2 (count) + cpCount * 2 (types array)
        private int headerSize;

        TypeTable(int cpCount, Function<String, Integer> classRefResolver) {
            this.cpCount = cpCount;
            this.classRefResolver = classRefResolver;
            this.headerSize = 2 + cpCount * 2;
        }

        void registerTypeDescriptor(int[] nibbles) {
            String key = nibbleKey(nibbles);
            uniqueTypes.putIfAbsent(key, nibbles);
        }

        void registerCpType(int cpIndex, int[] nibbles) {
            String key = nibbleKey(nibbles);
            uniqueTypes.putIfAbsent(key, nibbles);
            cpToKey.put(cpIndex, key);
        }

        void computeOffsets() {
            int offset = headerSize;
            for (var e : uniqueTypes.entrySet()) {
                typeOffsets.put(e.getKey(), offset);
                int nibbleCount = e.getValue().length;
                int byteCount = (nibbleCount + 1) / 2;
                offset += 1 + byteCount; // nibble_count(1) + packed_nibbles
            }
        }

        int getTypeOffset(int[] nibbles) {
            String key = nibbleKey(nibbles);
            Integer offset = typeOffsets.get(key);
            return offset != null ? offset : 0;
        }

        int getMethodTypeOffset(String methodDescriptor) {
            int[] nibbles = methodDescriptorToNibbles(methodDescriptor, classRefResolver);
            return getTypeOffset(nibbles);
        }

        byte[] toBytes() {
            // --- type_descriptor_info (§6.14 Table 6-40) ---
            var out = new BinaryWriter();

            out.u2(cpCount); // §6.14 Table 6-40: u2 constant_pool_count

            // §6.14 Table 6-40: u2[] constant_pool_types (CP index → type offset, 0xFFFF = none)
            for (int i = 0; i < cpCount; i++) {
                String key = cpToKey.get(i);
                if (key != null) {
                    Integer offset = typeOffsets.get(key);
                    out.u2(offset != null ? offset : 0xFFFF);
                } else {
                    out.u2(0xFFFF); // no type info for this CP entry (e.g., ClassRef)
                }
            }

            // §6.14 Table 6-42: type_descriptor entries (nibble-packed, deduplicated)
            for (var e : uniqueTypes.entrySet()) {
                int[] nibbles = e.getValue();
                out.u1(nibbles.length); // §6.14 Table 6-42: u1 nibble_count
                // §6.14 Table 6-42: u1[] type nibbles, packed 2 per byte (high nibble first)
                for (int i = 0; i < nibbles.length; i += 2) {
                    int high = nibbles[i];
                    int low = (i + 1 < nibbles.length) ? nibbles[i + 1] : 0;
                    out.u1((high << 4) | (low & 0x0F));
                }
            }

            return out.toByteArray();
        }

        private static String nibbleKey(int[] nibbles) {
            var sb = new StringBuilder();
            for (int n : nibbles) {
                if (!sb.isEmpty()) sb.append(',');
                sb.append(n);
            }
            return sb.toString();
        }
    }
}
