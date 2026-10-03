package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.cap.BinaryWriter;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.resolve.BuiltinExports;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFile.ClassExport;
import name.velikodniy.jcexpress.converter.token.ExportFile.FieldExport;
import name.velikodniy.jcexpress.converter.token.ExportFile.MethodExport;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import name.velikodniy.jcexpress.converter.token.TokenMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes Java Card export ({@code .exp}) files (JCVM 3.1 Chapter 5).
 *
 * <p>{@link #write(ExportInput)} describes a converted package: {@link ExportModelBuilder}
 * decides what the file contains, {@link #write(ExportFile)} serializes it:
 * <pre>
 * ExportFile {
 *   u4 magic = 0x00FACADE
 *   u1 minor_version, u1 major_version     // export file FORMAT version: 2.1, or 2.3 (§5.5)
 *   u2 constant_pool_count
 *   cp_info constant_pool[]                // Utf8, Integer, Classref, Package (§5.6)
 *   u2 this_package                        // CONSTANT_Package: flags, PACKAGE version, AID
 *   u1 referenced_package_count            // format 2.3: packages of supertypes and
 *   u2 referenced_packages[]               //   descriptor types (§5.5)
 *   u1 export_class_count
 *   class_info classes[]                   // token, flags, name, supers, interfaces, fields,
 * }                                        //   methods [, CAP22 count in 2.3] (§5.7-§5.10)
 * </pre>
 * The constant pool is built deterministically, so the same model always gives the same bytes.
 *
 * @see ExportFileReader
 */
public final class ExportFileWriter {

    private static final int FORMAT_WITH_REFERENCED_PACKAGES = 3;
    private static final String CONSTANT_VALUE = "ConstantValue";

    private ExportFileWriter() {}

    /**
     * Generates the export file of a converted package.
     *
     * @param input the converted package, its tokens and its imports
     * @return the export file bytes, in the export file format of the target version
     * @throws ConverterException if the package cannot be described by a conforming export file
     */
    public static byte[] write(ExportInput input) throws ConverterException {
        return write(ExportModelBuilder.build(input));
    }

    /**
     * Serializes an export file model in the format given by its {@link ExportFile#formatMajor()}
     * and {@link ExportFile#formatMinor()}; format 2.3 adds the model's referenced packages and
     * every class's CAP22 inheritable method count (§5.5, §5.7).
     *
     * @param export the export file content
     * @return the export file bytes
     * @throws IllegalArgumentException if a table of the model exceeds its size limit
     */
    public static byte[] write(ExportFile export) {
        boolean format23 = export.formatMinor() >= FORMAT_WITH_REFERENCED_PACKAGES;
        ConstantPool cp = new ConstantPool();
        int thisPackage = cp.pkg(export.packageName(), export.packageFlags(), export.majorVersion(),
                export.minorVersion(), export.aid());
        List<Integer> referenced = new ArrayList<>();
        if (format23) {
            for (ExportFile.PackageReference ref : export.referencedPackages()) {
                // §5.6.1: the flags of a referenced package are not defined; written as zero
                referenced.add(cp.pkg(ref.name(), 0, ref.majorVersion(), ref.minorVersion(), ref.aid()));
            }
        }
        BinaryWriter body = new BinaryWriter();
        body.u2(thisPackage);
        if (format23) {
            body.u1(checkU1(referenced.size(), "referenced_package_count"));
            referenced.forEach(body::u2);
        }
        body.u1(checkU1(export.classes().size(), "export_class_count"));
        for (ClassExport c : export.classes()) writeClass(body, c, cp, format23);

        BinaryWriter out = new BinaryWriter();
        out.u4(ExportFileReader.EXP_MAGIC);
        out.u1(export.formatMinor());
        out.u1(export.formatMajor());
        cp.writeTo(out);
        out.bytes(body.toByteArray());
        return out.toByteArray();
    }

    /**
     * Generates an export file that describes the package as a library, with the built-in API
     * export data of {@code jcVersion} for external supertypes.
     *
     * @param tokenMap            token assignments
     * @param classes             classes of the package
     * @param packageAid          package AID bytes
     * @param packageMajorVersion package major version (written into CONSTANT_Package)
     * @param packageMinorVersion package minor version (written into CONSTANT_Package)
     * @param jcVersion           target Java Card version (determines the export file format)
     * @return binary export file data
     * @throws IllegalArgumentException if the package cannot be described by a conforming export file
     * @deprecated use {@link #write(ExportInput)}, which also knows whether the package declares
     *             applets (an applet package exports only its shareable interfaces, §5.5) and the
     *             export files of user-supplied imports
     */
    @Deprecated
    public static byte[] write(TokenMap tokenMap, List<ClassInfo> classes, byte[] packageAid,
                               int packageMajorVersion, int packageMinorVersion, JavaCardVersion jcVersion) {
        try {
            return write(new ExportInput(tokenMap.packageName(), packageAid, packageMajorVersion,
                    packageMinorVersion, true, classes, tokenMap,
                    BuiltinExports.allBuiltinImports(0, jcVersion), jcVersion));
        } catch (ConverterException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    // ── class_info, field_info, method_info (§5.7-§5.10) ──

    private static void writeClass(BinaryWriter out, ClassExport c, ConstantPool cp, boolean format23) {
        out.u1(c.token());
        out.u2(c.accessFlags());
        out.u2(cp.classref(c.name()));
        out.u2(c.supers().size());
        for (String s : c.supers()) out.u2(cp.classref(s));
        out.u1(checkU1(c.interfaces().size(), "export_interfaces_count of " + c.name()));
        for (String i : c.interfaces()) out.u2(cp.classref(i));
        out.u2(c.fields().size());
        for (FieldExport f : c.fields()) writeField(out, f, cp);
        out.u2(c.methods().size());
        for (MethodExport m : c.methods()) {
            out.u1(m.token());
            out.u2(m.accessFlags());
            out.u2(cp.utf8(m.name()));
            out.u2(cp.utf8(m.descriptor()));
        }
        if (format23) out.u1(c.cap22InheritableCount());
    }

    private static void writeField(BinaryWriter out, FieldExport f, ConstantPool cp) {
        out.u1(f.token());
        out.u2(f.accessFlags());
        out.u2(cp.utf8(f.name()));
        out.u2(cp.utf8(f.descriptor()));
        if (f.constantValue() == null) {
            out.u2(0);
            return;
        }
        out.u2(1);                              // §5.10.1 ConstantValue_attribute
        out.u2(cp.utf8(CONSTANT_VALUE));
        out.u4(2);
        out.u2(cp.integer(f.constantValue()));
    }

    private static int checkU1(int value, String item) {
        if (value > 0xFF) {
            throw new IllegalArgumentException(item + " is " + value + ", at most 255 (JCVM 3.1 §5.5, §5.7)");
        }
        return value;
    }
}
