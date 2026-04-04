package name.velikodniy.jcexpress.converter.exp;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
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
 * Supports both format 1.x and 2.x, determined by {@link JavaCardVersion#exportFormatMajor()}.
 * All current JavaCard versions produce format 2.1.
 * <p>
 * Key differences between formats:
 * <ul>
 *   <li>Format 1.x: CP starts at index 1, tokens are u2, no field attributes</li>
 *   <li>Format 2.x: CP starts at index 0, tokens are u1, field attributes present</li>
 * </ul>
 *
 * @see ExportFileReader
 */
public final class ExportFileWriter {

    private static final int CP_UTF8 = 1;
    private static final int CP_INTEGER = 3;
    private static final int CP_CLASSREF = 7;
    private static final int CP_PACKAGE = 13;

    private ExportFileWriter() {}

    /**
     * Generates an export file for the given package.
     *
     * @param tokenMap              token assignments
     * @param classes               class info list (for access flags)
     * @param packageAid            package AID bytes
     * @param packageMajorVersion   package major version (written into CONSTANT_Package)
     * @param packageMinorVersion   package minor version (written into CONSTANT_Package)
     * @param jcVersion             target JavaCard version (determines export format version)
     * @return binary export file data
     */
    @SuppressWarnings("java:S3776") // Inherently complex export file binary generation
    public static byte[] write(TokenMap tokenMap, List<ClassInfo> classes,
                                byte[] packageAid, int packageMajorVersion,
                                int packageMinorVersion, JavaCardVersion jcVersion) {
        boolean v2 = jcVersion.exportFormatMajor() >= 2;

        // Build constant pool entries
        List<CpEntry> cpEntries = new ArrayList<>();
        if (!v2) {
            cpEntries.add(null); // v1: index 0 unused
        }

        // Package name (UTF8)
        String packageName = tokenMap.packageName().replace('.', '/');
        int pkgNameIdx = addUtf8(cpEntries, packageName, v2);

        // Package entry
        int pkgEntryIdx = cpEntries.size();
        cpEntries.add(new CpPackage(pkgNameIdx, packageMajorVersion, packageMinorVersion, packageAid));

        // Build class export data, populating CP as we go
        List<ClassExportInfo> classExports = new ArrayList<>();

        for (TokenMap.ClassEntry ce : tokenMap.classes()) {
            ClassInfo classInfo = findClassInfo(classes, ce.internalName());

            int classNameIdx = addClassref(cpEntries, ce.internalName(), v2);
            // Export file access flags: strip JVM-only flags like ACC_SUPER(0x0020)
            int accessFlags = classInfo != null ? (classInfo.accessFlags() & 0x0611) : 0x0001;

            // Build super class chain (direct parent → java.lang.Object)
            List<Integer> superRefs = new ArrayList<>();
            if (classInfo != null && classInfo.superClass() != null) {
                buildSuperChain(classInfo, classes, cpEntries, v2, superRefs);
            }

            List<MethodExportInfo> methods = new ArrayList<>();
            for (TokenMap.MethodEntry me : ce.virtualMethods()) {
                int nameIdx = addUtf8(cpEntries, me.name(), v2);
                int descIdx = addUtf8(cpEntries, me.descriptor(), v2);
                MethodInfo mi = classInfo != null ? findMethod(classInfo, me.name(), me.descriptor()) : null;
                int mFlags = mi != null ? mi.accessFlags() : 0x0001;
                methods.add(new MethodExportInfo(me.token(), mFlags, nameIdx, descIdx));
            }
            for (TokenMap.MethodEntry me : ce.staticMethods()) {
                int nameIdx = addUtf8(cpEntries, me.name(), v2);
                int descIdx = addUtf8(cpEntries, me.descriptor(), v2);
                MethodInfo mi = classInfo != null ? findMethod(classInfo, me.name(), me.descriptor()) : null;
                int mFlags = mi != null ? (mi.accessFlags() | 0x0008) : 0x0009;
                methods.add(new MethodExportInfo(me.token(), mFlags, nameIdx, descIdx));
            }

            List<FieldExportInfo> fields = new ArrayList<>();
            for (TokenMap.FieldEntry fe : ce.staticFields()) {
                int nameIdx = addUtf8(cpEntries, fe.name(), v2);
                int descIdx = addUtf8(cpEntries, fe.descriptor(), v2);
                FieldInfo fi = classInfo != null ? findField(classInfo, fe.name()) : null;
                int fFlags = fi != null ? fi.accessFlags() : 0x0009;
                // For v2 ConstantValue attributes, pre-register CP entries
                int cvNameIdx = -1;
                int cvValueIdx = -1;
                if (v2 && fi != null && fi.constantValue() != null) {
                    cvNameIdx = addUtf8(cpEntries, "ConstantValue", v2);
                    cvValueIdx = addInteger(cpEntries, fi.constantValue());
                }
                fields.add(new FieldExportInfo(fe.token(), fFlags, nameIdx, descIdx,
                        cvNameIdx, cvValueIdx));
            }

            classExports.add(new ClassExportInfo(ce.token(), accessFlags, classNameIdx,
                    superRefs, methods, fields));
        }

        // Write binary output
        var out = new BinaryWriter();

        // Header: export FORMAT version, not package version
        out.u4(ExportFileReader.EXP_MAGIC);
        out.u1(jcVersion.exportFormatMinor());
        out.u1(jcVersion.exportFormatMajor());

        // Constant pool
        out.u2(cpEntries.size());
        int writeStart = v2 ? 0 : 1;
        for (int i = writeStart; i < cpEntries.size(); i++) {
            cpEntries.get(i).writeTo(out);
        }

        // this_package
        out.u2(pkgEntryIdx);

        // Classes
        out.u1(classExports.size());
        for (ClassExportInfo cei : classExports) {
            writeToken(out, cei.token, v2);
            out.u2(cei.accessFlags);
            out.u2(cei.nameIndex);

            // Supers
            out.u2(cei.superRefs.size());
            for (int superRef : cei.superRefs) {
                out.u2(superRef);
            }

            // Interfaces count
            out.u1(0);

            // Fields
            out.u2(cei.fields.size());
            for (FieldExportInfo fei : cei.fields) {
                writeToken(out, fei.token, v2);
                out.u2(fei.accessFlags);
                out.u2(fei.nameIndex);
                out.u2(fei.descriptorIndex);
                // v2: field attributes
                if (v2) {
                    if (fei.cvNameIdx >= 0) {
                        out.u2(1); // attribute_count = 1
                        out.u2(fei.cvNameIdx);
                        out.u4(2); // attribute_length = 2
                        out.u2(fei.cvValueIdx);
                    } else {
                        out.u2(0); // attribute_count = 0
                    }
                }
            }

            // Methods
            out.u2(cei.methods.size());
            for (MethodExportInfo mei : cei.methods) {
                writeToken(out, mei.token, v2);
                out.u2(mei.accessFlags);
                out.u2(mei.nameIndex);
                out.u2(mei.descriptorIndex);
            }
        }

        return out.toByteArray();
    }

    private static void writeToken(BinaryWriter out, int token, boolean v2) {
        if (v2) {
            out.u1(token);
        } else {
            out.u2(token);
        }
    }

    // ── CP helpers ──

    private static int addUtf8(List<CpEntry> cp, String value, boolean v2) {
        int start = v2 ? 0 : 1;
        for (int i = start; i < cp.size(); i++) {
            CpEntry e = cp.get(i);
            if (e instanceof CpUtf8 u && u.value.equals(value)) {
                return i;
            }
        }
        int idx = cp.size();
        cp.add(new CpUtf8(value));
        return idx;
    }

    private static int addClassref(List<CpEntry> cp, String className, boolean v2) {
        int utf8Idx = addUtf8(cp, className, v2);
        // Check for existing CLASSREF pointing to this UTF8
        for (int i = 0; i < cp.size(); i++) {
            if (cp.get(i) instanceof CpClassref cr && cr.nameIndex == utf8Idx) return i;
        }
        int idx = cp.size();
        cp.add(new CpClassref(utf8Idx));
        return idx;
    }

    private static int addInteger(List<CpEntry> cp, Object value) {
        if (value instanceof Number n) {
            int intVal = n.intValue();
            for (int i = 0; i < cp.size(); i++) {
                if (cp.get(i) instanceof CpInteger ci && ci.value == intVal) return i;
            }
            int idx = cp.size();
            cp.add(new CpInteger(intVal));
            return idx;
        }
        return 0;
    }

    private static void buildSuperChain(ClassInfo ci, List<ClassInfo> allClasses,
                                          List<CpEntry> cp, boolean v2, List<Integer> result) {
        String superName = ci.superClass();
        while (superName != null) {
            result.add(addClassref(cp, superName, v2));
            ClassInfo superInfo = findClassInfo(allClasses, superName);
            superName = (superInfo != null) ? superInfo.superClass() : null;
        }
        // Always end with java.lang.Object if not already there
        if (result.isEmpty() || !isJavaLangObject(cp, result.getLast())) {
            result.add(addClassref(cp, "java/lang/Object", v2));
        }
    }

    private static boolean isJavaLangObject(List<CpEntry> cp, int classrefIdx) {
        if (cp.get(classrefIdx) instanceof CpClassref cr) {
            if (cp.get(cr.nameIndex) instanceof CpUtf8 u) {
                return "java/lang/Object".equals(u.value);
            }
        }
        return false;
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

    private record CpInteger(int value) implements CpEntry {
        @Override
        public void writeTo(BinaryWriter out) {
            out.u1(CP_INTEGER);
            out.u4(value);
        }
    }

    private record CpClassref(int nameIndex) implements CpEntry {
        @Override
        public void writeTo(BinaryWriter out) {
            out.u1(CP_CLASSREF);
            out.u2(nameIndex);
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
                                    List<Integer> superRefs,
                                    List<MethodExportInfo> methods, List<FieldExportInfo> fields) {}

    private record MethodExportInfo(int token, int accessFlags, int nameIndex, int descriptorIndex) {}

    private record FieldExportInfo(int token, int accessFlags, int nameIndex, int descriptorIndex,
                                    int cvNameIdx, int cvValueIdx) {}
}
