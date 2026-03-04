package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.cap.BinaryWriter;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import name.velikodniy.jcexpress.converter.token.TokenMap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Generates JavaCard export (.exp) files in binary format.
 * <p>
 * Export files allow other packages to import and reference
 * public classes, methods, and fields from this package.
 * The format uses a constant pool similar to JVM class files.
 * <p>
 * Binary format:
 * <pre>
 * u4  magic = 0x00FACADE
 * u1  minor_version
 * u1  major_version = 2
 * u2  constant_pool_count
 * cp_info constant_pool[]
 * u2  this_package          (CP index)
 * u1  export_class_count
 * class_export_info classes[]
 * </pre>
 *
 * @see ExportFileReader
 */
public final class ExportFileWriter {

    private static final int CP_UTF8 = 1;
    private static final int CP_PACKAGE = 13;

    private ExportFileWriter() {}

    /**
     * Generates an export file for the given package.
     *
     * @param tokenMap        token assignments
     * @param classes         class info list (for access flags)
     * @param packageAid      package AID bytes
     * @param majorVersion    package major version
     * @param minorVersion    package minor version
     * @return binary export file data
     */
    @SuppressWarnings("java:S3776") // Inherently complex export file binary generation
    public static byte[] write(TokenMap tokenMap, List<ClassInfo> classes,
                                byte[] packageAid, int majorVersion, int minorVersion) {
        // Build constant pool entries
        List<CpEntry> cpEntries = new ArrayList<>();
        cpEntries.add(null); // index 0 is unused

        // CP[1]: package name (UTF8)
        String packageName = tokenMap.packageName().replace('.', '/');
        int pkgNameIdx = addUtf8(cpEntries, packageName);

        // CP[2]: package entry
        int pkgEntryIdx = cpEntries.size();
        cpEntries.add(new CpPackage(pkgNameIdx, majorVersion, minorVersion, packageAid));

        // Add class names, method names, method descriptors, field names, field descriptors
        List<ClassExportInfo> classExports = new ArrayList<>();

        for (TokenMap.ClassEntry ce : tokenMap.classes()) {
            ClassInfo classInfo = findClassInfo(classes, ce.internalName());

            int classNameIdx = addUtf8(cpEntries, simpleName(ce.internalName()));
            int accessFlags = classInfo != null ? classInfo.accessFlags() : 0x0001;

            List<MethodExportInfo> methods = new ArrayList<>();
            for (TokenMap.MethodEntry me : ce.virtualMethods()) {
                int nameIdx = addUtf8(cpEntries, me.name());
                int descIdx = addUtf8(cpEntries, me.descriptor());
                MethodInfo mi = classInfo != null ? findMethod(classInfo, me.name(), me.descriptor()) : null;
                int mFlags = mi != null ? mi.accessFlags() : 0x0001;
                methods.add(new MethodExportInfo(me.token(), mFlags, nameIdx, descIdx));
            }
            for (TokenMap.MethodEntry me : ce.staticMethods()) {
                int nameIdx = addUtf8(cpEntries, me.name());
                int descIdx = addUtf8(cpEntries, me.descriptor());
                MethodInfo mi = classInfo != null ? findMethod(classInfo, me.name(), me.descriptor()) : null;
                int mFlags = mi != null ? (mi.accessFlags() | 0x0008) : 0x0009;
                methods.add(new MethodExportInfo(me.token(), mFlags, nameIdx, descIdx));
            }

            List<FieldExportInfo> fields = new ArrayList<>();
            for (TokenMap.FieldEntry fe : ce.staticFields()) {
                int nameIdx = addUtf8(cpEntries, fe.name());
                int descIdx = addUtf8(cpEntries, fe.descriptor());
                FieldInfo fi = classInfo != null ? findField(classInfo, fe.name()) : null;
                int fFlags = fi != null ? fi.accessFlags() : 0x0009;
                fields.add(new FieldExportInfo(fe.token(), fFlags, nameIdx, descIdx));
            }

            classExports.add(new ClassExportInfo(ce.token(), accessFlags, classNameIdx, methods, fields));
        }

        // Write binary output
        var out = new BinaryWriter();

        // Header
        out.u4(ExportFileReader.EXP_MAGIC);
        out.u1(minorVersion);
        out.u1(majorVersion);

        // Constant pool
        out.u2(cpEntries.size());
        for (int i = 1; i < cpEntries.size(); i++) {
            CpEntry entry = cpEntries.get(i);
            entry.writeTo(out);
        }

        // this_package
        out.u2(pkgEntryIdx);

        // Classes
        out.u1(classExports.size());
        for (ClassExportInfo cei : classExports) {
            out.u2(cei.token);
            out.u2(cei.accessFlags);
            out.u2(cei.nameIndex);

            // Supers count (0 for simplicity)
            out.u2(0);

            // Interfaces count (0 for simplicity)
            out.u1(0);

            // Fields
            out.u2(cei.fields.size());
            for (FieldExportInfo fei : cei.fields) {
                out.u2(fei.token);
                out.u2(fei.accessFlags);
                out.u2(fei.nameIndex);
                out.u2(fei.descriptorIndex);
            }

            // Methods
            out.u2(cei.methods.size());
            for (MethodExportInfo mei : cei.methods) {
                out.u2(mei.token);
                out.u2(mei.accessFlags);
                out.u2(mei.nameIndex);
                out.u2(mei.descriptorIndex);
            }
        }

        return out.toByteArray();
    }

    // ── CP helpers ──

    private static int addUtf8(List<CpEntry> cp, String value) {
        // Check for existing entry
        for (int i = 1; i < cp.size(); i++) {
            CpEntry e = cp.get(i);
            if (e instanceof CpUtf8 u && u.value.equals(value)) {
                return i;
            }
        }
        int idx = cp.size();
        cp.add(new CpUtf8(value));
        return idx;
    }

    private static String simpleName(String internalName) {
        int last = internalName.lastIndexOf('/');
        return last >= 0 ? internalName.substring(last + 1) : internalName;
    }

    private static ClassInfo findClassInfo(List<ClassInfo> classes, String internalName) {
        for (ClassInfo ci : classes) {
            if (ci.thisClass().equals(internalName)) return ci;
        }
        return null;
    }

    private static MethodInfo findMethod(ClassInfo ci, String name, String desc) {
        for (MethodInfo mi : ci.methods()) {
            if (mi.name().equals(name) && mi.descriptor().equals(desc)) return mi;
        }
        return null;
    }

    private static FieldInfo findField(ClassInfo ci, String name) {
        for (FieldInfo fi : ci.fields()) {
            if (fi.name().equals(name)) return fi;
        }
        return null;
    }

    // ── CP entry types ──

    private sealed interface CpEntry {
        void writeTo(BinaryWriter out);
    }

    private record CpUtf8(String value) implements CpEntry {
        @Override
        public void writeTo(BinaryWriter out) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.u1(CP_UTF8);
            out.u2(bytes.length);
            out.bytes(bytes);
        }
    }

    private record CpPackage(int nameIndex, int majorVersion, int minorVersion, byte[] aid) implements CpEntry {
        @Override
        public void writeTo(BinaryWriter out) {
            out.u1(CP_PACKAGE);
            out.u1(0); // flags
            out.u2(nameIndex);
            out.u1(minorVersion);
            out.u1(majorVersion);
            out.u1(aid.length);
            out.bytes(aid);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof CpPackage(var ni, var maj, var min, var a)) {
                return nameIndex == ni && majorVersion == maj
                        && minorVersion == min && Arrays.equals(aid, a);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(nameIndex);
            result = 31 * result + Integer.hashCode(majorVersion);
            result = 31 * result + Integer.hashCode(minorVersion);
            result = 31 * result + Arrays.hashCode(aid);
            return result;
        }

        @Override
        public String toString() {
            return "CpPackage[nameIndex=" + nameIndex + ", majorVersion=" + majorVersion
                    + ", minorVersion=" + minorVersion + ", aid=" + HexFormat.of().formatHex(aid) + "]";
        }
    }

    private record ClassExportInfo(int token, int accessFlags, int nameIndex,
                                    List<MethodExportInfo> methods, List<FieldExportInfo> fields) {}

    private record MethodExportInfo(int token, int accessFlags, int nameIndex, int descriptorIndex) {}

    private record FieldExportInfo(int token, int accessFlags, int nameIndex, int descriptorIndex) {}
}
