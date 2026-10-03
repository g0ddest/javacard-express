package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;

import java.util.List;

/**
 * Generates the CAP Import component (tag 4) (JCVM 3.1 §6.7).
 *
 * <p>The Import component lists all external packages that this package depends on.
 * Each imported package is identified by its AID and version. The zero-based array
 * index of each package entry becomes that package's <em>package token</em> (§4.3.7.1), which
 * is used throughout the ConstantPool component to encode external references (e.g.,
 * in {@code CONSTANT_ClassRef}, {@code CONSTANT_StaticMethodRef}).
 *
 * <p>For example, if {@code javacard.framework} is at index 0 and
 * {@code javacard.security} is at index 1, then an external class reference
 * to a class in {@code javacard.framework} will encode package token 0 in its
 * high byte (as {@code 0x80 | 0 = 0x80}).
 *
 * <p>The version recorded for an imported package is the <em>package</em> version of the
 * export file it was linked against (CONSTANT_Package_info of {@code this_package}, §5.6.1),
 * never the export file format version (§6.7, §4.5.2). At most 128 packages can be imported.
 *
 * <p>Binary format (JCVM 3.1 §6.7; {@code package_info} as in the Header component, §6.4):
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

    private static final int MAX_IMPORTS = 128;

    private ImportComponent() {}

    /**
     * Generates the Import component bytes.
     *
     * <p>At most 128 packages can be imported (JCVM 3.1 §6.7).
     *
     * @param imports imported packages in token order
     * @return complete component bytes including tag and size
     * @throws IllegalArgumentException if more than 128 packages are imported
     */
    public static byte[] generate(List<ImportedPackage> imports) {
        if (imports.size() > MAX_IMPORTS) {
            throw new IllegalArgumentException(imports.size() + " imported packages; a CAP file imports at most "
                    + MAX_IMPORTS + " packages (package tokens 0..127, JCVM 3.1 §4.3.7.1, §6.7)");
        }
        // --- import_component (JCVM 3.1 §6.7) ---
        var info = new BinaryWriter();
        info.u1(imports.size()); // §6.7: u1 count (number of imported packages)

        for (ImportedPackage imp : imports) {
            // --- package_info (§6.4, §6.7) ---
            info.u1(imp.minorVersion());  // u1 minor_version of the imported package
            info.u1(imp.majorVersion());  // u1 major_version of the imported package
            info.aidWithLength(imp.aid()); // u1 AID_length + u1[] AID
        }

        return HeaderComponent.wrapComponent(TAG, info.toByteArray());
    }
}
