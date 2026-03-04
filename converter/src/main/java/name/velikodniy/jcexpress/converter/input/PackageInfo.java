package name.velikodniy.jcexpress.converter.input;

import java.util.List;

/**
 * Immutable representation of all classes belonging to a single Java package.
 *
 * <h2>Converter Pipeline: Stage 1 (Load)</h2>
 * <p>This record is the output of the Load stage. It captures the complete
 * set of classes that will be converted into a single CAP file. Per the
 * JCVM 3.0.5 specification (Section 4.1), a CAP file contains the contents
 * of exactly one Java package, making this record the fundamental unit of
 * conversion.
 *
 * <p>Once constructed by {@link PackageScanner#scan(java.nio.file.Path, String)},
 * the {@code PackageInfo} is consumed by:
 * <ul>
 *   <li><strong>Stage 2 (Subset Check):</strong>
 *       {@link name.velikodniy.jcexpress.converter.check.SubsetChecker SubsetChecker}
 *       validates that all classes conform to the JavaCard language subset.</li>
 *   <li><strong>Stage 3 (Token Assignment):</strong>
 *       {@link name.velikodniy.jcexpress.converter.token.TokenAssigner TokenAssigner}
 *       assigns numeric tokens to classes, methods, and fields.</li>
 *   <li><strong>Stages 4-6:</strong> Individual {@link ClassInfo} records are
 *       passed to the reference resolver, bytecode translator, and CAP component
 *       generators.</li>
 * </ul>
 *
 * @param packageName the Java package name in dot notation (e.g. {@code "com.example"})
 * @param classes     all classes in this package, as parsed by {@link ClassFileReader}
 * @see PackageScanner
 * @see ClassInfo
 */
public record PackageInfo(
        String packageName,
        List<ClassInfo> classes
) {
    /**
     * Returns the package name in JVM internal (slash) notation.
     *
     * <p>Converts the dot-separated package name to slash-separated format
     * (e.g. {@code "com.example"} becomes {@code "com/example"}), which is the
     * format used by JVM class files and the CAP file's internal directory structure.
     *
     * @return the package name with dots replaced by forward slashes
     */
    public String internalName() {
        return packageName.replace('.', '/');
    }
}
