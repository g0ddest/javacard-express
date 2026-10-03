package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.clinit.StaticValue;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Generates the CAP Static Field component (tag 8) as defined in JCVM 3.1 §6.11.
 *
 * <p>The component describes the static field image of the package and how to initialize it.
 * A Java Card VM never executes {@code <clinit>}; the values it assigns (evaluated at conversion
 * time, see {@link name.velikodniy.jcexpress.converter.clinit.ClinitInterpreter}) are represented
 * here. The image is laid out in the four segments of §6.11 Table 6-13:
 * <ol>
 *   <li>reference fields holding arrays of primitive types created by {@code <clinit>}
 *       (one {@code array_init_info} each, in segment order);</li>
 *   <li>other reference fields (initialized to {@code null});</li>
 *   <li>primitive fields with the default value (zero);</li>
 *   <li>primitive fields with a non-default value ({@code non_default_values}).</li>
 * </ol>
 * Within a segment, fields appear in the order of the class list and, per class, in declaration
 * order. Field sizes follow Table 6-14 (boolean/byte 1, short 2, int 4, reference 2 bytes).
 * Compile-time constants ({@code static final} primitives with a {@code ConstantValue}) are not
 * part of the image.
 *
 * <pre>
 * u1  tag = 8
 * u2  size
 * u2  image_size
 * u2  reference_count
 * u2  array_init_count
 * array_init_info { u1 type; u2 count; u1 values[count] } array_init[array_init_count]
 * u2  default_value_count
 * u2  non_default_value_count
 * u1  non_default_values[non_default_value_count]
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
     * Generates the component without {@code <clinit>} information: every static field gets its
     * default value unless its class file carries a {@code ConstantValue} for it.
     *
     * @param classes all classes in the package
     * @return result containing component bytes and image statistics
     */
    public static StaticFieldResult generate(List<ClassInfo> classes) {
        return generate(classes, Map.of());
    }

    /**
     * Generates the component (§6.11).
     *
     * @param classes       all classes and interfaces of the package, in Class component order
     * @param initialValues {@code "class:field"} to the value assigned by the class's
     *                      {@code <clinit>}; fields without an entry keep their default value
     * @return result containing component bytes, image statistics and the image offset of every
     *         static field
     */
    public static StaticFieldResult generate(List<ClassInfo> classes, Map<String, StaticValue> initialValues) {
        Segments segments = new Segments();
        for (ClassInfo ci : classes) {
            for (FieldInfo fi : ci.fields()) {
                if (fi.isStatic() && !fi.isCompileTimeConstant()) {
                    segments.add(ci.thisClass() + ":" + fi.name(), fi, initialValues.get(ci.thisClass() + ":" + fi.name()));
                }
            }
        }
        return segments.toResult();
    }

    /** Fields per segment of Table 6-13, with the data of segments 1 and 4. */
    private static final class Segments {
        private final List<String> arrays = new ArrayList<>();
        private final List<String> references = new ArrayList<>();
        private final Map<String, Integer> defaults = new HashMap<>();
        private final Map<String, Integer> nonDefaults = new HashMap<>();
        private final BinaryWriter arrayInits = new BinaryWriter();
        private final BinaryWriter nonDefaultValues = new BinaryWriter();
        private int defaultValueCount;
        private int arrayInitSize;

        void add(String key, FieldInfo fi, StaticValue value) {
            String desc = fi.descriptor();
            if (desc.startsWith("L") || desc.startsWith("[")) {
                if (value instanceof StaticValue.PrimitiveArray array) {
                    arrays.add(key);
                    writeArrayInit(array);
                } else {
                    references.add(key);
                }
            } else if (value instanceof StaticValue.Primitive(int v) && v != 0) {
                nonDefaults.put(key, nonDefaultValues.size());
                writeValue(nonDefaultValues, fieldSize(desc), v);
            } else if (value == null && fi.constantValue() instanceof Number n && n.intValue() != 0) {
                nonDefaults.put(key, nonDefaultValues.size()); // ConstantValue of a non-final field
                writeValue(nonDefaultValues, fieldSize(desc), n.intValue());
            } else {
                defaults.put(key, defaultValueCount);
                defaultValueCount += fieldSize(desc);
            }
        }

        /** array_init_info: u1 type, u2 count (bytes), u1 values[count] (§6.11). */
        private void writeArrayInit(StaticValue.PrimitiveArray array) {
            int[] elements = array.elements();
            int count = elements.length * array.elementSize();
            arrayInits.u1(array.type());
            arrayInits.u2(count);
            for (int element : elements) {
                writeValue(arrayInits, array.elementSize(), element);
            }
            arrayInitSize += count;
        }

        StaticFieldResult toResult() {
            int referenceCount = arrays.size() + references.size();
            int defaultBase = 2 * referenceCount;
            int nonDefaultBase = defaultBase + defaultValueCount;
            Map<String, Integer> offsets = new HashMap<>();
            for (int i = 0; i < arrays.size(); i++) {
                offsets.put(arrays.get(i), 2 * i);
            }
            for (int i = 0; i < references.size(); i++) {
                offsets.put(references.get(i), 2 * (arrays.size() + i));
            }
            defaults.forEach((k, off) -> offsets.put(k, defaultBase + off));
            nonDefaults.forEach((k, off) -> offsets.put(k, nonDefaultBase + off));
            byte[] nonDefault = nonDefaultValues.toByteArray();
            int imageSize = nonDefaultBase + nonDefault.length;

            var info = new BinaryWriter();
            info.u2(imageSize);                 // image_size = reference_count*2 + default + non-default
            info.u2(referenceCount);            // reference_count (segments 1 and 2)
            info.u2(arrays.size());             // array_init_count (segment 1)
            info.bytes(arrayInits.toByteArray()); // array_init[]
            info.u2(defaultValueCount);         // default_value_count (segment 3 bytes)
            info.u2(nonDefault.length);         // non_default_value_count (segment 4 bytes)
            info.bytes(nonDefault);             // non_default_values[]
            byte[] bytes = HeaderComponent.wrapComponent(TAG, info.toByteArray());
            return new StaticFieldResult(bytes, imageSize, arrays.size(), arrayInitSize, offsets);
        }
    }

    /** Writes a big-endian value of 1, 2 or 4 bytes (§6.11: boolean true is 1). */
    private static void writeValue(BinaryWriter out, int size, int value) {
        switch (size) {
            case 1 -> out.u1(value);
            case 2 -> out.u2(value);
            default -> {
                out.u2((value >> 16) & 0xFFFF);
                out.u2(value & 0xFFFF);
            }
        }
    }

    /** Size of a primitive static field in the image (§6.11 Table 6-14). */
    private static int fieldSize(String desc) {
        return switch (desc.charAt(0)) {
            case 'B', 'Z' -> 1;        // byte, boolean
            case 'I' -> 4;             // int
            default -> 2;              // short (and char)
        };
    }

    /**
     * Result of StaticField component generation, containing both the serialized
     * component bytes and metadata needed by other components.
     *
     * @param bytes            complete component bytes including tag and size header
     * @param imageSize        total static field image size in bytes (used by DirectoryComponent)
     * @param arrayInitCount   number of array_init_info entries (used by DirectoryComponent)
     * @param arrayInitSize    sum of the count items of the array_init_info entries (used by
     *                         DirectoryComponent, JCVM 3.1 §6.5)
     * @param fieldOffsetMap   map from "className:fieldName" to byte offset within the static
     *                         field image, used by the Constant Pool, Descriptor and Export
     *                         components
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
