package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.token.TokenMap;

import java.util.List;
import java.util.Map;

/**
 * Generates the CAP Export component (tag 10) as defined in JCVM 3.0.5 spec section 6.12.
 *
 * <p>The Export component provides the linking information that other packages need to
 * resolve references to this package's public API. It is present only when the package
 * is a shareable library (indicated by {@link HeaderComponent#ACC_EXPORT} in the Header
 * flags), or when explicitly requested during conversion.
 *
 * <p>For each public class in the package, the Export component records:
 * <ul>
 *   <li>The class's byte offset within the Class component (so importing packages can
 *       build {@code CONSTANT_Classref} entries)</li>
 *   <li>Byte offsets of all public static fields within the StaticField component image
 *       (for {@code CONSTANT_StaticFieldref} resolution)</li>
 *   <li>Byte offsets of all public static methods within the Method component
 *       (for {@code CONSTANT_StaticMethodref} resolution)</li>
 * </ul>
 *
 * <p>Classes are listed in token order (matching the order in the Class component).
 * Within each class, static fields and static methods are listed in their respective
 * token order.
 *
 * <p>Binary format (JCVM 3.0.5 spec section 6.12, Table 6-12):
 * <pre>
 * u1  tag = 10
 * u2  size
 * u1  class_count                                        (number of exported classes)
 * class_export_info[class_count]:
 *   u2  class_offset                                     (offset into Class component)
 *   u1  static_field_count
 *   u1  static_method_count
 *   u2  static_field_offsets[static_field_count]          (offsets into StaticField image)
 *   u2  static_method_offsets[static_method_count]        (offsets into Method component)
 * </pre>
 *
 * @see ClassComponent
 * @see MethodComponent
 * @see StaticFieldComponent
 * @see ImportComponent
 */
public final class ExportComponent {

    public static final int TAG = 10;

    private ExportComponent() {}

    /**
     * Generates the Export component bytes.
     *
     * @param tokenMap            token assignments for the package
     * @param methodOffsets       method offsets in the Method component (global index → offset)
     * @param classOffsets        class offsets in the Class component (class token → offset)
     * @param methodIndexMap      maps "className:methodName:methodDesc" → global method index
     * @param staticFieldOffsetMap maps "className:fieldName" → StaticField image offset
     * @return complete component bytes including tag and size
     */
    public static byte[] generate(TokenMap tokenMap, int[] methodOffsets,
                                   int[] classOffsets,
                                   Map<String, Integer> methodIndexMap,
                                   Map<String, Integer> staticFieldOffsetMap) {
        // --- export_component (§6.12 Table 6-12) ---
        var info = new BinaryWriter();
        List<TokenMap.ClassEntry> classes = tokenMap.classes();
        info.u1(classes.size()); // §6.12: u1 class_count

        for (int i = 0; i < classes.size(); i++) {
            TokenMap.ClassEntry ce = classes.get(i);

            // --- class_export_info (§6.12 Table 6-12) ---
            int classOffset = (i < classOffsets.length) ? classOffsets[i] : 0;
            info.u2(classOffset); // §6.12: u2 class_offset (into Class component)

            List<TokenMap.FieldEntry> staticFields = ce.staticFields();
            List<TokenMap.MethodEntry> staticMethods = ce.staticMethods();

            info.u1(staticFields.size());   // §6.12: u1 static_field_count
            info.u1(staticMethods.size());  // §6.12: u1 static_method_count

            // §6.12: u2[] static_field_offsets (into StaticField image)
            for (TokenMap.FieldEntry fe : staticFields) {
                String key = ce.internalName() + ":" + fe.name();
                Integer offset = staticFieldOffsetMap.get(key);
                info.u2(offset != null ? offset : 0);
            }

            // §6.12: u2[] static_method_offsets (into Method component)
            for (TokenMap.MethodEntry me : staticMethods) {
                String key = ce.internalName() + ":" + me.name() + ":" + me.descriptor();
                Integer globalIdx = methodIndexMap.get(key);
                if (globalIdx != null && globalIdx < methodOffsets.length) {
                    info.u2(methodOffsets[globalIdx]);
                } else {
                    info.u2(0);
                }
            }
        }

        return HeaderComponent.wrapComponent(TAG, info.toByteArray());
    }
}
