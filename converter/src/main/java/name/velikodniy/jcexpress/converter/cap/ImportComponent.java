package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;

import java.util.List;

/**
 * Generates the CAP Import component (tag 4) as defined in JCVM 3.0.5 spec section 6.6.
 *
 * <p>The Import component lists all external packages that this package depends on.
 * Each imported package is identified by its AID and version. The zero-based array
 * index of each package entry becomes that package's <em>package token</em>, which is
 * used throughout the ConstantPool component to encode external references (e.g.,
 * in {@code CONSTANT_ClassRef}, {@code CONSTANT_StaticMethodRef}).
 *
 * <p>For example, if {@code javacard.framework} is at index 0 and
 * {@code javacard.security} is at index 1, then an external class reference
 * to a class in {@code javacard.framework} will encode package token 0 in its
 * high byte (as {@code 0x80 | 0 = 0x80}).
 *
 * <p>The maximum number of imported packages is 128, per the JCVM specification.
 *
 * <p>Binary format (JCVM 3.0.5 spec section 6.6, Table 6-5):
 * <pre>
 * u1  tag = 4
 * u2  size
 * u1  count                  (number of imported packages, 0-128)
 * package_info[count]:
 *   u1  minor_version        (imported package minor version)
 *   u1  major_version        (imported package major version)
 *   u1  AID_length
 *   u1  AID[AID_length]      (imported package AID)
 * </pre>
 *
 * @see ConstantPoolComponent
 * @see name.velikodniy.jcexpress.converter.resolve.ImportedPackage
 */
public final class ImportComponent {

    public static final int TAG = 4;

    private ImportComponent() {}

    /**
     * Generates the Import component bytes.
     *
     * @param imports imported packages in token order
     * @return complete component bytes including tag and size
     */
    public static byte[] generate(List<ImportedPackage> imports) {
        // --- import_component (§6.6 Table 6-5) ---
        var info = new BinaryWriter();
        info.u1(imports.size()); // §6.6: u1 count (number of imported packages)

        for (ImportedPackage imp : imports) {
            // --- package_info (§6.6 Table 6-5) ---
            info.u1(imp.minorVersion());  // §6.6: u1 minor_version
            info.u1(imp.majorVersion());  // §6.6: u1 major_version
            info.aidWithLength(imp.aid()); // §6.6: u1 AID_length + u1[] AID
        }

        return HeaderComponent.wrapComponent(TAG, info.toByteArray());
    }
}
