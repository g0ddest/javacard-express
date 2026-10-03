package name.velikodniy.jcexpress.converter.cap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code type_descriptor_info} item at the end of the Descriptor component
 * (JCVM 3.1 §6.14.5): one type offset per constant pool entry followed by the deduplicated
 * {@code type_descriptor} structures (§6.9.1) of all field types and method signatures.
 *
 * <p>Descriptors are registered first; {@link #seal()} then fixes their offsets, which are
 * counted from the start of {@code type_descriptor_info}.
 */
final class TypeDescriptorTable {

    /** constant_pool_types value of an entry without a type (a class reference). */
    static final int NO_TYPE = 0xFFFF;

    private final int cpCount;
    private final Map<String, int[]> descriptors = new LinkedHashMap<>();
    private final Map<Integer, String> cpTypes = new HashMap<>();
    private final Map<String, Integer> offsets = new HashMap<>();

    /**
     * Creates an empty table.
     *
     * @param cpCount number of constant pool entries
     */
    TypeDescriptorTable(int cpCount) {
        this.cpCount = cpCount;
    }

    /** Registers a type descriptor (no effect if an equal one is already registered). */
    void register(int[] nibbles) {
        descriptors.putIfAbsent(key(nibbles), nibbles);
    }

    /** Registers the type of a constant pool entry. */
    void registerCpType(int cpIndex, int[] nibbles) {
        register(nibbles);
        cpTypes.put(cpIndex, key(nibbles));
    }

    /** Assigns the offsets of the registered descriptors; call once after registration. */
    void seal() {
        int offset = 2 + 2 * cpCount;
        for (var e : descriptors.entrySet()) {
            offsets.put(e.getKey(), offset);
            offset += 1 + (e.getValue().length + 1) / 2;
        }
    }

    /**
     * Returns the offset of a registered descriptor.
     *
     * @param nibbles the descriptor
     * @return offset into type_descriptor_info
     * @throws IllegalStateException if the descriptor was not registered
     */
    int offsetOf(int[] nibbles) {
        Integer offset = offsets.get(key(nibbles));
        if (offset == null) {
            throw new IllegalStateException("Type descriptor not registered: " + Arrays.toString(nibbles));
        }
        return offset;
    }

    /** Serializes type_descriptor_info (§6.14.5). */
    byte[] toBytes() {
        var out = new BinaryWriter();
        out.u2(cpCount); // constant_pool_count
        for (int i = 0; i < cpCount; i++) {
            String key = cpTypes.get(i);
            out.u2(key == null ? NO_TYPE : offsets.get(key)); // constant_pool_types[i]
        }
        for (int[] nibbles : descriptors.values()) {
            out.u1(nibbles.length); // nibble_count
            for (int i = 0; i < nibbles.length; i += 2) {
                int low = i + 1 < nibbles.length ? nibbles[i + 1] : 0; // odd count: padding nibble 0
                out.u1((nibbles[i] << 4) | (low & 0x0F));
            }
        }
        return out.toByteArray();
    }

    private static String key(int[] nibbles) {
        return Arrays.toString(nibbles);
    }
}
