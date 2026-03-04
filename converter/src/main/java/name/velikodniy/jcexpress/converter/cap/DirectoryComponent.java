package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.JavaCardVersion;

/**
 * Generates the CAP Directory component (tag 2) as defined in JCVM 3.0.5 spec section 6.4.
 *
 * <p>The Directory component acts as a table of contents for the CAP file. It records
 * the size of every other component (tags 1-11), enabling the JCVM to pre-allocate
 * memory before loading. It also contains static field image statistics needed by the
 * card's runtime to initialize the static field segment.
 *
 * <p>The Directory component is typically generated last (or updated last) during
 * Stage 6 because it requires the final sizes of all other components to be known.
 * The component_sizes array uses 11 entries for tags 1-11 (Header through Descriptor);
 * note that this implementation writes 11 entries per the spec's standard form (the
 * 12th entry for Debug/custom components is handled separately via custom_count).
 *
 * <p>Binary format (JCVM 3.0.5 spec section 6.4, Table 6-2):
 * <pre>
 * u1  tag = 2
 * u2  size
 * u2  component_sizes[11]   (sizes for tags 1-11, index = tag - 1)
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

    private DirectoryComponent() {}

    /**
     * Generates the Directory component bytes (format 2.1/2.2 default).
     *
     * @param componentSizes    array of component sizes (index 0 = Header, index 10 = Descriptor)
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
     * Generates the Directory component bytes with explicit JavaCard version.
     *
     * <p>CAP format 2.3 (JC 3.1.0+) writes 14 component size entries (tags 1-14)
     * instead of 11 entries (tags 1-11) used by format 2.1/2.2. The additional entries
     * cover Debug (tag 12), StaticResources (tag 13), and an extension slot (tag 14).
     *
     * @param componentSizes    array of component sizes (index 0 = Header)
     * @param staticImageSize   total static field image size in bytes
     * @param arrayInitCount    number of array static initializers
     * @param arrayInitSize     total bytes of array initializer data
     * @param importCount       number of imported packages
     * @param appletCount       number of applets
     * @param version           target JavaCard version (determines entry count), null defaults to 2.1
     * @return complete component bytes including tag and size
     */
    public static byte[] generate(int[] componentSizes, int staticImageSize,
                                   int arrayInitCount, int arrayInitSize,
                                   int importCount, int appletCount,
                                   JavaCardVersion version) {
        // --- directory_component (§6.4 Table 6-2) ---
        var info = new BinaryWriter();

        // §6.4: format 2.3 writes 14 component size entries (tags 1-14),
        // format 2.1/2.2 writes 11 entries (tags 1-11)
        int entryCount = (version != null && version.formatMinor() >= 3) ? 14 : 11;
        for (int i = 0; i < entryCount; i++) {
            info.u2(i < componentSizes.length ? componentSizes[i] : 0);
        }

        // --- static_field_size_info (§6.4 Table 6-3) ---
        info.u2(staticImageSize); // §6.4: u2 image_size (total static field image bytes)
        info.u2(arrayInitCount);  // §6.4: u2 array_init_count
        info.u2(arrayInitSize);   // §6.4: u2 array_init_size

        // §6.4 Table 6-2: trailing counts
        info.u1(importCount);     // §6.4: u1 import_count
        info.u1(appletCount);     // §6.4: u1 applet_count
        info.u1(0);               // §6.4: u1 custom_count (always 0, no custom components)

        return HeaderComponent.wrapComponent(TAG, info.toByteArray());
    }
}
