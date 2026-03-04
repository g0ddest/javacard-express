package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.resolve.ReferenceResolver;
import name.velikodniy.jcexpress.converter.token.TokenMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates the CAP Class component (tag 6) as defined in JCVM 3.0.5 spec section 6.8-6.9.
 *
 * <p>The Class component defines the class hierarchy for the entire package. It contains
 * two kinds of entries laid out sequentially: <em>interface_info</em> entries for
 * interfaces, followed by <em>class_info</em> entries for concrete and abstract classes.
 * Interfaces must precede classes in the component.
 *
 * <p>For each class, this component includes:
 * <ul>
 *   <li>Access flags and interface count</li>
 *   <li>Superclass reference (as a direct class_ref, not a CP index)</li>
 *   <li>Instance field size and reference-type field tracking (for garbage collection)</li>
 *   <li>Public and package-visible virtual method dispatch tables, containing offsets
 *       into the Method component for each method token</li>
 *   <li>Implemented interface mapping table, which maps each interface's method tokens
 *       to the class's corresponding virtual method tokens (for {@code invokeinterface})</li>
 * </ul>
 *
 * <p>The byte offset of each class within this component is significant: it serves as
 * the internal class reference used by the ConstantPool, Export, and Descriptor
 * components.
 *
 * <p><b>Current limitations:</b> Only methods declared or overridden in the class itself
 * are resolved in the dispatch tables; inherited virtual method entries are written as
 * {@code 0xFFFF}. Interface-to-method mapping count is currently 0 for interfaces with
 * no declared methods.
 *
 * <p>Binary format (JCVM 3.0.5 spec section 6.8-6.9):
 * <pre>
 * u1  tag = 6
 * u2  size
 * // For each interface (interface_info):
 *   u1  flags (bit 7=1 ACC_INTERFACE) | interface_count (bits 3..0)
 *   u2  superinterfaces[interface_count]   (direct class_ref encoding)
 * // For each class (class_info):
 *   u1  flags (bit 6=ACC_ABSTRACT) | interface_count (bits 3..0)
 *   u2  super_class_ref                    (direct class_ref, 0xFFFF for Object)
 *   u1  declared_instance_size             (in 2-byte cells)
 *   u1  first_reference_token
 *   u1  reference_count
 *   u1  public_method_table_base
 *   u1  public_method_table_count
 *   u2  public_method_table[count]         (offsets into Method component)
 *   u1  package_method_table_base
 *   u1  package_method_table_count
 *   u2  package_method_table[count]        (offsets into Method component)
 *   implemented_interface_info[interface_count]:
 *     u2  interface_ref                    (direct class_ref)
 *     u1  count                            (number of method mappings)
 *     u1  index[count]                     (interface token to class token mapping)
 * </pre>
 *
 * @see MethodComponent
 * @see DescriptorComponent
 * @see ExportComponent
 */
public final class ClassComponent {

    public static final int TAG = 6;

    private static final String JAVA_LANG_OBJECT = "java/lang/Object";

    private ClassComponent() {}

    /**
     * Generates the Class component bytes (format 2.1/2.2 default).
     *
     * @param classes          classes sorted in token order (interfaces first)
     * @param tokenMap         token assignments
     * @param methodOffsets    offsets of each method in the Method component (indexed by global method position)
     * @param methodIndexMap   maps "className:methodName:methodDesc" → global method index
     * @param resolver         reference resolver for superclass/interface CP refs
     * @return result containing component bytes and per-class byte offsets
     */
    public static ClassResult generate(List<ClassInfo> classes, TokenMap tokenMap,
                                        int[] methodOffsets,
                                        Map<String, Integer> methodIndexMap,
                                        ReferenceResolver resolver) {
        return generate(classes, tokenMap, methodOffsets, methodIndexMap, resolver, null, false);
    }

    /**
     * Generates the Class component bytes with explicit JavaCard version.
     *
     * <p>CAP format 2.3 (JC 3.1.0+) extends the Class component with:
     * <ul>
     *   <li>{@code u2 signature_pool_length} prepended before all class entries (0 for compact format)</li>
     *   <li>VMMT (Virtual Method Mapping Table) appended after each class_info, mapping
     *       virtual method tokens for superclass evolution compatibility</li>
     * </ul>
     *
     * @param classes          classes sorted in token order (interfaces first)
     * @param tokenMap         token assignments
     * @param methodOffsets    offsets of each method in the Method component
     * @param methodIndexMap   maps "className:methodName:methodDesc" → global method index
     * @param resolver         reference resolver for superclass/interface CP refs
     * @param version          target JavaCard version, null defaults to format 2.1
     * @param oracleCompat     when true, replicate Oracle's dispatch table off-by-one bug
     * @return result containing component bytes and per-class byte offsets
     */
    public static ClassResult generate(List<ClassInfo> classes, TokenMap tokenMap,
                                        int[] methodOffsets,
                                        Map<String, Integer> methodIndexMap,
                                        ReferenceResolver resolver,
                                        JavaCardVersion version,
                                        boolean oracleCompat) {
        boolean isFormat23 = version != null && version.formatMinor() >= 3;

        // --- class_component (§6.8 Table 6-14) ---
        var info = new BinaryWriter();

        // §6.8: format 2.3 prepends u2 signature_pool_length before class entries
        if (isFormat23) {
            info.u2(0); // signature_pool_length = 0 (compact format, no signature pool)
        }

        int[] classOffsets = new int[classes.size()];
        // Map class name → byte offset for internal super_class_ref resolution
        Map<String, Integer> classOffsetMap = new HashMap<>();

        for (int i = 0; i < classes.size(); i++) {
            classOffsets[i] = info.size();
            ClassInfo ci = classes.get(i);
            classOffsetMap.put(ci.thisClass(), classOffsets[i]);
            TokenMap.ClassEntry entry = tokenMap.findClass(ci.thisClass());

            if (ci.isInterface()) {
                writeInterface(info, ci, resolver);
            } else {
                writeClass(info, ci, entry, methodOffsets, methodIndexMap,
                        resolver, version, classes, classOffsetMap, oracleCompat);
            }
        }

        return new ClassResult(
                HeaderComponent.wrapComponent(TAG, info.toByteArray()),
                classOffsets);
    }

    private static void writeInterface(BinaryWriter info, ClassInfo ci,
                                        ReferenceResolver resolver) {
        // --- interface_info (§6.8 Table 6-15) ---
        int interfaceCount = ci.interfaces().size();
        // §6.8 Table 6-15: u1 bitfield — bit 7 = ACC_INTERFACE, bits 3..0 = interface_count
        info.u1(0x80 | (interfaceCount & 0x0F));

        // §6.8 Table 6-15: u2[] superinterfaces — direct class_ref encoding (not CP indices)
        for (String iface : ci.interfaces()) {
            int ifaceRef = resolver.resolveClassRefDirect(iface);
            info.u2(ifaceRef); // §6.8: u2 superinterface class_ref
        }
    }

    @SuppressWarnings("java:S3776") // Inherently complex class_info binary generation
    private static void writeClass(BinaryWriter info, ClassInfo ci,
                                    TokenMap.ClassEntry entry, int[] methodOffsets,
                                    Map<String, Integer> methodIndexMap,
                                    ReferenceResolver resolver,
                                    JavaCardVersion version,
                                    List<ClassInfo> allClasses,
                                    Map<String, Integer> classOffsetMap,
                                    boolean oracleCompat) {
        // --- class_info (§6.8 Table 6-16) ---
        int interfaceCount = ci.interfaces().size();
        // §6.8 Table 6-16: u1 bitfield — bits 3..0 = interface_count
        // bit 6 = ACC_SHAREABLE: set when class implements javacard.framework.Shareable
        int flags = interfaceCount & 0x0F;
        if (implementsShareable(ci, allClasses)) {
            flags |= 0x40; // ACC_SHAREABLE
        }
        info.u1(flags);

        // §6.8 Table 6-16: u2 super_class_ref — direct class_ref encoding (NOT a CP index!)
        // Internal: byte offset into ClassComponent info; External: (0x80|pkg_token)<<8|class_token
        if (ci.superClass() == null) {
            info.u2(0xFFFF); // §6.8: 0xFFFF = this class IS java.lang.Object
        } else if (JAVA_LANG_OBJECT.equals(ci.superClass())) {
            // External class ref to java.lang.Object
            int objectRef = resolver.resolveClassRefDirect(JAVA_LANG_OBJECT);
            info.u2(objectRef);
        } else {
            Integer internalOffset = classOffsetMap.get(ci.superClass());
            if (internalOffset != null) {
                // Internal superclass: use byte offset into ClassComponent info area
                info.u2(internalOffset);
            } else {
                // External superclass: use (0x80|pkg_token)<<8|class_token encoding
                int superRef = resolver.resolveClassRefDirect(ci.superClass());
                info.u2(superRef);
            }
        }

        // §6.8 Table 6-16: u1 declared_instance_size (in 2-byte cells)
        int instanceSize = entry.instanceFields().size();
        info.u1(instanceSize);

        // §6.8 Table 6-16: u1 first_reference_token, u1 reference_count
        // Tracks reference-type instance fields for GC root scanning
        int firstRefToken = 0xFF; // §6.8: 0xFF when no reference-type instance fields
        int refCount = 0;
        for (TokenMap.FieldEntry fe : entry.instanceFields()) {
            if (fe.descriptor().startsWith("L") || fe.descriptor().startsWith("[")) {
                if (refCount == 0) firstRefToken = fe.token();
                refCount++;
            }
        }
        info.u1(firstRefToken); // §6.8: u1 first_reference_token (0xFF if no refs)
        info.u1(refCount);      // §6.8: u1 reference_count

        // Build set of locally declared/overridden virtual methods
        Set<String> locallyDeclared = new HashSet<>();
        for (MethodInfo mi : ci.methods()) {
            if (!mi.isConstructor() && !mi.isStaticInitializer() && !mi.isStatic()) {
                locallyDeclared.add(mi.name() + ":" + mi.descriptor());
            }
        }

        // --- public_virtual_method_table (§6.8 Table 6-16) ---
        // §6.8: contiguous range from min to max locally declared/overridden token.
        // Inherited non-overridden methods within the range are included with their
        // implementation offset resolved by walking up the class hierarchy.
        List<TokenMap.MethodEntry> allVirtuals = entry.virtualMethods();
        int minLocalToken = Integer.MAX_VALUE;
        int maxLocalToken = -1;
        for (TokenMap.MethodEntry me : allVirtuals) {
            if (locallyDeclared.contains(me.name() + ":" + me.descriptor())) {
                minLocalToken = Math.min(minLocalToken, me.token());
                maxLocalToken = Math.max(maxLocalToken, me.token());
            }
        }

        // Tracks whether Oracle compat mode consumed the pkg_base/pkg_count bytes
        // by overflowing the last dispatch table entry into those positions.
        boolean pkgTableWritten = false;

        if (maxLocalToken < 0) {
            // No locally declared public virtual methods
            info.u1(0); // §6.8: u1 public_method_table_base
            info.u1(0); // §6.8: u1 public_method_table_count
        } else {
            info.u1(minLocalToken); // §6.8: u1 public_method_table_base
            int count = maxLocalToken - minLocalToken + 1;
            info.u1(count); // §6.8: u1 public_method_table_count

            if (oracleCompat) {
                // Oracle compatibility: replicate the off-by-one bug in
                // public_virtual_method_table serialization (see BINARY_COMPATIBILITY.md).
                // Oracle writes a phantom 0x0000 first, then all real entries. The last
                // real entry overflows into the package_method_table_base/count positions.
                // Total byte count is identical to spec-correct: 2*(count+1) = 2*count+2.
                info.u2(0x0000); // phantom entry
                for (int t = minLocalToken; t <= maxLocalToken; t++) {
                    int offset = resolveDispatchEntry(t, allVirtuals, ci, allClasses,
                            methodIndexMap, methodOffsets);
                    info.u2(offset);
                }
                pkgTableWritten = true; // last u2 occupies pkg_base/pkg_count positions
            } else {
                for (int t = minLocalToken; t <= maxLocalToken; t++) {
                    int offset = resolveDispatchEntry(t, allVirtuals, ci, allClasses,
                            methodIndexMap, methodOffsets);
                    info.u2(offset);
                }
            }
        }

        // --- package_method_table (§6.8 Table 6-16) ---
        if (!pkgTableWritten) {
            info.u1(0); // §6.8: u1 package_method_table_base
            info.u1(0); // §6.8: u1 package_method_table_count
        }

        // --- implemented_interface_info (§6.8 Table 6-17) ---
        // §6.8: interface_count entries, each maps interface method tokens to class tokens
        for (String iface : ci.interfaces()) {
            int ifaceRef = resolver.resolveClassRefDirect(iface);
            info.u2(ifaceRef); // §6.8 Table 6-17: u2 interface class_ref

            // §6.8 Table 6-17: u1 count + u1[] index (interface-to-class token mapping)
            List<TokenMap.MethodEntry> ifaceMethods = resolver.getInterfaceMethods(iface);
            if (ifaceMethods.isEmpty()) {
                info.u1(0); // §6.8: u1 count = 0 (no interface methods to map)
            } else {
                // count = max_token + 1 (tokens are 0-based, contiguous)
                int maxIfaceToken = ifaceMethods.stream()
                        .mapToInt(TokenMap.MethodEntry::token).max().orElse(-1);
                int mappingCount = maxIfaceToken + 1;
                info.u1(mappingCount); // §6.8: u1 count

                // For each interface method token, find the class's corresponding virtual token
                for (int t = 0; t < mappingCount; t++) {
                    final int ifaceToken = t;
                    TokenMap.MethodEntry ifaceMethod = ifaceMethods.stream()
                            .filter(m -> m.token() == ifaceToken)
                            .findFirst()
                            .orElse(null);

                    if (ifaceMethod != null) {
                        // §6.8 Table 6-17: u1 index — class virtual method token
                        String key = ifaceMethod.name() + ":" + ifaceMethod.descriptor();
                        TokenMap.MethodEntry classMethod = allVirtuals.stream()
                                .filter(m -> (m.name() + ":" + m.descriptor()).equals(key))
                                .findFirst()
                                .orElse(null);
                        info.u1(classMethod != null ? classMethod.token() : 0xFF);
                    } else {
                        info.u1(0xFF); // §6.8: gap in token numbering
                    }
                }
            }
        }

        // §6.8: CAP format 2.3 appends VMMT (Virtual Method Mapping Table) per class.
        // VMMT maps virtual method tokens for superclass evolution compatibility.
        // Each entry is u1: identity mapping for inherited methods, 0xFF for newly declared.
        if (version != null && version.formatMinor() >= 3) {
            int pubBase = (maxLocalToken < 0) ? 0 : minLocalToken;
            int pubCount = (maxLocalToken < 0) ? 0 : (maxLocalToken - minLocalToken + 1);
            // VMMT size = max(pub_base + pub_count, pkg_base + pkg_count) + 1
            // pkg_base and pkg_count are currently always 0
            int vmmtSize = pubBase + pubCount + 1;

            // Inherited count = superclass's virtual method count
            int inheritedCount;
            String superClass = ci.superClass();
            if (superClass == null || JAVA_LANG_OBJECT.equals(superClass)) {
                inheritedCount = 1; // Object has equals at token 0
            } else {
                inheritedCount = resolver.getVirtualMethodCount(superClass);
            }

            for (int i = 0; i < vmmtSize; i++) {
                if (i < inheritedCount || i == vmmtSize - 1) {
                    info.u1(i); // identity mapping (inherited or phantom slot)
                } else {
                    info.u1(0xFF); // newly declared virtual method
                }
            }
        }
    }

    /**
     * Checks whether a class directly or transitively implements javacard.framework.Shareable.
     * Walks both the interface hierarchy and the superclass chain.
     */
    @SuppressWarnings("java:S3776") // Recursive interface hierarchy traversal
    private static boolean implementsShareable(ClassInfo ci, List<ClassInfo> allClasses) {
        // Direct check
        for (String iface : ci.interfaces()) {
            if ("javacard/framework/Shareable".equals(iface)) return true;
            // Check if the interface itself extends Shareable (transitive)
            for (ClassInfo other : allClasses) {
                if (other.thisClass().equals(iface) && implementsShareable(other, allClasses)) {
                    return true;
                }
            }
        }
        // Check superclass chain
        if (ci.superClass() != null && !JAVA_LANG_OBJECT.equals(ci.superClass())) {
            for (ClassInfo sup : allClasses) {
                if (sup.thisClass().equals(ci.superClass())) {
                    return implementsShareable(sup, allClasses);
                }
            }
        }
        return false;
    }

    /**
     * Resolves a virtual method's offset in the Method component by walking up the
     * class hierarchy. For locally declared/overridden methods, the offset is found
     * in the current class. For inherited methods, the closest superclass that
     * declares or overrides the method is used.
     *
     * Resolves a single dispatch table entry for the given token.
     * Returns the method offset, or {@code 0xFFFF} for gaps in token numbering.
     */
    private static int resolveDispatchEntry(int token, List<TokenMap.MethodEntry> allVirtuals,
                                             ClassInfo ci, List<ClassInfo> allClasses,
                                             Map<String, Integer> methodIndexMap,
                                             int[] methodOffsets) {
        TokenMap.MethodEntry me = allVirtuals.stream()
                .filter(m -> m.token() == token)
                .findFirst()
                .orElse(null);
        if (me != null) {
            return resolveVirtualMethodOffset(
                    ci.thisClass(), me.name(), me.descriptor(),
                    allClasses, methodIndexMap, methodOffsets);
        }
        return 0xFFFF;
    }

    /**
     * @return method offset, or 0xFFFF if not found in any package class
     */
    private static int resolveVirtualMethodOffset(String className, String methodName,
                                                   String methodDesc,
                                                   List<ClassInfo> allClasses,
                                                   Map<String, Integer> methodIndexMap,
                                                   int[] methodOffsets) {
        String current = className;
        while (current != null) {
            String key = current + ":" + methodName + ":" + methodDesc;
            Integer idx = methodIndexMap.get(key);
            if (idx != null && idx < methodOffsets.length) {
                return methodOffsets[idx];
            }
            // Walk up to superclass
            String sup = null;
            for (ClassInfo ci : allClasses) {
                if (ci.thisClass().equals(current)) {
                    sup = ci.superClass();
                    break;
                }
            }
            current = sup;
        }
        return 0xFFFF;
    }

    /**
     * Result of Class component generation.
     *
     * @param bytes        complete component bytes including tag and size
     * @param classOffsets byte offset of each class within the component info area,
     *                     indexed by position in the sorted class list (= class token)
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
