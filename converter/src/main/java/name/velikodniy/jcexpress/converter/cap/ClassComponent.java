package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.resolve.ReferenceResolver;
import name.velikodniy.jcexpress.converter.token.ImportedTypes;
import name.velikodniy.jcexpress.converter.token.TokenMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Generates the CAP Class component (tag 6) as defined in JCVM 3.1 §6.9.
 *
 * <p>The component lists every interface ({@code interface_info}) and then every class
 * ({@code class_info_compact}) of the package; supertypes precede subtypes. The offset of an entry
 * within the component's info item is its internal {@code class_ref} (§6.8.1), used by the
 * Constant Pool, Export and Descriptor components and by the entries of this component itself.
 *
 * <p>Layout (compact format, §6.9.2; CAP 2.2+ prepends {@code u2 signature_pool_length}, CAP 2.3
 * appends the token mapping to each class_info):
 * <pre>
 * interface_info {
 *   u1 bitfield { bit[4] flags (ACC_INTERFACE 0x8, ACC_SHAREABLE 0x4), bit[4] interface_count }
 *   class_ref superinterfaces[interface_count]       // direct and indirect
 * }
 * class_info_compact {
 *   u1 bitfield { bit[4] flags (ACC_SHAREABLE 0x4), bit[4] interface_count }
 *   class_ref super_class_ref
 *   u1 declared_instance_size                         // 16-bit cells
 *   u1 first_reference_token
 *   u1 reference_count
 *   u1 public_method_table_base
 *   u1 public_method_table_count
 *   u1 package_method_table_base
 *   u1 package_method_table_count
 *   u2 public_virtual_method_table[public_method_table_count]
 *   u2 package_virtual_method_table[package_method_table_count]
 *   implemented_interface_info { class_ref interface; u1 count; u1 index[count] } interfaces[]
 *   u1 public_virtual_method_token_mapping[public_method_count]   // CAP 2.3
 *   u1 CAP22_inheritable_public_method_token_count                // CAP 2.3
 * }
 * </pre>
 *
 * @see MethodComponent
 * @see DescriptorComponent
 * @see ExportComponent
 */
public final class ClassComponent {

    public static final int TAG = 6;

    /** interface_info / class_info flag ACC_INTERFACE in the bitfield (§6.9.2.1 Table 6-11). */
    static final int ACC_INTERFACE = 0x80;
    /** interface_info / class_info flag ACC_SHAREABLE in the bitfield (§6.9.2.1 Table 6-11). */
    static final int ACC_SHAREABLE = 0x40;
    /** Maximum interface_count of a class_info (§6.9.2.1). */
    static final int MAX_CLASS_INTERFACES = 15;
    /** Maximum interface_count of an interface_info (§6.9.2.1). */
    static final int MAX_SUPERINTERFACES = 14;
    private static final int NO_REFERENCE_FIELDS = 0xFF;

    private ClassComponent() {}

    /**
     * Generates the Class component in CAP format 2.1.
     *
     * @param classes        classes and interfaces in Class component order (see {@link TokenMap})
     * @param tokenMap       token assignment
     * @param methodOffsets  Method component offset of each translated method (global index)
     * @param methodIndexMap {@code "class:name:descriptor"} to global method index
     * @param resolver       resolver of external class references
     * @return component bytes and the offset of every entry, in the order of {@code classes}
     */
    public static ClassResult generate(List<ClassInfo> classes, TokenMap tokenMap, int[] methodOffsets,
                                       Map<String, Integer> methodIndexMap, ReferenceResolver resolver) {
        return generate(classes, tokenMap, methodOffsets, methodIndexMap, resolver, null);
    }

    /**
     * Generates the Class component.
     *
     * @param classes        classes and interfaces in Class component order
     * @param tokenMap       token assignment
     * @param methodOffsets  Method component offset of each translated method (global index)
     * @param methodIndexMap {@code "class:name:descriptor"} to global method index
     * @param resolver       resolver of external class references
     * @param version        target Java Card version; {@code null} means CAP format 2.1
     * @param oracleCompat   ignored: the layout always follows §6.9.2, which is also what Oracle's
     *                       converter produces
     * @return component bytes and the offset of every entry, in the order of {@code classes}
     * @deprecated use {@link #generate(List, TokenMap, int[], Map, ReferenceResolver, JavaCardVersion)}
     */
    @Deprecated
    public static ClassResult generate(List<ClassInfo> classes, TokenMap tokenMap, int[] methodOffsets,
                                       Map<String, Integer> methodIndexMap, ReferenceResolver resolver,
                                       JavaCardVersion version, boolean oracleCompat) {
        return generate(classes, tokenMap, methodOffsets, methodIndexMap, resolver, version);
    }

    /**
     * Generates the Class component (§6.9).
     *
     * @param classes        classes and interfaces in Class component order: interfaces first,
     *                       supertypes before subtypes (as listed by {@link TokenMap#classes()})
     * @param tokenMap       token assignment
     * @param methodOffsets  Method component offset of each translated method (global index)
     * @param methodIndexMap {@code "class:name:descriptor"} to global method index
     * @param resolver       resolver of external class references and imported type information
     * @param version        target Java Card version; {@code null} means CAP format 2.1
     * @return component bytes and the offset of every entry, in the order of {@code classes}
     * @throws IllegalStateException if the classes are not in Class component order or a limit of
     *                               §6.9.2.1 is exceeded
     */
    public static ClassResult generate(List<ClassInfo> classes, TokenMap tokenMap, int[] methodOffsets,
                                       Map<String, Integer> methodIndexMap, ReferenceResolver resolver,
                                       JavaCardVersion version) {
        ImportedTypes imported = resolver.importedTypes();
        TypeHierarchy hierarchy = new TypeHierarchy(classes, tokenMap, imported);
        Writer w = new Writer(hierarchy, tokenMap, resolver,
                new MethodTables(hierarchy, offsetsByKey(methodOffsets, methodIndexMap)),
                atLeast(version, 2), atLeast(version, 3));
        int[] offsets = new int[classes.size()];
        for (int i = 0; i < classes.size(); i++) {
            offsets[i] = w.write(classes.get(i));
        }
        return new ClassResult(HeaderComponent.wrapComponent(TAG, w.info.toByteArray()), offsets);
    }

    /** Whether the CAP format of {@code version} is 2.{@code minor} or later (2.1 when null). */
    private static boolean atLeast(JavaCardVersion version, int minor) {
        return version != null && (version.formatMajor() > 2 || version.formatMinor() >= minor);
    }

    private static Map<String, Integer> offsetsByKey(int[] methodOffsets, Map<String, Integer> methodIndexMap) {
        Map<String, Integer> result = new HashMap<>();
        methodIndexMap.forEach((key, idx) -> {
            if (idx < methodOffsets.length) {
                result.put(key, methodOffsets[idx]);
            }
        });
        return result;
    }

    /** Serializes the entries; keeps the offsets of the entries written so far. */
    private static final class Writer {
        private final BinaryWriter info = new BinaryWriter();
        private final Map<String, Integer> written = new HashMap<>();
        private final TypeHierarchy hierarchy;
        private final TokenMap tokenMap;
        private final ReferenceResolver resolver;
        private final MethodTables tables;
        private final boolean tokenMapping;

        Writer(TypeHierarchy hierarchy, TokenMap tokenMap, ReferenceResolver resolver, MethodTables tables,
               boolean signaturePool, boolean tokenMapping) {
            this.hierarchy = hierarchy;
            this.tokenMap = tokenMap;
            this.resolver = resolver;
            this.tables = tables;
            this.tokenMapping = tokenMapping;
            if (signaturePool) {
                info.u2(0); // §6.9: signature_pool_length (since CAP 2.2; no remote interfaces)
            }
        }

        int write(ClassInfo ci) {
            int offset = info.size();
            if (ci.isInterface()) {
                writeInterface(ci);
            } else {
                writeClass(ci, tokenMap.findClass(ci.thisClass()));
            }
            written.put(ci.thisClass(), offset);
            return offset;
        }

        /** §6.9.2.2 interface_info. */
        private void writeInterface(ClassInfo ci) {
            List<String> supers = hierarchy.interfaceClosure(ci.interfaces());
            requireAtMost(supers.size(), MAX_SUPERINTERFACES, ci, "superinterfaces");
            int flags = ACC_INTERFACE | (hierarchy.isShareableInterface(ci.thisClass()) ? ACC_SHAREABLE : 0);
            info.u1(flags | supers.size());
            supers.forEach(s -> info.u2(classRef(s)));
        }

        /** §6.9.2.3 class_info_compact. */
        private void writeClass(ClassInfo ci, TokenMap.ClassEntry entry) {
            List<String> interfaces = hierarchy.interfaceClosure(ci.interfaces());
            requireAtMost(interfaces.size(), MAX_CLASS_INTERFACES, ci, "implemented interfaces");
            info.u1((hierarchy.isShareableClass(ci) ? ACC_SHAREABLE : 0) | interfaces.size());
            info.u2(ci.superClass() == null ? 0xFFFF : classRef(ci.superClass())); // super_class_ref
            writeInstanceLayout(entry);
            MethodTables.Table pub = tables.table(ci, entry, false);
            MethodTables.Table pkg = tables.table(ci, entry, true);
            info.u1(pub.base());
            info.u1(pub.count());
            info.u1(pkg.base());
            info.u1(pkg.count());
            pub.entries().forEach(info::u2);
            pkg.entries().forEach(info::u2);
            for (String iface : interfaces) {
                writeImplementedInterface(ci, entry, iface);
            }
            if (tokenMapping) {
                writeTokenMapping(ci, pub); // since CAP 2.3
            }
        }

        /**
         * declared_instance_size (16-bit cells, two for int), first_reference_token and
         * reference_count; reference fields have consecutive tokens (§4.3.7.5, §6.9.2.3).
         */
        private void writeInstanceLayout(TokenMap.ClassEntry entry) {
            int cells = 0;
            int firstReference = NO_REFERENCE_FIELDS;
            int references = 0;
            for (TokenMap.FieldEntry fe : entry.instanceFields()) {
                cells += "I".equals(fe.descriptor()) ? 2 : 1;
                if (fe.descriptor().startsWith("L") || fe.descriptor().startsWith("[")) {
                    firstReference = Math.min(firstReference, fe.token());
                    references++;
                }
            }
            info.u1(cells);
            info.u1(firstReference);
            info.u1(references);
        }

        /** §6.9.2.5 implemented_interface_info: index[interface token] = class virtual token. */
        private void writeImplementedInterface(ClassInfo ci, TokenMap.ClassEntry entry, String iface) {
            List<TokenMap.MethodEntry> methods = hierarchy.interfaceMethods(iface);
            int count = methods.isEmpty() ? 0 : methods.getLast().token() + 1;
            info.u2(classRef(iface));
            info.u1(count);
            for (int t = 0; t < count; t++) {
                int token = t;
                TokenMap.MethodEntry im = methods.stream().filter(m -> m.token() == token).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Interface " + iface
                                + " has no method with token " + token));
                info.u1(implementingToken(ci, entry, iface, im));
            }
        }

        private static int implementingToken(ClassInfo ci, TokenMap.ClassEntry entry, String iface,
                                             TokenMap.MethodEntry im) {
            return entry.virtualMethods().stream()
                    .filter(m -> m.name().equals(im.name()) && m.descriptor().equals(im.descriptor()))
                    .mapToInt(TokenMap.MethodEntry::token).findFirst()
                    .orElseThrow(() -> new IllegalStateException(ci.thisClass() + " implements " + iface
                            + " but neither it nor a superclass declares " + im.name() + im.descriptor()
                            + "; declare the method (abstract if needed) in " + ci.thisClass()
                            + " (JCVM 3.1 §6.9.2.5)"));
        }

        /**
         * §6.9.2.7 public_virtual_method_token_mapping (identity for tokens inherited from the
         * direct superclass, 0xFF for methods introduced by this class) followed by
         * CAP22_inheritable_public_method_token_count.
         */
        private void writeTokenMapping(ClassInfo ci, MethodTables.Table pub) {
            int publicMethodCount = pub.base() + pub.count();
            int inherited = hierarchy.inheritedVirtuals(ci).stream().mapToInt(TokenMap.MethodEntry::token)
                    .filter(t -> (t & 0x80) == 0).max().orElse(-1) + 1;
            for (int t = 0; t < publicMethodCount; t++) {
                info.u1(t < inherited ? t : 0xFF);
            }
            info.u1(publicMethodCount);
        }

        /**
         * class_ref (§6.8.1): the offset of an entry of this component for internal types (always
         * written before the entries that reference them), the package and class token otherwise.
         */
        private int classRef(String name) {
            Integer offset = written.get(name);
            if (offset != null) {
                return offset;
            }
            if (hierarchy.isInternal(name)) {
                throw new IllegalStateException(name + " is referenced before its own entry; classes must"
                        + " be in Class component order (JCVM 3.1 §6.9)");
            }
            return resolver.resolveClassRefDirect(name);
        }

        private static void requireAtMost(int count, int max, ClassInfo ci, String what) {
            if (count > max) {
                throw new IllegalStateException(ci.thisClass() + " has " + count + " " + what
                        + ", at most " + max + " are allowed (JCVM 3.1 §6.9.2.1)");
            }
        }
    }

    /**
     * Result of Class component generation.
     *
     * @param bytes        complete component bytes including tag and size
     * @param classOffsets offset of each entry within the component info item, in the order of
     *                     the class list passed to {@code generate} (Class component order)
     */
    public record ClassResult(byte[] bytes, int[] classOffsets) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof ClassResult(var b, var co)) {
                return Arrays.equals(bytes, b) && Arrays.equals(classOffsets, co);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(bytes) + Arrays.hashCode(classOffsets);
        }

        @Override
        public String toString() {
            return "ClassResult[bytes=" + HexFormat.of().formatHex(bytes)
                    + ", classOffsets=" + Arrays.toString(classOffsets) + "]";
        }
    }
}
