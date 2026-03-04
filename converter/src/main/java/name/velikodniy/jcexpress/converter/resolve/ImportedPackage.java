package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Represents an imported JavaCard package with its assigned token, used during
 * <strong>Stage 4: Reference Resolution</strong> of the converter pipeline.
 *
 * <p>Each imported package occupies a slot in the JCVM Import Component (JCVM spec 6.7),
 * identified by a zero-based token. When the constant pool encodes an external reference
 * (class, method, or field belonging to another package), it uses the package token with
 * the high bit set ({@code package_token | 0x80}) as specified in JCVM spec 6.8.
 *
 * <p>The export file provides the token-to-name mappings for all publicly visible classes,
 * methods, and fields in the imported package. These tokens are assigned by the package
 * publisher and must match exactly for binary compatibility with real JavaCard cards.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Created by {@link BuiltinExports#allBuiltinImports} for standard API packages,
 *       or constructed directly for user-provided export files.</li>
 *   <li>Passed to {@link ReferenceResolver} which looks up class/method/field tokens
 *       during bytecode translation.</li>
 *   <li>After translation, {@link ReferenceResolver#finalizeImports(name.velikodniy.jcexpress.converter.JavaCardVersion) finalizeImports()} prunes unreferenced
 *       packages and reassigns contiguous tokens.</li>
 *   <li>The finalized list is serialized into the Import Component of the CAP file.</li>
 * </ol>
 *
 * @param token        import index (0-based), used as the package token in external CP
 *                     references; reassigned by {@link ReferenceResolver#finalizeImports(name.velikodniy.jcexpress.converter.JavaCardVersion) finalizeImports()}
 * @param aid          Application Identifier (AID) of the imported package, as defined by
 *                     ISO 7816-5 (typically 5-16 bytes)
 * @param majorVersion major version of the imported package API
 * @param minorVersion minor version of the imported package API
 * @param exportFile   parsed export file containing class/method/field token assignments
 *
 * @see ReferenceResolver
 * @see BuiltinExports
 * @see <a href="https://docs.oracle.com/javacard/3.0.5/JCVM/jcvm-spec-3_0_5.pdf">JCVM 3.0.5 spec, section 6.7 (Import Component)</a>
 * @see <a href="https://docs.oracle.com/javacard/3.0.5/JCVM/jcvm-spec-3_0_5.pdf">JCVM 3.0.5 spec, section 6.8 (Constant Pool Component)</a>
 */
public record ImportedPackage(
        int token,
        byte[] aid,
        int majorVersion,
        int minorVersion,
        ExportFile exportFile
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ImportedPackage(var t, var a, var maj, var min, var ef)) {
            return token == t
                    && majorVersion == maj
                    && minorVersion == min
                    && Arrays.equals(aid, a)
                    && Objects.equals(exportFile, ef);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Integer.hashCode(token) + Arrays.hashCode(aid))
                + Objects.hashCode(exportFile);
    }

    @Override
    public String toString() {
        return "ImportedPackage[token=" + token
                + ", aid=" + HexFormat.of().formatHex(aid)
                + ", majorVersion=" + majorVersion
                + ", minorVersion=" + minorVersion + "]";
    }
}
