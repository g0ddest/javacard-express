package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.JavaCardVersion;

/**
 * Generates the CAP Directory component (tag 2) in Compact format (JCVM 3.1 §6.5).
 *
 * <p>The Directory component acts as a table of contents for the CAP file. It records
 * the size of every component, enabling the JCVM to pre-allocate memory before loading,
 * and the static field image statistics needed to initialize the static field segment.
 * It is generated last because it needs the final sizes of all other components; absent
 * optional components (Applet, Export, Debug, Static Resources) have size zero.
 *
 * <p>Binary format ({@code directory_component_compact}, JCVM 3.1 §6.5):
 * <pre>
 * u1  tag = 2
 * u2  size
 * component_size_info_compact:
 *   u2  Header .. Descriptor sizes      (tags 1-11, 11 entries)
 *   u2  Debug_Component_Size            (since CAP format 2.2)
 *   u4  Static_Resource_Component_Size  (since CAP format 2.3)
 * static_field_size_info:
 *   u2  image_size          (total bytes for static field image)
 *   u2  array_init_count    (number of array initializers)
 *   u2  array_init_size     (total bytes of array init data)
 * u1  import_count          (number of imported packages)
 * u1  applet_count          (number of applets defined in this package)
 * u1  custom_count = 0      (number of custom components; always 0)
 * </pre>
 *
 * @see StaticFieldComponent
 */
public final class DirectoryComponent {

    public static final int TAG = 2;

    private static final int DESCRIPTOR_TAG = 11;
    private static final int DEBUG_TAG = 12;
    private static final int STATIC_RESOURCES_TAG = 13;

    private DirectoryComponent() {}

    /**
     * Generates the Directory component bytes for CAP format 2.1.
     *
     * @param componentSizes    component body sizes indexed by tag - 1 (index 0 = Header)
     * @param staticImageSize   total static field image size in bytes
     * @param arrayInitCount    number of array static initializers
     * @param arrayInitSize     total bytes of array initializer data
     * @param importCount       number of imported packages
     * @param appletCount       number of applets
     * @return complete component bytes including tag and size
     */
    public static byte[] generate(int[] componentSizes, int staticImageSize,
                                   int arrayInitCount, int arrayInitSize,
                                   int importCount, int appletCount) {
        return generate(componentSizes, staticImageSize, arrayInitCount, arrayInitSize,
                importCount, appletCount, null);
    }

    /**
     * Generates the Directory component bytes for the CAP format of a Java Card version.
     *
     * <p>CAP format 2.1 lists the sizes of the components with tags 1-11; format 2.2 adds the
     * u2 Debug component size and format 2.3 the u4 Static Resource component size
     * (JCVM 3.1 §6.5, {@code component_size_info_compact}).
     *
     * @param componentSizes    component body sizes indexed by tag - 1 (index 0 = Header);
     *                          missing entries are zero
     * @param staticImageSize   total static field image size in bytes
     * @param arrayInitCount    number of array static initializers
     * @param arrayInitSize     total bytes of array initializer data
     * @param importCount       number of imported packages
     * @param appletCount       number of applets
     * @param version           target JavaCard version (selects the CAP format), null means format 2.1
     * @return complete component bytes including tag and size
     */
    public static byte[] generate(int[] componentSizes, int staticImageSize,
                                   int arrayInitCount, int arrayInitSize,
                                   int importCount, int appletCount,
                                   JavaCardVersion version) {
        var info = new BinaryWriter();
        int formatMinor = version == null ? 1 : version.formatMinor();

        // §6.5 component_size_info_compact
        for (int tag = 1; tag <= DESCRIPTOR_TAG; tag++) {
            info.u2(size(componentSizes, tag));
        }
        if (formatMinor >= 2) {
            info.u2(size(componentSizes, DEBUG_TAG));
        }
        if (formatMinor >= 3) {
            info.u4(size(componentSizes, STATIC_RESOURCES_TAG));
        }

        // §6.5 static_field_size_info
        info.u2(staticImageSize);
        info.u2(arrayInitCount);
        info.u2(arrayInitSize);

        info.u1(importCount);     // §6.5: same value as the count item of the Import component
        info.u1(appletCount);     // §6.5: same value as the count item of the Applet component, or 0
        info.u1(0);               // §6.5: custom_count (no custom components are generated)

        return HeaderComponent.wrapComponent(TAG, info.toByteArray());
    }

    private static int size(int[] componentSizes, int tag) {
        return tag - 1 < componentSizes.length ? componentSizes[tag - 1] : 0;
    }
}
