package name.velikodniy.jcexpress.converter.input;

/**
 * Parsed representation of a single field declaration extracted from a JVM
 * {@code .class} file.
 *
 * <h2>Converter Pipeline: Stage 1 (Load)</h2>
 * <p>Each {@code FieldInfo} is produced by {@link ClassFileReader} when parsing
 * a class file's field table. It captures the field's name, JVM type descriptor,
 * access flags, and optional compile-time constant value.
 *
 * <h2>Usage in Later Stages</h2>
 * <ul>
 *   <li><strong>Stage 2 (Subset Check):</strong> The field descriptor is inspected
 *       for forbidden types ({@code long}, {@code float}, {@code double}).</li>
 *   <li><strong>Stage 3 (Token Assignment):</strong> Instance fields receive
 *       instance field tokens (0-based per class), and static fields receive
 *       static field tokens (0-based per class).</li>
 *   <li><strong>Stage 6 (CAP Generation):</strong> Static fields are laid out
 *       in the Static Field Component (JCVM 3.0.5 spec Section 6.11), while
 *       instance field references are resolved through the Constant Pool
 *       Component (Section 6.8).</li>
 * </ul>
 *
 * @param name          field name (e.g. {@code "balance"}, {@code "MAX_SIZE"})
 * @param descriptor    JVM type descriptor (e.g. {@code "S"} for {@code short},
 *                      {@code "[B"} for {@code byte[]},
 *                      {@code "Ljavacard/framework/APDU;"} for an object reference)
 * @param accessFlags   JVM access flags bitmask ({@code ACC_PUBLIC},
 *                      {@code ACC_STATIC}, {@code ACC_FINAL}, etc.)
 * @param constantValue the compile-time constant value from the {@code ConstantValue}
 *                      attribute for {@code static final} primitives and strings,
 *                      or {@code null} if not present
 * @see ClassFileReader
 * @see ClassInfo
 */
public record FieldInfo(
        String name,
        String descriptor,
        int accessFlags,
        Object constantValue
) {
    /**
     * Returns {@code true} if this field is static ({@code ACC_STATIC} flag is set).
     *
     * <p>Static fields are stored in the Static Field Component of the CAP file
     * (JCVM 3.0.5 spec Section 6.11), whereas instance fields are stored
     * within their owning object on the card.
     *
     * @return {@code true} if the {@code ACC_STATIC} flag (0x0008) is set
     */
    public boolean isStatic() {
        return (accessFlags & 0x0008) != 0;
    }

    /**
     * Returns {@code true} if this field is final ({@code ACC_FINAL} flag is set).
     *
     * <p>Static final fields with primitive or string constant values may have
     * their values inlined at compile time. The {@link #constantValue()} will
     * be non-null in that case.
     *
     * @return {@code true} if the {@code ACC_FINAL} flag (0x0010) is set
     */
    public boolean isFinal() {
        return (accessFlags & 0x0010) != 0;
    }

    /**
     * Returns {@code true} if this field is a compile-time constant that gets
     * inlined by javac and should be excluded from the CAP file's static field
     * image and descriptor table.
     *
     * <p>A compile-time constant is a {@code static final} field with a
     * {@code ConstantValue} attribute whose type is a primitive (not an object
     * reference or array). Javac inlines such constants at all use sites, so
     * they do not need storage in the static field image (JCVM 3.0.5 Section 6.10)
     * or an entry in the Descriptor component (Section 6.14).
     *
     * @return {@code true} if this field is a compile-time constant
     */
    public boolean isCompileTimeConstant() {
        return isStatic() && isFinal() && constantValue() != null
                && !descriptor().startsWith("L") && !descriptor().startsWith("[");
    }
}
