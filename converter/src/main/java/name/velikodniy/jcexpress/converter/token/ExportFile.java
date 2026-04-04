package name.velikodniy.jcexpress.converter.token;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Parsed, in-memory representation of a Java Card export ({@code .exp}) file.
 *
 * <p>Export files are the Java Card platform's mechanism for cross-package linking
 * (JCVM 3.0.5, Section 4.4). They capture the public API of a package -- classes,
 * methods, and fields -- together with the numeric tokens assigned to each element
 * during conversion. When a package imports another, the converter reads the
 * imported package's export file to resolve symbolic references to concrete tokens.
 *
 * <h2>Role in the Converter Pipeline</h2>
 *
 * <p>Export files participate in two stages of the pipeline:
 *
 * <ul>
 *   <li><b>Stage 1 (Load)</b> -- {@link ExportFileReader} deserializes {@code .exp} files
 *       for imported packages into {@code ExportFile} instances.</li>
 *   <li><b>Stage 3 (Token Assignment)</b> -- {@link TokenAssigner} queries {@code ExportFile}
 *       data to inherit virtual method tokens from external superclasses, ensuring that
 *       overriding methods in the current package share the same token as the method they
 *       override.</li>
 *   <li><b>Stage 7 (Export Generation)</b> --
 *       {@link name.velikodniy.jcexpress.converter.exp.ExportFileWriter ExportFileWriter}
 *       generates a new {@code .exp} file for the package being converted.</li>
 * </ul>
 *
 * <h2>Binary Format</h2>
 *
 * <p>The on-disk binary format is defined in JCVM 3.0.5, Section 4.4 and uses a constant
 * pool structure similar to JVM class files. The magic number is {@code 0x00FACADE}.
 * See {@link ExportFileReader} for the detailed wire format.
 *
 * @param packageName  package name in dot notation (e.g., {@code "javacard.framework"})
 * @param aid          Application Identifier (AID) bytes that uniquely identify this package
 *                     on the card (5--16 bytes per ISO 7816-5)
 * @param majorVersion major version of the package (used for version-aware import linking)
 * @param minorVersion minor version of the package
 * @param classes      exported classes with their assigned tokens, methods, and fields
 * @see ExportFileReader
 * @see TokenAssigner
 */
public record ExportFile(
        String packageName,
        byte[] aid,
        int majorVersion,
        int minorVersion,
        List<ClassExport> classes
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ExportFile(var pn, var a, var maj, var min, var cls)) {
            return majorVersion == maj
                    && minorVersion == min
                    && Objects.equals(packageName, pn)
                    && Arrays.equals(aid, a)
                    && Objects.equals(classes, cls);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Objects.hash(packageName, majorVersion, minorVersion, classes)
                + Arrays.hashCode(aid));
    }

    @Override
    public String toString() {
        return "ExportFile[packageName=" + packageName
                + ", aid=" + HexFormat.of().formatHex(aid)
                + ", majorVersion=" + majorVersion
                + ", minorVersion=" + minorVersion
                + ", classes=" + classes + "]";
    }

    /**
     * Finds an exported class by its simple (unqualified) name.
     *
     * @param simpleName the simple class name (e.g., {@code "Applet"}, not
     *                   {@code "javacard/framework/Applet"})
     * @return the matching class export
     * @throws NoSuchElementException if no class with the given name is exported
     */
    public ClassExport findClass(String simpleName) {
        for (ClassExport ce : classes) {
            if (ce.name().equals(simpleName)) return ce;
            // Also match by simple name when export stores fully-qualified name
            String ceName = ce.name();
            int lastSlash = ceName.lastIndexOf('/');
            if (lastSlash >= 0 && ceName.substring(lastSlash + 1).equals(simpleName)) {
                return ce;
            }
        }
        throw new NoSuchElementException("Export class not found: " + simpleName);
    }

    /**
     * An exported class with its token, access flags, and exported members.
     *
     * @param name        simple (unqualified) class name (e.g., {@code "Applet"})
     * @param token       class token (0-based, package-scoped)
     * @param accessFlags JVM-style access flags (e.g., {@code ACC_PUBLIC | ACC_INTERFACE})
     * @param methods     exported methods with their tokens
     * @param fields      exported fields with their tokens
     */
    public record ClassExport(
            String name,
            int token,
            int accessFlags,
            List<MethodExport> methods,
            List<FieldExport> fields
    ) {}

    /**
     * An exported method with its token and access flags.
     *
     * <p>Both virtual and static methods appear in this list. Static methods can be
     * identified by the {@code ACC_STATIC} (0x0008) bit in {@link #accessFlags()}.
     *
     * @param name        method name (e.g., {@code "process"})
     * @param descriptor  method descriptor in JVM format (e.g., {@code "(Ljavacard/framework/APDU;)V"})
     * @param token       method token (0-based, class-scoped within the virtual or static namespace)
     * @param accessFlags JVM-style access flags (e.g., {@code ACC_PUBLIC}, {@code ACC_STATIC})
     */
    public record MethodExport(
            String name,
            String descriptor,
            int token,
            int accessFlags
    ) {}

    /**
     * An exported field with its token and access flags.
     *
     * @param name        field name
     * @param descriptor  field descriptor in JVM format (e.g., {@code "S"} for short)
     * @param token       field token (0-based, class-scoped)
     * @param accessFlags JVM-style access flags (e.g., {@code ACC_PUBLIC | ACC_STATIC | ACC_FINAL})
     */
    public record FieldExport(
            String name,
            String descriptor,
            int token,
            int accessFlags
    ) {}
}
