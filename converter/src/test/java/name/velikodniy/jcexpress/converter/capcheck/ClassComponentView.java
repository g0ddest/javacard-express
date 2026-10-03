package name.velikodniy.jcexpress.converter.capcheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Test-only parser of the Class component using exactly the layout of JCVM 3.1 §6.9:
 * {@code interface_info} and {@code class_info_compact} (§6.9.2), including the CAP 2.2
 * signature pool prefix and the CAP 2.3 {@code public_virtual_method_token_mapping}.
 *
 * <p>Remote classes/interfaces ({@code ACC_REMOTE}) are rejected: the converter does not
 * support Java Card RMI.
 *
 * @param signaturePoolLength value of {@code signature_pool_length} (0 when absent)
 * @param entries             interface and class entries in component order
 */
public record ClassComponentView(int signaturePoolLength, List<TypeEntry> entries) {

    /** interface_info / class_info flag ACC_INTERFACE (bit 7 of the bitfield). */
    public static final int ACC_INTERFACE = 0x80;
    /** interface_info / class_info flag ACC_SHAREABLE (bit 6 of the bitfield). */
    public static final int ACC_SHAREABLE = 0x40;
    /** interface_info / class_info flag ACC_REMOTE (bit 5 of the bitfield). */
    public static final int ACC_REMOTE = 0x20;

    static ClassComponentView parse(byte[] b, boolean signaturePool, boolean format23) {
        int o = 0;
        int poolLength = 0;
        if (signaturePool) {
            poolLength = CapImage.u2(b, 0);
            o = 2 + poolLength;
        }
        List<TypeEntry> entries = new ArrayList<>();
        while (o < b.length) {
            int bitfield = CapImage.u1(b, o);
            if ((bitfield & ACC_REMOTE) != 0) {
                throw new IllegalStateException("ACC_REMOTE entries are not supported at offset " + o);
            }
            TypeEntry entry = (bitfield & ACC_INTERFACE) != 0
                    ? parseInterface(b, o)
                    : parseClass(b, o, format23);
            entries.add(entry);
            o = entry.end();
        }
        if (o != b.length) {
            throw new IllegalStateException("Class component overrun: parsed to " + o + " of " + b.length);
        }
        return new ClassComponentView(poolLength, List.copyOf(entries));
    }

    private static InterfaceEntry parseInterface(byte[] b, int start) {
        int bitfield = CapImage.u1(b, start);
        int count = bitfield & 0x0F;
        List<Integer> supers = new ArrayList<>(count);
        int o = start + 1;
        for (int i = 0; i < count; i++, o += 2) {
            supers.add(CapImage.u2(b, o));
        }
        return new InterfaceEntry(start, o, bitfield & 0xF0, List.copyOf(supers));
    }

    private static ClassEntry parseClass(byte[] b, int start, boolean format23) {
        int bitfield = CapImage.u1(b, start);
        int o = start + 1;
        int superRef = CapImage.u2(b, o);
        int[] header = new int[7];
        for (int i = 0; i < header.length; i++) {
            header[i] = CapImage.u1(b, o + 2 + i);
        }
        o += 9;
        List<Integer> pubTable = readU2List(b, o, header[4]);
        o += 2 * header[4];
        List<Integer> pkgTable = readU2List(b, o, header[6]);
        o += 2 * header[6];
        List<ImplementedInterface> interfaces = new ArrayList<>();
        for (int i = 0; i < (bitfield & 0x0F); i++) {
            int ref = CapImage.u2(b, o);
            int count = CapImage.u1(b, o + 2);
            List<Integer> index = new ArrayList<>(count);
            for (int k = 0; k < count; k++) {
                index.add(CapImage.u1(b, o + 3 + k));
            }
            interfaces.add(new ImplementedInterface(ref, List.copyOf(index)));
            o += 3 + count;
        }
        List<Integer> mapping = List.of();
        int cap22Count = -1;
        if (format23) {
            int n = header[3] + header[4];
            List<Integer> m = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                m.add(CapImage.u1(b, o + i));
            }
            mapping = List.copyOf(m);
            cap22Count = CapImage.u1(b, o + n);
            o += n + 1;
        }
        return new ClassEntry(start, o, bitfield & 0xF0, superRef, header[0], header[1], header[2],
                header[3], header[4], header[5], header[6], pubTable, pkgTable,
                List.copyOf(interfaces), mapping, cap22Count);
    }

    private static List<Integer> readU2List(byte[] b, int o, int count) {
        List<Integer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(CapImage.u2(b, o + 2 * i));
        }
        return List.copyOf(list);
    }

    /**
     * Finds the entry that starts exactly at the given offset of the component info item.
     *
     * @param offset internal class_ref value
     * @return the entry, or empty if no entry starts there
     */
    public Optional<TypeEntry> at(int offset) {
        return entries.stream().filter(e -> e.offset() == offset).findFirst();
    }

    /** @return class entries (class_info) in component order */
    public List<ClassEntry> classes() {
        return entries.stream().filter(ClassEntry.class::isInstance).map(ClassEntry.class::cast).toList();
    }

    /** @return interface entries (interface_info) in component order */
    public List<InterfaceEntry> interfaces() {
        return entries.stream().filter(InterfaceEntry.class::isInstance)
                .map(InterfaceEntry.class::cast).toList();
    }

    /** An interface_info or class_info entry. */
    public sealed interface TypeEntry permits InterfaceEntry, ClassEntry {
        /** @return offset of the entry in the component info item (its internal class_ref) */
        int offset();

        /** @return offset just after the entry */
        int end();

        /** @return flags nibble shifted into bits 7..4 (ACC_INTERFACE/ACC_SHAREABLE/ACC_REMOTE) */
        int flags();

        /** @return {@code true} if ACC_SHAREABLE is set */
        default boolean isShareable() {
            return (flags() & ACC_SHAREABLE) != 0;
        }
    }

    /**
     * interface_info (§6.9.2.2).
     *
     * @param offset          offset in the component info item
     * @param end             offset after the entry
     * @param flags           flags (bits 7..4)
     * @param superinterfaces superinterface class_refs (direct and indirect)
     */
    public record InterfaceEntry(int offset, int end, int flags, List<Integer> superinterfaces)
            implements TypeEntry {}

    /**
     * class_info_compact (§6.9.2.3).
     *
     * @param offset               offset in the component info item
     * @param end                  offset after the entry
     * @param flags                flags (bits 7..4)
     * @param superClassRef        super_class_ref (0xFFFF for no superclass)
     * @param declaredInstanceSize declared_instance_size in 16-bit cells
     * @param firstReferenceToken  first_reference_token (0xFF if none)
     * @param referenceCount       reference_count
     * @param publicBase           public_method_table_base
     * @param publicCount          public_method_table_count
     * @param packageBase          package_method_table_base
     * @param packageCount         package_method_table_count
     * @param publicTable          public_virtual_method_table
     * @param packageTable         package_virtual_method_table
     * @param interfaces           implemented_interface_info entries
     * @param tokenMapping         public_virtual_method_token_mapping (CAP 2.3 only, else empty)
     * @param cap22InheritableCount CAP22_inheritable_public_method_token_count (CAP 2.3, else -1)
     */
    public record ClassEntry(int offset, int end, int flags, int superClassRef,
                             int declaredInstanceSize, int firstReferenceToken, int referenceCount,
                             int publicBase, int publicCount, int packageBase, int packageCount,
                             List<Integer> publicTable, List<Integer> packageTable,
                             List<ImplementedInterface> interfaces, List<Integer> tokenMapping,
                             int cap22InheritableCount) implements TypeEntry {}

    /**
     * implemented_interface_info (§6.9.2.5).
     *
     * @param interfaceRef class_ref of the interface
     * @param index        interface method token to class virtual method token mapping
     */
    public record ImplementedInterface(int interfaceRef, List<Integer> index) {}
}
