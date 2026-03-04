package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Generates the CAP StaticField component (tag 8) as defined in JCVM 3.0.5 spec section 6.10.
 *
 * <p>The StaticField component contains the initialization image for all static fields
 * in the package. When the JCVM loads a package, it copies this image into the
 * persistent (EEPROM) static field area, providing initial values for all static
 * fields without requiring {@code <clinit>} execution for simple cases.
 *
 * <p>Fields are organized into segments in a specific order mandated by the spec:
 * <ol>
 *   <li><b>Segment 1:</b> Reference fields initialized to arrays (from array_init).
 *       Not yet implemented in this converter.</li>
 *   <li><b>Segment 2:</b> Other reference fields (initialized to {@code null} = 2 zero
 *       bytes each, since references are 2 bytes on the JCVM)</li>
 *   <li><b>Segment 3:</b> Primitive fields with default (zero) values (size depends on type:
 *       1 byte for byte/boolean, 2 bytes for short/char, 4 bytes for int)</li>
 *   <li><b>Segment 4:</b> Primitive fields with explicit non-default initial values
 *       (from {@code ConstantValue} attributes in the class file)</li>
 * </ol>
 *
 * <p>Compile-time constants ({@code static final} primitives with a {@code ConstantValue}
 * attribute) are excluded from the image because javac inlines them at all use sites.
 *
 * <p>The component also tracks a {@code fieldOffsetMap} that maps each static field
 * (by {@code "className:fieldName"}) to its byte offset within the image, which is
 * needed by the ConstantPool component for {@code CONSTANT_StaticFieldref} entries
 * and by the Export component for exported static field offsets.
 *
 * <p>Binary format (JCVM 3.0.5 spec section 6.10, Table 6-10):
 * <pre>
 * u1  tag = 8
 * u2  size
 * u2  image_size              (total bytes in the static field image)
 * u2  reference_count         (number of reference-type fields)
 * u2  array_init_count        (number of array initializers; currently 0)
 * u2  default_value_count     (bytes of default-value primitive fields)
 * u2  non_default_value_count (bytes of non-default primitive init data)
 * u1  non_default_values[]    (explicit initial values for Segment 4 fields)
 * </pre>
 *
 * @see ExportComponent
 * @see ConstantPoolComponent
 * @see DirectoryComponent
 */
public final class StaticFieldComponent {

    public static final int TAG = 8;

    private StaticFieldComponent() {}

    /**
     * Generates the StaticField component bytes.
     *
     * @param classes all classes in the package
     * @return result containing component bytes and image statistics
     */
    @SuppressWarnings("java:S3776") // Inherently complex static field image generation
    public static StaticFieldResult generate(List<ClassInfo> classes) {
        // Track field offsets within the static field image.
        // §6.10: Image layout segments (in order):
        //   Segment 1: array-init reference fields (not yet implemented)
        //   Segment 2: other reference fields (2 bytes each, null = 0x0000)
        //   Segment 3: default-value primitive fields (zero-initialized)
        //   Segment 4: non-default-value primitive fields (explicit values)
        Map<String, Integer> fieldOffsetMap = new HashMap<>();

        // First pass: count segments and track per-field assignment
        int referenceCount = 0;
        int defaultValueCount = 0;
        var nonDefaultValues = new BinaryWriter();

        // Track fields for offset computation (separate lists per segment)
        record FieldRef(String className, String fieldName, int segmentOffset) {}
        var refFields = new ArrayList<FieldRef>();
        var defaultFields = new ArrayList<FieldRef>();
        var nonDefaultFields = new ArrayList<FieldRef>();

        for (ClassInfo ci : classes) {
            for (FieldInfo fi : ci.fields()) {
                if (!fi.isStatic()) continue;

                // Skip compile-time constants — javac inlines these at use sites,
                // so they don't need storage in the static field image (JCVM 3.0.5 §6.10).
                if (fi.isCompileTimeConstant()) continue;

                String desc = fi.descriptor();
                if (desc.startsWith("L") || desc.startsWith("[")) {
                    refFields.add(new FieldRef(ci.thisClass(), fi.name(), referenceCount * 2));
                    referenceCount++;
                } else if (fi.constantValue() != null) {
                    nonDefaultFields.add(new FieldRef(ci.thisClass(), fi.name(), nonDefaultValues.size()));
                    writeConstantValue(nonDefaultValues, desc, fi.constantValue());
                } else {
                    defaultFields.add(new FieldRef(ci.thisClass(), fi.name(), defaultValueCount));
                    defaultValueCount += fieldSize(desc);
                }
            }
        }

        byte[] nonDefaults = nonDefaultValues.toByteArray();
        int imageSize = (referenceCount * 2) + defaultValueCount + nonDefaults.length;

        // Compute absolute offsets within the image
        int refBase = 0;
        int defaultBase = referenceCount * 2;
        int nonDefaultBase = defaultBase + defaultValueCount;

        for (FieldRef f : refFields) {
            fieldOffsetMap.put(f.className + ":" + f.fieldName, refBase + f.segmentOffset);
        }
        for (FieldRef f : defaultFields) {
            fieldOffsetMap.put(f.className + ":" + f.fieldName, defaultBase + f.segmentOffset);
        }
        for (FieldRef f : nonDefaultFields) {
            fieldOffsetMap.put(f.className + ":" + f.fieldName, nonDefaultBase + f.segmentOffset);
        }

        // --- static_field_component (§6.10 Table 6-10) ---
        var info = new BinaryWriter();
        info.u2(imageSize);          // §6.10: u2 image_size (total static field image bytes)
        info.u2(referenceCount);     // §6.10: u2 reference_count (reference-type fields)
        info.u2(0);                  // §6.10: u2 array_init_count (not yet implemented)
        info.u2(defaultValueCount);  // §6.10: u2 default_value_count (zero-init primitive bytes)
        info.u2(nonDefaults.length); // §6.10: u2 non_default_value_count (explicit init bytes)
        info.bytes(nonDefaults);     // §6.10: u1[] non_default_values (Segment 4 init data)

        byte[] bytes = HeaderComponent.wrapComponent(TAG, info.toByteArray());
        return new StaticFieldResult(bytes, imageSize, 0, 0, fieldOffsetMap);
    }

    private static void writeConstantValue(BinaryWriter out, String desc, Object value) {
        if (value instanceof Number n) {
            switch (desc.charAt(0)) {
                case 'B', 'Z' -> out.u1(n.intValue());
                case 'S', 'C' -> out.u2(n.intValue());
                case 'I' -> { out.u2((n.intValue() >> 16) & 0xFFFF); out.u2(n.intValue() & 0xFFFF); }
                default -> out.u2(n.intValue());
            }
        }
    }

    private static int fieldSize(String desc) {
        return switch (desc.charAt(0)) {
            case 'B', 'Z' -> 1;        // byte, boolean
            case 'S', 'C' -> 2;        // short, char
            case 'I' -> 4;             // int
            default -> 2;              // reference
        };
    }

    /**
     * Result of StaticField component generation, containing both the serialized
     * component bytes and metadata needed by other components.
     *
     * @param bytes            complete component bytes including tag and size header
     * @param imageSize        total static field image size in bytes (used by DirectoryComponent)
     * @param arrayInitCount   number of array initializers (used by DirectoryComponent; currently 0)
     * @param arrayInitSize    total bytes of array init data (used by DirectoryComponent; currently 0)
     * @param fieldOffsetMap   map from "className:fieldName" to byte offset within the static
     *                         field image, used by ConstantPoolComponent and ExportComponent
     */
    public record StaticFieldResult(byte[] bytes, int imageSize,
                                    int arrayInitCount, int arrayInitSize,
                                    Map<String, Integer> fieldOffsetMap) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof StaticFieldResult(var b, var is, var aic, var ais, var fom)) {
                return imageSize == is && arrayInitCount == aic
                        && arrayInitSize == ais && Arrays.equals(bytes, b)
                        && fom.equals(fieldOffsetMap);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(bytes);
            result = 31 * result + Integer.hashCode(imageSize);
            result = 31 * result + Integer.hashCode(arrayInitCount);
            result = 31 * result + Integer.hashCode(arrayInitSize);
            result = 31 * result + fieldOffsetMap.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "StaticFieldResult[bytes=" + HexFormat.of().formatHex(bytes)
                    + ", imageSize=" + imageSize + ", arrayInitCount=" + arrayInitCount
                    + ", arrayInitSize=" + arrayInitSize + ", fieldOffsetMap=" + fieldOffsetMap + "]";
        }
    }
}
