package name.velikodniy.jcexpress.converter;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Validates the name and version of the package being converted when they are configured
 * (fail-closed: a value that cannot be represented in the CAP and export files is an error,
 * never silently truncated or written malformed).
 *
 * <ul>
 *   <li>The package has a name. It locates the CAP components ({@code <package>/javacard/},
 *       JCVM 3.1 §4.1.3) and names the package in its export file (CONSTANT_Package_info,
 *       "a valid Java package name", §4.1.1, §5.6.1), so classes of the unnamed package cannot
 *       be converted.</li>
 *   <li>The name is a sequence of Java identifiers separated by {@code '.'} (or {@code '/'},
 *       the internal form of §5.6.1), and its fully qualified form has at most 255 bytes in
 *       UTF-8 (§2.2.4.1.3; the u1 {@code name_length} of {@code package_name_info}, §6.4).</li>
 *   <li>The major and minor versions are u1 items of {@code package_info} and
 *       CONSTANT_Package_info (§4.5, §6.4, §5.6.1): 0 to 255.</li>
 * </ul>
 */
final class PackageRules {

    private static final int MAX_NAME_BYTES = 255;
    private static final int MAX_VERSION = 255;

    private PackageRules() {}

    /**
     * Checks a package name and returns it in dot notation.
     *
     * @param name package name in dot notation ({@code com.acme.wallet}) or internal form
     *             ({@code com/acme/wallet})
     * @return the name in dot notation
     * @throws IllegalArgumentException if the name is empty, not a sequence of Java identifiers,
     *                                  or longer than 255 bytes in UTF-8
     * @throws NullPointerException     if {@code name} is {@code null}
     */
    static String packageName(String name) {
        Objects.requireNonNull(name, "packageName");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Classes of the unnamed package cannot be converted: a Java Card"
                    + " package needs a name for its CAP components (<package>/javacard/, JCVM 3.1 §4.1.3) and"
                    + " its export file (§4.1.1, §5.6.1); declare the classes in a named package");
        }
        String dotted = name.replace('/', '.');
        for (String identifier : dotted.split("\\.", -1)) {
            if (!isJavaIdentifier(identifier)) {
                throw new IllegalArgumentException("Invalid package name '" + name + "': '" + identifier
                        + "' is not a Java identifier; expected identifiers separated by '.', e.g. com.acme.wallet");
            }
        }
        int bytes = dotted.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_NAME_BYTES) {
            throw new IllegalArgumentException("Invalid package name '" + name + "': it is " + bytes
                    + " bytes long in UTF-8; a Java Card package name has at most 255 (JCVM 3.1 §2.2.4.1.3, §6.4)");
        }
        return dotted;
    }

    /**
     * Checks a package version.
     *
     * @param major major version
     * @param minor minor version
     * @throws IllegalArgumentException if either number is outside 0..255
     */
    static void packageVersion(int major, int minor) {
        if (major < 0 || major > MAX_VERSION || minor < 0 || minor > MAX_VERSION) {
            throw new IllegalArgumentException("Invalid package version " + major + "." + minor
                    + ": major and minor version are each 0 to 255 (u1 items, JCVM 3.1 §4.5, §6.4)");
        }
    }

    private static boolean isJavaIdentifier(String s) {
        if (s.isEmpty() || !Character.isJavaIdentifierStart(s.codePointAt(0))) return false;
        return s.codePoints().skip(1).allMatch(Character::isJavaIdentifierPart);
    }
}
