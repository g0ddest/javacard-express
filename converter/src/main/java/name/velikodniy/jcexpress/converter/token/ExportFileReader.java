package name.velikodniy.jcexpress.converter.token;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Deserializes Java Card export ({@code .exp}) files from their binary format into
 * {@link ExportFile} instances.
 *
 * <p>This class is used during <b>Stage 1 (Load)</b> of the converter pipeline to read
 * the export files of imported packages. The resulting {@link ExportFile} objects are then
 * consumed in <b>Stage 3 (Token Assignment)</b> by {@link TokenAssigner} to resolve
 * virtual method tokens from external superclasses, and in <b>Stage 4 (Reference
 * Resolution)</b> to map symbolic references to imported classes/methods/fields into
 * their token-based equivalents.
 *
 * <h2>Binary Format (JCVM 3.0.5, Section 4.4)</h2>
 *
 * <pre>
 * export_file {
 *   u4 magic = 0x00FACADE
 *   u1 minor_version
 *   u1 major_version
 *   u2 constant_pool_count
 *   cp_info constant_pool[constant_pool_count]
 *   u2 this_package          // CP index → CONSTANT_Package
 *   u1 export_class_count
 *   class_export_info classes[export_class_count]
 * }
 * </pre>
 *
 * <h2>Constant Pool Tags</h2>
 *
 * <p>The export file constant pool supports the following tag types:
 * <ul>
 *   <li>{@code CONSTANT_Utf8} (1) -- UTF-8 encoded strings for names and descriptors</li>
 *   <li>{@code CONSTANT_Integer} (3) -- 32-bit integer constants</li>
 *   <li>{@code CONSTANT_Classref} (7) -- reference to a class name (u2 name index)</li>
 *   <li>{@code CONSTANT_Package} (13) -- package entry with AID, version, and name</li>
 * </ul>
 *
 * <p>Index 0 of the constant pool is unused (following the JVM convention).
 *
 * @see ExportFile
 * @see name.velikodniy.jcexpress.converter.exp.ExportFileWriter ExportFileWriter
 */
public final class ExportFileReader {

    private ExportFileReader() {}

    /**
     * Magic number identifying a valid Java Card export file ({@code 0x00FACADE}).
     *
     * <p>This value occupies the first four bytes of every {@code .exp} file and is
     * validated during parsing. It is also used by
     * {@link name.velikodniy.jcexpress.converter.exp.ExportFileWriter ExportFileWriter}
     * when generating new export files.
     */
    public static final int EXP_MAGIC = 0x00FACADE;

    // Constant pool tags
    private static final int CP_UTF8 = 1;
    private static final int CP_INTEGER = 3;
    private static final int CP_CLASSREF = 7;
    private static final int CP_PACKAGE = 13;

    /**
     * Parses an export file from raw bytes.
     *
     * @param data the complete binary content of a {@code .exp} file
     * @return the parsed {@link ExportFile} containing package metadata, class tokens,
     *         method tokens, and field tokens
     * @throws IOException if the magic number is invalid or the data is malformed
     */
    public static ExportFile read(byte[] data) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(data);
        return parse(buf);
    }

    /**
     * Reads and parses an export file from the filesystem.
     *
     * <p>This is a convenience method that reads all bytes from the given path and
     * delegates to {@link #read(byte[])}.
     *
     * @param path filesystem path to the {@code .exp} file
     * @return the parsed {@link ExportFile}
     * @throws IOException if the file cannot be read or the content is malformed
     */
    public static ExportFile readFile(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    private static ExportFile parse(ByteBuffer buf) throws IOException {
        int magic = buf.getInt();
        if (magic != EXP_MAGIC) {
            throw new IOException("Invalid export file: expected magic 0x00FACADE, got 0x"
                    + Integer.toHexString(magic));
        }

        int minorVersion = Byte.toUnsignedInt(buf.get());
        int majorVersion = Byte.toUnsignedInt(buf.get());

        // Read constant pool
        int cpCount = Short.toUnsignedInt(buf.getShort());
        Object[] cp = new Object[cpCount];

        int cpStart = (majorVersion >= 2) ? 0 : 1;
        for (int i = cpStart; i < cpCount; i++) {
            int tag = Byte.toUnsignedInt(buf.get());
            switch (tag) {
                case CP_UTF8 -> {
                    int len = Short.toUnsignedInt(buf.getShort());
                    byte[] bytes = new byte[len];
                    buf.get(bytes);
                    cp[i] = new String(bytes, StandardCharsets.UTF_8);
                }
                case CP_INTEGER -> cp[i] = buf.getInt();
                case CP_CLASSREF -> cp[i] = Short.toUnsignedInt(buf.getShort());
                case CP_PACKAGE -> cp[i] = readPackageEntry(buf);
                default -> throw new IOException("Unknown constant pool tag: " + tag);
            }
        }

        // this_package
        int thisPackageIdx = Short.toUnsignedInt(buf.getShort());
        PackageEntry pkgEntry = (PackageEntry) cp[thisPackageIdx];
        String packageName = (String) cp[pkgEntry.nameIndex()];

        // Classes
        int classCount = Byte.toUnsignedInt(buf.get());
        List<ExportFile.ClassExport> classes = new ArrayList<>(classCount);

        boolean v2Format = majorVersion >= 2;
        for (int c = 0; c < classCount; c++) {
            classes.add(readClassExport(buf, cp, v2Format));
        }

        return new ExportFile(
                packageName, pkgEntry.aid(),
                majorVersion, minorVersion,
                List.copyOf(classes)
        );
    }

    private static PackageEntry readPackageEntry(ByteBuffer buf) {
        int flags = Byte.toUnsignedInt(buf.get());
        int nameIndex = Short.toUnsignedInt(buf.getShort());
        int minor = Byte.toUnsignedInt(buf.get());
        int major = Byte.toUnsignedInt(buf.get());
        int aidLen = Byte.toUnsignedInt(buf.get());
        byte[] aid = new byte[aidLen];
        buf.get(aid);
        return new PackageEntry(nameIndex, minor, major, aid);
    }

    private static ExportFile.ClassExport readClassExport(ByteBuffer buf, Object[] cp,
                                                          boolean v2Format) {
        // Format 2.x uses u1 tokens; format 1.x uses u2
        int classToken = v2Format
                ? Byte.toUnsignedInt(buf.get())
                : Short.toUnsignedInt(buf.getShort());
        int accessFlags = Short.toUnsignedInt(buf.getShort());
        int nameIndex = Short.toUnsignedInt(buf.getShort());
        String className = resolveString(cp, nameIndex);

        // Supers
        int supersCount = Short.toUnsignedInt(buf.getShort());
        for (int s = 0; s < supersCount; s++) buf.getShort();

        // Interfaces
        int ifaceCount = Byte.toUnsignedInt(buf.get());
        for (int i = 0; i < ifaceCount; i++) buf.getShort();

        // Fields
        int fieldCount = Short.toUnsignedInt(buf.getShort());
        List<ExportFile.FieldExport> fields = new ArrayList<>(fieldCount);
        for (int f = 0; f < fieldCount; f++) {
            int fToken = v2Format
                    ? Byte.toUnsignedInt(buf.get())
                    : Short.toUnsignedInt(buf.getShort());
            int fFlags = Short.toUnsignedInt(buf.getShort());
            int fName = Short.toUnsignedInt(buf.getShort());
            int fDesc = Short.toUnsignedInt(buf.getShort());
            // Format 2.x fields have an attributes section (e.g., ConstantValue)
            if (v2Format) {
                int attrCount = Short.toUnsignedInt(buf.getShort());
                for (int a = 0; a < attrCount; a++) {
                    buf.getShort(); // attribute_name_index
                    int attrLen = buf.getInt(); // attribute_length
                    buf.position(buf.position() + attrLen); // skip attribute data
                }
            }
            fields.add(new ExportFile.FieldExport(
                    resolveString(cp, fName),
                    resolveString(cp, fDesc),
                    fToken, fFlags));
        }

        // Methods
        int methodCount = Short.toUnsignedInt(buf.getShort());
        List<ExportFile.MethodExport> methods = new ArrayList<>(methodCount);
        for (int m = 0; m < methodCount; m++) {
            int mToken = v2Format
                    ? Byte.toUnsignedInt(buf.get())
                    : Short.toUnsignedInt(buf.getShort());
            int mFlags = Short.toUnsignedInt(buf.getShort());
            int mName = Short.toUnsignedInt(buf.getShort());
            int mDesc = Short.toUnsignedInt(buf.getShort());
            methods.add(new ExportFile.MethodExport(
                    resolveString(cp, mName),
                    resolveString(cp, mDesc),
                    mToken, mFlags));
        }

        return new ExportFile.ClassExport(
                className, classToken, accessFlags,
                List.copyOf(methods), List.copyOf(fields));
    }

    private static String resolveString(Object[] cp, int index) {
        Object entry = cp[index];
        if (entry instanceof String s) return s;
        // CLASSREF → resolve name index
        if (entry instanceof Integer nameIdx) return (String) cp[nameIdx];
        return entry.toString();
    }

    private record PackageEntry(int nameIndex, int minorVersion, int majorVersion, byte[] aid) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o instanceof PackageEntry(var ni, var min, var maj, var a)) {
                return nameIndex == ni && minorVersion == min
                        && majorVersion == maj && Arrays.equals(aid, a);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int result = Integer.hashCode(nameIndex);
            result = 31 * result + Integer.hashCode(minorVersion);
            result = 31 * result + Integer.hashCode(majorVersion);
            result = 31 * result + Arrays.hashCode(aid);
            return result;
        }

        @Override
        public String toString() {
            return "PackageEntry[nameIndex=" + nameIndex + ", minorVersion=" + minorVersion
                    + ", majorVersion=" + majorVersion + ", aid=" + HexFormat.of().formatHex(aid) + "]";
        }
    }
}
