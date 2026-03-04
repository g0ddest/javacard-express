package name.velikodniy.jcexpress.converter.cap;

/**
 * Generates the CAP Header component (tag 1) as defined in JCVM 3.0.5 spec section 6.3.
 *
 * <p>The Header component is the first component in every CAP file and serves as the
 * identification block. It contains:
 * <ul>
 *   <li>The CAP magic number ({@code 0xDECAFFED}) for format identification</li>
 *   <li>The CAP file format version (e.g., 2.1 for JavaCard 3.0.5)</li>
 *   <li>Package flags indicating integer support, export presence, and applet presence</li>
 *   <li>Package AID and version information</li>
 *   <li>Optional human-readable package name</li>
 * </ul>
 *
 * <p>This component also provides the shared {@link #wrapComponent(int, byte[])} utility
 * method used by all other component generators to prepend the standard
 * {@code u1 tag + u2 size} header to their binary payload.
 *
 * <p>Binary format (JCVM 3.0.5 spec section 6.3, Table 6-1):
 * <pre>
 * u1  tag = 1
 * u2  size
 * u4  magic = 0xDECAFFED
 * u1  minor_version       (CAP format minor version)
 * u1  major_version       (CAP format major version, e.g. 2)
 * u1  flags               (ACC_INT=0x01, ACC_EXPORT=0x02, ACC_APPLET=0x04)
 * package_info:
 *   u1  minor_version     (package minor version)
 *   u1  major_version     (package major version)
 *   u1  AID_length
 *   u1  AID[AID_length]
 * package_name_info (optional):
 *   u1  name_length
 *   u1  name[name_length] (UTF-8, slash-separated)
 * </pre>
 *
 * @see CapFileWriter
 */
public final class HeaderComponent {

    public static final int TAG = 1;
    public static final int CAP_MAGIC = 0xDECAFFED;
    // CAP file format version (2.1 for JC 3.0.5)
    public static final int FORMAT_MAJOR = 2;
    public static final int FORMAT_MINOR = 1;

    public static final int ACC_INT = 0x01;
    public static final int ACC_EXPORT = 0x02;
    public static final int ACC_APPLET = 0x04;

    private HeaderComponent() {}

    /**
     * Generates the Header component bytes.
     *
     * @param packageAid      package AID (5-16 bytes)
     * @param pkgMajorVersion package major version
     * @param pkgMinorVersion package minor version
     * @param flags           ACC_INT | ACC_EXPORT | ACC_APPLET
     * @param packageName     package name in slash notation (e.g. "com/example"),
     *                        set to null to omit package_name_info
     * @return complete component bytes including tag and size
     */
    public static byte[] generate(byte[] packageAid, int pkgMajorVersion, int pkgMinorVersion,
                                   int flags, String packageName) {
        return generate(packageAid, pkgMajorVersion, pkgMinorVersion, flags, packageName, null);
    }

    /**
     * Generates the Header component bytes with explicit JavaCard version.
     *
     * @param packageAid      package AID (5-16 bytes)
     * @param pkgMajorVersion package major version
     * @param pkgMinorVersion package minor version
     * @param flags           ACC_INT | ACC_EXPORT | ACC_APPLET
     * @param packageName     package name in slash notation, or null to omit
     * @param version         target JavaCard version (determines CAP format version),
     *                        null defaults to 2.1
     * @return complete component bytes including tag and size
     */
    public static byte[] generate(byte[] packageAid, int pkgMajorVersion, int pkgMinorVersion,
                                   int flags, String packageName,
                                   name.velikodniy.jcexpress.converter.JavaCardVersion version) {
        int fmtMajor = version != null ? version.formatMajor() : FORMAT_MAJOR;
        int fmtMinor = version != null ? version.formatMinor() : FORMAT_MINOR;

        var info = new BinaryWriter();
        // --- header_component (§6.3 Table 6-1) ---
        info.u4(CAP_MAGIC);       // §6.3: u4 magic = 0xDECAFFED
        info.u1(fmtMinor);        // §6.3: u1 minor_version (CAP format)
        info.u1(fmtMajor);        // §6.3: u1 major_version (CAP format)
        info.u1(flags);           // §6.3: u1 flags (ACC_INT|ACC_EXPORT|ACC_APPLET)

        // --- package_info (§6.3 Table 6-1) ---
        info.u1(pkgMinorVersion); // §6.3: u1 package minor_version
        info.u1(pkgMajorVersion); // §6.3: u1 package major_version
        info.aidWithLength(packageAid); // §6.3: u1 AID_length + u1[] AID

        // --- package_name_info (§6.3 Table 6-1, optional) ---
        // Per §6.3: present only when the converter is directed to include it
        if (packageName != null && !packageName.isEmpty()) {
            byte[] nameBytes = packageName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            info.u1(nameBytes.length); // §6.3: u1 name_length
            info.bytes(nameBytes);     // §6.3: u1[] name (UTF-8, slash-separated)
        }
        // else: omit package_name_info entirely per §6.3

        // §6.3: CAP format 2.3 (JC 3.1.0+) appends u1 flags2 after package info
        if (fmtMinor >= 3) {
            info.u1(0); // flags2: 0 = compact format (no extended header)
        }

        return wrapComponent(TAG, info.toByteArray());
    }

    /**
     * Wraps a component's info payload with the standard CAP component header.
     * Every CAP component uses the same envelope: {@code u1 tag + u2 size + u1[] info}.
     * This method is shared by all component generators in this package.
     *
     * @param tag  the component tag number (1-12)
     * @param info the component's serialized info bytes (excluding tag and size)
     * @return the complete component bytes: tag(1) + size(2) + info(N)
     */
    static byte[] wrapComponent(int tag, byte[] info) {
        // §6.1: Every CAP component uses the envelope: u1 tag + u2 size + u1[] info
        var out = new BinaryWriter();
        out.u1(tag);          // §6.1: u1 tag (component identifier, 1-12)
        out.u2(info.length);  // §6.1: u2 size (byte count of info[], excludes tag+size)
        out.bytes(info);      // §6.1: u1[] info (component-specific payload)
        return out.toByteArray();
    }
}
