package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.translate.JcvmConstantPool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * Constant pool entries that refer to items of the package being converted and therefore hold
 * a placeholder until the Class, Method and Static Field components are laid out.
 *
 * <p>Internal references are offsets into those components (JCVM 3.1 §6.8.1 - §6.8.3), which are
 * only known after the components have been generated. Every placeholder is recorded here and
 * replaced by {@link #apply}; an entry whose target has no offset is an error, never left in the
 * CAP file.
 */
final class InternalRefPatches {

    /** First placeholder value; placeholders are unique so that equal entries are not merged. */
    private static final int FIRST_PLACEHOLDER = 0x7F00;

    /** Kind of internal reference, which determines the layout of the patched entry. */
    enum Kind {
        /** CONSTANT_Classref: Class component offset + padding (§6.8.1). */
        CLASS,
        /** CONSTANT_StaticMethodref: padding + Method component offset (§6.8.3). */
        STATIC_METHOD,
        /** CONSTANT_StaticFieldref: padding + static field image offset (§6.8.3). */
        STATIC_FIELD,
        /** CONSTANT_InstanceFieldref: declaring class offset + instance field token (§6.8.2). */
        INSTANCE_FIELD,
        /** CONSTANT_VirtualMethodref: declaring class offset + virtual method token (§6.8.2). */
        VIRTUAL_METHOD,
        /**
         * CONSTANT_SuperMethodref: offset of the class containing the super invocation + virtual
         * method token in the hierarchy of its superclass (§6.8.2).
         */
        SUPER_METHOD
    }

    /**
     * One deferred patch.
     *
     * @param cpIndex    constant pool index holding the placeholder
     * @param kind       reference kind
     * @param className  internal name of the class the entry refers to (for SUPER_METHOD: the
     *                   class containing the super invocation)
     * @param memberName field or method name ({@code null} for CLASS)
     * @param memberDesc method descriptor ({@code null} for CLASS and fields)
     * @param token      token written into the patched entry (instance field, virtual or super
     *                   method token; unused otherwise)
     */
    record Patch(int cpIndex, Kind kind, String className, String memberName, String memberDesc, int token) {}

    private final List<Patch> patches = new ArrayList<>();
    private int nextPlaceholder = FIRST_PLACEHOLDER;

    /** Returns a fresh placeholder value for a new constant pool entry. */
    int nextPlaceholder() {
        return nextPlaceholder++;
    }

    /** Records a deferred patch. */
    void add(Patch patch) {
        patches.add(patch);
    }

    /**
     * Updates the recorded constant pool indices after the pool was reordered.
     *
     * @param remap old index to new index
     */
    void remap(int[] remap) {
        patches.replaceAll(p -> new Patch(remap[p.cpIndex()], p.kind(), p.className(), p.memberName(),
                p.memberDesc(), p.token()));
    }

    /**
     * Returns a sort key for every internal CONSTANT_InstanceFieldref entry, keyed by CP index.
     *
     * @param classKey sort key of a class of the package
     * @return CP index to the sort key of the class declaring the field
     */
    Map<Integer, Integer> instanceFieldKeys(ToIntFunction<String> classKey) {
        Map<Integer, Integer> result = new HashMap<>();
        for (Patch p : patches) {
            if (p.kind() == Kind.INSTANCE_FIELD) {
                result.put(p.cpIndex(), classKey.applyAsInt(p.className()));
            }
        }
        return result;
    }

    /**
     * Replaces every placeholder with its final value.
     *
     * @param cp                 the constant pool
     * @param classOffsets       internal class name to Class component offset
     * @param methodOffsets      {@code "class:name:descriptor"} to Method component offset
     * @param staticFieldOffsets {@code "class:field"} to static field image offset
     * @throws IllegalStateException if a referenced item has no offset (the reference cannot be
     *                               represented in the CAP file)
     */
    void apply(JcvmConstantPool cp, Map<String, Integer> classOffsets, Map<String, Integer> methodOffsets,
               Map<String, Integer> staticFieldOffsets) {
        for (Patch p : patches) {
            cp.replaceEntry(p.cpIndex(), switch (p.kind()) {
                case CLASS -> entry(JcvmConstantPool.TAG_CLASSREF, offset(classOffsets, p.className(), p), 0, false);
                case STATIC_METHOD -> entry(JcvmConstantPool.TAG_STATIC_METHODREF,
                        offset(methodOffsets, p.className() + ":" + p.memberName() + ":" + p.memberDesc(), p), 0, true);
                case STATIC_FIELD -> entry(JcvmConstantPool.TAG_STATIC_FIELDREF,
                        offset(staticFieldOffsets, p.className() + ":" + p.memberName(), p), 0, true);
                case INSTANCE_FIELD -> entry(JcvmConstantPool.TAG_INSTANCE_FIELDREF,
                        offset(classOffsets, p.className(), p), p.token(), false);
                case VIRTUAL_METHOD -> entry(JcvmConstantPool.TAG_VIRTUAL_METHODREF,
                        offset(classOffsets, p.className(), p), p.token(), false);
                case SUPER_METHOD -> entry(JcvmConstantPool.TAG_SUPER_METHODREF,
                        offset(classOffsets, p.className(), p), p.token(), false);
            });
        }
    }

    /**
     * Builds a patched entry. Class-based entries are {@code u2 class_ref, u1 token/padding}
     * (§6.8.1, §6.8.2); internal static references are {@code u1 padding, u2 offset} (§6.8.3).
     */
    private static JcvmConstantPool.CpEntry entry(int tag, int offset, int token, boolean paddingFirst) {
        int max = paddingFirst ? 0xFFFF : 0x7FFF; // internal_class_ref is 0..32767 (§6.8.1)
        if (offset > max) {
            throw new IllegalStateException("Offset " + offset + " does not fit the internal reference of"
                    + " constant pool tag " + tag + " (maximum " + max + ", JCVM 3.1 §6.8)");
        }
        if (paddingFirst) {
            return new JcvmConstantPool.CpEntry(tag, (byte) 0, (byte) (offset >> 8), (byte) offset);
        }
        return new JcvmConstantPool.CpEntry(tag, (byte) (offset >> 8), (byte) offset, (byte) token);
    }

    private static int offset(Map<String, Integer> offsets, String key, Patch p) {
        Integer offset = offsets.get(key);
        if (offset == null) {
            throw new IllegalStateException("Internal " + p.kind() + " reference to " + key
                    + " (constant pool entry " + p.cpIndex() + ") has no location in the CAP file;"
                    + " the referenced item is not part of the converted package (JCVM 3.1 §6.8)");
        }
        return offset;
    }
}
