package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;
import name.velikodniy.jcexpress.converter.token.TokenMap;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Everything needed to describe a converted package in its export file (JCVM 3.1 Chapter 5).
 *
 * @param packageName     package name in internal form (e.g. {@code "com/acme/lib"})
 * @param aid             package AID (5 to 16 bytes, §5.6.1)
 * @param majorVersion    package major version (§4.5, §5.6.1)
 * @param minorVersion    package minor version (§4.5, §5.6.1)
 * @param library         {@code true} if the package declares no applets (ACC_LIBRARY, §5.6.1):
 *                        a library exports all public classes and interfaces, an applet package
 *                        only its public shareable interfaces (§5.5)
 * @param classes         all classes and interfaces of the package
 * @param tokenMap        tokens assigned to the package's classes and members (§4.3.7)
 * @param imports         imported packages; their export files describe external supertypes and
 *                        supply AIDs and versions of referenced packages (§5.4, §5.5)
 * @param javaCardVersion target version; selects the export file format (2.1, or 2.3 for
 *                        Java Card 3.1 and later)
 */
public record ExportInput(
        String packageName,
        byte[] aid,
        int majorVersion,
        int minorVersion,
        boolean library,
        List<ClassInfo> classes,
        TokenMap tokenMap,
        List<ImportedPackage> imports,
        JavaCardVersion javaCardVersion
) {
    /**
     * Validates and copies the arguments.
     *
     * @throws IllegalArgumentException for the unnamed package: CONSTANT_Package_info names a
     *                                  package (JCVM 3.1 §5.6.1)
     */
    public ExportInput {
        Objects.requireNonNull(packageName, "packageName");
        if (packageName.isEmpty()) {
            throw new IllegalArgumentException("The unnamed package has no export file: CONSTANT_Package_info"
                    + " names a package (JCVM 3.1 §5.6.1)");
        }
        packageName = packageName.replace('.', '/');
        aid = aid.clone();
        classes = List.copyOf(classes);
        Objects.requireNonNull(tokenMap, "tokenMap");
        imports = List.copyOf(imports);
        Objects.requireNonNull(javaCardVersion, "javaCardVersion");
    }

    @Override
    public byte[] aid() {
        return aid.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ExportInput(var n, var a, var maj, var min, var lib, var cls, var tm, var imp, var v)
                && packageName.equals(n) && Arrays.equals(aid, a) && majorVersion == maj && minorVersion == min
                && library == lib && classes.equals(cls) && tokenMap.equals(tm) && imports.equals(imp)
                && javaCardVersion == v;
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(packageName, majorVersion, minorVersion, library, classes, tokenMap, imports,
                javaCardVersion) + Arrays.hashCode(aid);
    }

    @Override
    public String toString() {
        return "ExportInput[packageName=" + packageName + ", aid=" + HexFormat.of().formatHex(aid)
                + ", version=" + majorVersion + "." + minorVersion + ", library=" + library
                + ", classes=" + classes.size() + ", imports=" + imports.size() + ", javaCardVersion="
                + javaCardVersion + "]";
    }
}
