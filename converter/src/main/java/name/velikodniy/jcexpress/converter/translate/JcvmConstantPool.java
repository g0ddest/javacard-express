package name.velikodniy.jcexpress.converter.translate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder for the JCVM constant pool used in the {@code constant_pool} component of a CAP file.
 *
 * <p>This class participates in <b>Stage 5: Bytecode Translation</b> and the subsequent
 * <b>Stage 6: CAP Assembly</b> of the converter pipeline. It is populated by
 * {@link name.velikodniy.jcexpress.converter.resolve.ReferenceResolver} during bytecode
 * translation (when resolving field, method, and class references) and later serialized by
 * {@link name.velikodniy.jcexpress.converter.cap.ConstantPoolComponent} into the binary
 * CAP format.
 *
 * <h2>Entry Format</h2>
 * <p>Each constant pool entry is exactly 4 bytes, as defined in JCVM 3.0.5 spec, section 6.8:
 * <pre>
 *   cp_info {
 *       u1 tag;    // entry type (1-6), see TAG_* constants
 *       u1 info[3]; // type-specific payload
 *   }
 * </pre>
 *
 * <h2>Internal vs. External References</h2>
 * <ul>
 *   <li><b>Internal references</b> (high bit of first info byte = 0): point to byte offsets
 *       within the current package's CAP components (e.g. Class component, Method component,
 *       Static Field component). These offsets may be placeholders initially and are patched
 *       via {@link #replaceEntry(int, CpEntry)} once component layout is finalized.</li>
 *   <li><b>External references</b> (high bit of first info byte = 1): use token-based encoding
 *       with imported package index, class token, and member token as defined in the export
 *       files of referenced packages (JCVM spec 6.8.1-6.8.6).</li>
 * </ul>
 *
 * <p>Entries are deduplicated: adding the same reference twice returns the same CP index.
 * After all references are collected, {@link #remapPackageTokens(Map)} can be called to
 * reassign external package tokens when unused imports are filtered out.
 *
 * @see name.velikodniy.jcexpress.converter.resolve.ReferenceResolver
 * @see name.velikodniy.jcexpress.converter.cap.ConstantPoolComponent
 * @see BytecodeTranslator
 */
public final class JcvmConstantPool {

    /** Tag for class reference entries (JCVM spec 6.8.1). */
    public static final int TAG_CLASSREF           = 1;
    /** Tag for instance field reference entries (JCVM spec 6.8.2). */
    public static final int TAG_INSTANCE_FIELDREF   = 2;
    /** Tag for virtual method reference entries (JCVM spec 6.8.3). */
    public static final int TAG_VIRTUAL_METHODREF   = 3;
    /** Tag for super method reference entries, used by {@code invokespecial} (JCVM spec 6.8.4). */
    public static final int TAG_SUPER_METHODREF     = 4;
    /** Tag for static field reference entries (JCVM spec 6.8.5). */
    public static final int TAG_STATIC_FIELDREF     = 5;
    /** Tag for static method reference entries (JCVM spec 6.8.6). */
    public static final int TAG_STATIC_METHODREF    = 6;

    private final List<CpEntry> entries = new ArrayList<>();
    private final Map<String, Integer> dedup = new HashMap<>();

    /**
     * Returns the number of entries currently in the constant pool.
     *
     * @return the entry count (0-based; the next added entry will have this index)
     */
    public int size() {
        return entries.size();
    }

    /**
     * Returns an unmodifiable snapshot of all constant pool entries in index order.
     *
     * @return immutable list of {@link CpEntry} instances
     */
    public List<CpEntry> entries() {
        return List.copyOf(entries);
    }

    /**
     * Replaces a CP entry at the given index with a new entry.
     * Used for deferred patching of internal references after component
     * offsets have been computed.
     *
     * @param index    the CP index to replace
     * @param newEntry the replacement entry
     */
    public void replaceEntry(int index, CpEntry newEntry) {
        entries.set(index, newEntry);
    }

    /**
     * Adds or reuses an external class reference.
     *
     * @param packageToken imported package index
     * @param classToken   class token within that package
     * @return CP index (0-based)
     */
    public int addExternalClassRef(int packageToken, int classToken) {
        String key = "EC:" + packageToken + ":" + classToken;
        return dedup.computeIfAbsent(key, k -> {
            int idx = entries.size();
            entries.add(new CpEntry(TAG_CLASSREF,
                    (byte) (packageToken | 0x80), (byte) classToken, (byte) 0));
            return idx;
        });
    }

    /**
     * Adds or reuses an internal class reference.
     *
     * @param classOffset offset into the Class component
     * @return CP index
     */
    public int addInternalClassRef(int classOffset) {
        String key = "IC:" + classOffset;
        return dedup.computeIfAbsent(key, k -> {
            int idx = entries.size();
            // JCVM spec 6.8.1 Table 6-18: internal = u2 internal_class_ref + u1 padding
            // Note: ClassRef byte order differs from StaticMethodRef/StaticFieldRef
            // which use u1 padding + u2 offset (spec 6.8.5/6.8.6)
            entries.add(new CpEntry(TAG_CLASSREF,
                    (byte) (classOffset >> 8), (byte) classOffset, (byte) 0));
            return idx;
        });
    }

    /**
     * Adds an internal virtual method reference using a class offset.
     *
     * @param classOffset byte offset into ClassComponent (or placeholder for deferred patching)
     * @param methodToken virtual method token
     * @return CP index
     */
    public int addVirtualMethodRef(int classOffset, int methodToken) {
        String key = "VM:" + classOffset + ":" + methodToken;
        return dedup.computeIfAbsent(key, k -> {
            int idx = entries.size();
            entries.add(new CpEntry(TAG_VIRTUAL_METHODREF,
                    (byte) (classOffset >> 8), (byte) classOffset, (byte) methodToken));
            return idx;
        });
    }

    /**
     * Adds an external virtual method reference using direct encoding.
     * Per JCVM spec 6.8.3, external virtual method refs encode package and class
     * tokens directly without an intermediate ClassRef entry.
     *
     * @param packageToken imported package index
     * @param classToken   class token within that package
     * @param methodToken  virtual method token
     * @return CP index
     */
    public int addExternalVirtualMethodRef(int packageToken, int classToken, int methodToken) {
        String key = "EVM:" + packageToken + ":" + classToken + ":" + methodToken;
        return dedup.computeIfAbsent(key, k -> {
            int idx = entries.size();
            entries.add(new CpEntry(TAG_VIRTUAL_METHODREF,
                    (byte) (packageToken | 0x80), (byte) classToken, (byte) methodToken));
            return idx;
        });
    }

    /**
     * Adds or reuses a super method reference for {@code invokespecial} calls
     * targeting a superclass method (JCVM spec 6.8.4).
     *
     * @param classRefIndex CP index of the class reference for the superclass
     * @param methodToken   virtual method token within that class
     * @return CP index
     */
    public int addSuperMethodRef(int classRefIndex, int methodToken) {
        String key = "SM:" + classRefIndex + ":" + methodToken;
        return dedup.computeIfAbsent(key, k -> {
            int idx = entries.size();
            entries.add(new CpEntry(TAG_SUPER_METHODREF,
                    (byte) (classRefIndex >> 8), (byte) classRefIndex, (byte) methodToken));
            return idx;
        });
    }

    /**
     * Adds or reuses an external static method reference (JCVM spec 6.8.6).
     * The high bit of {@code packageToken} is set automatically in the encoded entry.
     *
     * @param packageToken imported package index
     * @param classToken   class token within that package
     * @param methodToken  static method token within that class
     * @return CP index
     */
    public int addExternalStaticMethodRef(int packageToken, int classToken, int methodToken) {
        String key = "ESM:" + packageToken + ":" + classToken + ":" + methodToken;
        return dedup.computeIfAbsent(key, k -> {
            int idx = entries.size();
            entries.add(new CpEntry(TAG_STATIC_METHODREF,
                    (byte) (packageToken | 0x80), (byte) classToken, (byte) methodToken));
            return idx;
        });
    }

    /**
     * Adds an internal static method reference.
     *
     * @param methodOffset offset into the Method component
     */
    public int addInternalStaticMethodRef(int methodOffset) {
        String key = "ISM:" + methodOffset;
        return dedup.computeIfAbsent(key, k -> {
            int idx = entries.size();
            // JCVM spec 6.8.6 Table 6-26: internal = padding(0) + u2 offset
            entries.add(new CpEntry(TAG_STATIC_METHODREF,
                    (byte) 0, (byte) (methodOffset >> 8), (byte) methodOffset));
            return idx;
        });
    }

    /**
     * Adds an internal instance field reference using a class offset.
     *
     * @param classOffset byte offset into ClassComponent (or placeholder for deferred patching)
     * @param fieldToken  instance field token
     * @return CP index
     */
    public int addInstanceFieldRef(int classOffset, int fieldToken) {
        String key = "IF:" + classOffset + ":" + fieldToken;
        return dedup.computeIfAbsent(key, k -> {
            int idx = entries.size();
            entries.add(new CpEntry(TAG_INSTANCE_FIELDREF,
                    (byte) (classOffset >> 8), (byte) classOffset, (byte) fieldToken));
            return idx;
        });
    }

    /**
     * Adds an external instance field reference using direct encoding.
     * Per JCVM spec 6.8.2, external instance field refs encode package and class
     * tokens directly without an intermediate ClassRef entry.
     *
     * @param packageToken imported package index
     * @param classToken   class token within that package
     * @param fieldToken   instance field token
     * @return CP index
     */
    public int addExternalInstanceFieldRef(int packageToken, int classToken, int fieldToken) {
        String key = "EIF:" + packageToken + ":" + classToken + ":" + fieldToken;
        return dedup.computeIfAbsent(key, k -> {
            int idx = entries.size();
            entries.add(new CpEntry(TAG_INSTANCE_FIELDREF,
                    (byte) (packageToken | 0x80), (byte) classToken, (byte) fieldToken));
            return idx;
        });
    }

    /**
     * Adds or reuses an external static field reference (JCVM spec 6.8.5).
     * The high bit of {@code packageToken} is set automatically in the encoded entry.
     *
     * @param packageToken imported package index
     * @param classToken   class token within that package
     * @param fieldToken   static field token within that class
     * @return CP index
     */
    public int addExternalStaticFieldRef(int packageToken, int classToken, int fieldToken) {
        String key = "ESF:" + packageToken + ":" + classToken + ":" + fieldToken;
        return dedup.computeIfAbsent(key, k -> {
            int idx = entries.size();
            entries.add(new CpEntry(TAG_STATIC_FIELDREF,
                    (byte) (packageToken | 0x80), (byte) classToken, (byte) fieldToken));
            return idx;
        });
    }

    /**
     * Adds or reuses an internal static field reference (JCVM spec 6.8.5).
     *
     * @param fieldOffset byte offset into the Static Field component image
     * @return CP index
     */
    public int addInternalStaticFieldRef(int fieldOffset) {
        String key = "ISF:" + fieldOffset;
        return dedup.computeIfAbsent(key, k -> {
            int idx = entries.size();
            // JCVM spec 6.8.5 Table 6-24: internal = padding(0) + u2 offset
            entries.add(new CpEntry(TAG_STATIC_FIELDREF,
                    (byte) 0, (byte) (fieldOffset >> 8), (byte) fieldOffset));
            return idx;
        });
    }

    /**
     * Reorders CP entries so that instance field references ({@link #TAG_INSTANCE_FIELDREF})
     * appear first, sorted by class token order, followed by all remaining entries in their
     * original order.
     *
     * <p>This matches Oracle's converter ordering, which pre-scans declared instance fields
     * in class token order before processing bytecode, resulting in instance field refs
     * appearing at the beginning of the constant pool sorted by declaring class token.
     * Our converter adds CP entries in bytecode-encounter order, so this method is called
     * after all bytecode translation is complete to reorder entries.
     *
     * <p>The returned array maps old CP indices to new CP indices. Callers must use this
     * mapping to patch all CP index references in translated bytecode.
     *
     * @param fieldClassTokens mapping from CP index to class token for internal instance
     *                         field refs (from {@link name.velikodniy.jcexpress.converter.resolve.ReferenceResolver#getInstanceFieldClassTokens()}).
     *                         External instance field refs (not in this map) sort after
     *                         internal ones, preserving their original order.
     * @return mapping where {@code result[oldIndex] = newIndex}; empty array if no reordering needed
     */
    public int[] reorderInstanceFieldsFirst(Map<Integer, Integer> fieldClassTokens) {
        // Partition: instance field refs first, then everything else
        List<Integer> fieldIndices = new ArrayList<>();
        List<Integer> otherIndices = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).tag() == TAG_INSTANCE_FIELDREF) {
                fieldIndices.add(i);
            } else {
                otherIndices.add(i);
            }
        }

        // Sort field refs by class token (internal refs first by token, then external refs)
        fieldIndices.sort((a, b) -> {
            Integer tokenA = fieldClassTokens.get(a);
            Integer tokenB = fieldClassTokens.get(b);
            // Internal refs (have class token) sort before external refs (no class token)
            if (tokenA != null && tokenB != null) return Integer.compare(tokenA, tokenB);
            if (tokenA != null) return -1;
            if (tokenB != null) return 1;
            return Integer.compare(a, b); // preserve original order for externals
        });

        // Check if entries are already in the desired order at the front
        boolean alreadyOrdered = true;
        for (int i = 0; i < fieldIndices.size(); i++) {
            if (fieldIndices.get(i) != i) {
                alreadyOrdered = false;
                break;
            }
        }
        if (alreadyOrdered) {
            return new int[0];
        }

        // Build old→new index mapping
        int[] remap = new int[entries.size()];
        List<Integer> newOrder = new ArrayList<>(fieldIndices);
        newOrder.addAll(otherIndices);
        for (int newIdx = 0; newIdx < newOrder.size(); newIdx++) {
            remap[newOrder.get(newIdx)] = newIdx;
        }

        // Reorder entries
        List<CpEntry> reordered = new ArrayList<>(entries.size());
        for (int oldIdx : newOrder) {
            reordered.add(entries.get(oldIdx));
        }
        entries.clear();
        entries.addAll(reordered);

        // Dedup map no longer needed — no more entries will be added after reordering
        dedup.clear();

        return remap;
    }

    /**
     * Remaps external package tokens in all CP entries.
     * Used after filtering unused imports to reassign contiguous tokens.
     *
     * @param remap mapping from old package token to new package token
     */
    public void remapPackageTokens(Map<Integer, Integer> remap) {
        for (int i = 0; i < entries.size(); i++) {
            CpEntry e = entries.get(i);
            int b1 = e.b1() & 0xFF;
            if ((b1 & 0x80) != 0) {
                int oldToken = b1 & 0x7F;
                Integer newToken = remap.get(oldToken);
                if (newToken != null && newToken != oldToken) {
                    entries.set(i, new CpEntry(e.tag(),
                            (byte) (newToken | 0x80), e.b2(), e.b3()));
                }
            }
        }
    }

    /**
     * A single 4-byte constant pool entry as defined in JCVM 3.0.5 spec, section 6.8.
     *
     * <p>The layout is: 1-byte tag ({@link #tag}) followed by 3 payload bytes
     * ({@link #b1}, {@link #b2}, {@link #b3}). For external references, the high bit
     * of {@code b1} is set to 1, and the lower 7 bits encode the imported package index.
     * For internal references, the high bit of {@code b1} is 0, and {@code b2}/{@code b3}
     * form a 16-bit offset into the relevant CAP component.
     *
     * @param tag entry type tag (one of the {@code TAG_*} constants)
     * @param b1  first payload byte (high bit distinguishes internal/external)
     * @param b2  second payload byte
     * @param b3  third payload byte
     */
    public record CpEntry(int tag, byte b1, byte b2, byte b3) {
        /**
         * Serializes this entry to its 4-byte binary representation suitable
         * for direct inclusion in the CAP file's constant pool component.
         *
         * @return a 4-byte array: {@code [tag, b1, b2, b3]}
         */
        public byte[] toBytes() {
            return new byte[]{(byte) tag, b1, b2, b3};
        }
    }
}
