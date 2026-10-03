package name.velikodniy.jcexpress.converter.token;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Deserializes Java Card export ({@code .exp}) files into {@link ExportFile} instances,
 * following JCVM 3.1 Chapter 5 ("The Export File Format").
 *
 * <p>Supported export file formats are 2.0 to 2.3 (header {@code major_version} 2). Format
 * 2.3 adds {@code referenced_packages[]} after {@code this_package} (§5.5) and a trailing
 * {@code u1 CAP22_inheritable_public_method_token_count} in every {@code class_info} (§5.7).
 * Files with another major version or a newer minor version are rejected with an
 * {@link IOException}, as are truncated files, trailing data, dangling constant-pool indices
 * and AIDs outside 5..16 bytes: an export file that cannot be read exactly must never yield
 * silently wrong tokens.
 *
 * <pre>
 * ExportFile {
 *   u4 magic = 0x00FACADE
 *   u1 minor_version, u1 major_version          // export FILE FORMAT version (§5.5)
 *   u2 constant_pool_count
 *   cp_info constant_pool[constant_pool_count]  // entry 0 is a real entry (§5.5)
 *   u2 this_package                             // CONSTANT_Package: PACKAGE version (§5.6.1)
 *   u1 referenced_package_count                 // format 2.3 only
 *   u2 referenced_packages[referenced_package_count]
 *   u1 export_class_count
 *   class_info classes[export_class_count]      // §5.7
 * }
 * </pre>
 *
 * @see ExportFile
 * @see name.velikodniy.jcexpress.converter.exp.ExportFileWriter ExportFileWriter
 */
public final class ExportFileReader {

    /**
     * Magic number identifying a Java Card export file ({@code 0x00FACADE}, JCVM 3.1 §5.5).
     */
    public static final int EXP_MAGIC = 0x00FACADE;

    /** The only export file format major version defined by JCVM 3.1 §5.5. */
    public static final int SUPPORTED_FORMAT_MAJOR = 2;

    /** The newest export file format minor version defined by JCVM 3.1 §5.5. */
    public static final int MAX_FORMAT_MINOR = 3;

    private static final int CP_UTF8 = 1;
    private static final int CP_INTEGER = 3;
    private static final int CP_CLASSREF = 7;
    private static final int CP_PACKAGE = 13;

    private ExportFileReader() {}

    /**
     * Parses an export file from raw bytes.
     *
     * @param data the complete binary content of a {@code .exp} file
     * @return the parsed {@link ExportFile}
     * @throws IOException if the data is not a well-formed export file of a supported format
     */
    public static ExportFile read(byte[] data) throws IOException {
        return new Parser(new Cursor(data)).parse();
    }

    /**
     * Reads and parses an export file from the filesystem.
     *
     * @param path filesystem path to the {@code .exp} file
     * @return the parsed {@link ExportFile}
     * @throws IOException if the file cannot be read or is malformed; the message names the file
     */
    public static ExportFile readFile(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        try {
            return read(data);
        } catch (IOException e) {
            throw new IOException("Cannot read export file " + path + ": " + e.getMessage(), e);
        }
    }

    // ── constant pool model ──

    private sealed interface CpInfo permits Utf8, IntegerConst, ClassRef, PackageRef {}

    private record Utf8(String value) implements CpInfo {}

    private record IntegerConst(int value) implements CpInfo {}

    private record ClassRef(int nameIndex) implements CpInfo {}

    private record PackageRef(int flags, int nameIndex, int minor, int major, byte[] aid) implements CpInfo {}

    /** Parses one export file; holds the constant pool while class entries are decoded. */
    private static final class Parser {
        private final Cursor in;
        private CpInfo[] cp;

        Parser(Cursor in) {
            this.in = in;
        }

        ExportFile parse() throws IOException {
            if (in.u4("magic") != EXP_MAGIC) {
                throw new IOException("Invalid export file: expected magic 0x00FACADE");
            }
            int formatMinor = in.u1("minor_version");
            int formatMajor = in.u1("major_version");
            checkFormat(formatMajor, formatMinor);
            readConstantPool();
            PackageRef pkg = packageAt(in.u2("this_package"));
            List<ExportFile.PackageReference> referenced = new ArrayList<>();
            if (formatMinor >= 3) {
                int count = in.u1("referenced_package_count");
                for (int i = 0; i < count; i++) {
                    PackageRef ref = packageAt(in.u2("referenced_packages"));
                    referenced.add(new ExportFile.PackageReference(utf8At(ref.nameIndex()), ref.aid(),
                            ref.major(), ref.minor()));
                }
            }
            int classCount = in.u1("export_class_count");
            List<ExportFile.ClassExport> classes = new ArrayList<>(classCount);
            for (int c = 0; c < classCount; c++) {
                classes.add(readClass(formatMinor >= 3));
            }
            in.expectEnd();
            return new ExportFile(utf8At(pkg.nameIndex()), pkg.aid(), pkg.major(), pkg.minor(),
                    classes, pkg.flags(), formatMajor, formatMinor, referenced);
        }

        private static void checkFormat(int major, int minor) throws IOException {
            if (major != SUPPORTED_FORMAT_MAJOR || minor > MAX_FORMAT_MINOR) {
                throw new IOException("Export file format " + major + "." + minor
                        + " is not supported (supported: 2.0-2.3, JCVM 3.1 §5.5)");
            }
        }

        private void readConstantPool() throws IOException {
            int count = in.u2("constant_pool_count");
            cp = new CpInfo[count];
            for (int i = 0; i < count; i++) {
                cp[i] = readCpEntry(i);
            }
        }

        private CpInfo readCpEntry(int index) throws IOException {
            int tag = in.u1("constant_pool tag");
            return switch (tag) {
                case CP_UTF8 -> new Utf8(modifiedUtf8(in.bytes(in.u2("utf8 length"), "utf8 bytes"), index));
                case CP_INTEGER -> new IntegerConst(in.u4("integer"));
                case CP_CLASSREF -> new ClassRef(in.u2("classref name_index"));
                case CP_PACKAGE -> readPackage();
                default -> throw new IOException("Unknown constant pool tag " + tag
                        + " at entry " + index + " (offset " + (in.offset() - 1) + ")");
            };
        }

        /** §5.6.4: strings are encoded as in class files (modified UTF-8, JVMS §4.4.7). */
        private String modifiedUtf8(byte[] bytes, int index) throws IOException {
            byte[] framed = new byte[bytes.length + 2];
            framed[0] = (byte) (bytes.length >> 8);
            framed[1] = (byte) bytes.length;
            System.arraycopy(bytes, 0, framed, 2, bytes.length);
            try {
                return new DataInputStream(new ByteArrayInputStream(framed)).readUTF();
            } catch (UTFDataFormatException e) {
                throw new IOException("Constant pool entry " + index + " is not a valid CONSTANT_Utf8 string"
                        + " (JCVM 3.1 §5.6.4) near offset " + in.offset(), e);
            }
        }

        private PackageRef readPackage() throws IOException {
            int flags = in.u1("package flags");
            int nameIndex = in.u2("package name_index");
            int minor = in.u1("package minor_version");
            int major = in.u1("package major_version");
            int aidLength = in.u1("aid_length");
            if (aidLength < 5 || aidLength > 16) {
                throw new IOException("Invalid package AID length " + aidLength
                        + " (must be 5..16, JCVM 3.1 §5.6.1) at offset " + (in.offset() - 1));
            }
            return new PackageRef(flags, nameIndex, minor, major, in.bytes(aidLength, "aid"));
        }

        private ExportFile.ClassExport readClass(boolean format23) throws IOException {
            int token = in.u1("class token");
            int flags = in.u2("class access_flags");
            String name = classNameAt(in.u2("class name_index"));
            List<String> supers = readClassRefs(in.u2("export_supers_count"));
            List<String> interfaces = readClassRefs(in.u1("export_interfaces_count"));
            List<ExportFile.FieldExport> fields = readFields();
            List<ExportFile.MethodExport> methods = readMethods();
            if (format23) {
                int cap22Count = in.u1("CAP22_inheritable_public_method_token_count");
                return new ExportFile.ClassExport(name, token, flags, methods, fields, supers, interfaces,
                        cap22Count);
            }
            return new ExportFile.ClassExport(name, token, flags, methods, fields, supers, interfaces);
        }

        private List<String> readClassRefs(int count) throws IOException {
            List<String> names = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                names.add(classNameAt(in.u2("classref index")));
            }
            return names;
        }

        private List<ExportFile.FieldExport> readFields() throws IOException {
            int count = in.u2("export_fields_count");
            List<ExportFile.FieldExport> fields = new ArrayList<>(count);
            for (int f = 0; f < count; f++) {
                int token = in.u1("field token");
                int flags = in.u2("field access_flags");
                String name = utf8At(in.u2("field name_index"));
                String descriptor = utf8At(in.u2("field descriptor_index"));
                Integer constant = readFieldAttributes();
                fields.add(new ExportFile.FieldExport(name, descriptor, token, flags, constant));
            }
            return fields;
        }

        /** Reads a field's attributes (§5.8, §5.10); returns the ConstantValue, if any. */
        private Integer readFieldAttributes() throws IOException {
            Integer constant = null;
            int count = in.u2("attributes_count");
            for (int a = 0; a < count; a++) {
                String attrName = utf8At(in.u2("attribute_name_index"));
                int length = in.u4("attribute_length");
                if ("ConstantValue".equals(attrName) && length == 2) {
                    constant = integerAt(in.u2("constantvalue_index"));
                } else {
                    in.bytes(length, "attribute " + attrName);
                }
            }
            return constant;
        }

        private List<ExportFile.MethodExport> readMethods() throws IOException {
            int count = in.u2("export_methods_count");
            List<ExportFile.MethodExport> methods = new ArrayList<>(count);
            for (int m = 0; m < count; m++) {
                int token = in.u1("method token");
                int flags = in.u2("method access_flags");
                String name = utf8At(in.u2("method name_index"));
                String descriptor = utf8At(in.u2("method descriptor_index"));
                methods.add(new ExportFile.MethodExport(name, descriptor, token, flags));
            }
            return methods;
        }

        private CpInfo entry(int index, String expected) throws IOException {
            if (index < 0 || index >= cp.length) {
                throw new IOException("Constant pool index " + index + " out of range (count "
                        + cp.length + ") for " + expected + " near offset " + in.offset());
            }
            return cp[index];
        }

        private String utf8At(int index) throws IOException {
            if (entry(index, "CONSTANT_Utf8") instanceof Utf8(String value)) return value;
            throw wrongType(index, "CONSTANT_Utf8");
        }

        private int integerAt(int index) throws IOException {
            if (entry(index, "CONSTANT_Integer") instanceof IntegerConst(int value)) return value;
            throw wrongType(index, "CONSTANT_Integer");
        }

        private String classNameAt(int index) throws IOException {
            if (entry(index, "CONSTANT_Classref") instanceof ClassRef(int nameIndex)) return utf8At(nameIndex);
            throw wrongType(index, "CONSTANT_Classref");
        }

        private PackageRef packageAt(int index) throws IOException {
            if (entry(index, "CONSTANT_Package") instanceof PackageRef p) return p;
            throw wrongType(index, "CONSTANT_Package");
        }

        private IOException wrongType(int index, String expected) {
            return new IOException("Constant pool entry " + index + " is "
                    + cp[index].getClass().getSimpleName() + ", expected " + expected
                    + " (near offset " + in.offset() + ")");
        }
    }

    /** Bounds-checked big-endian reader that reports offsets in its error messages. */
    private static final class Cursor {
        private final byte[] data;
        private int pos;

        Cursor(byte[] data) {
            this.data = data;
        }

        int offset() {
            return pos;
        }

        private void require(int n, String item) throws IOException {
            if (pos + n > data.length) {
                throw new IOException("Malformed export file: truncated at offset " + pos
                        + " while reading " + item + " (" + n + " byte(s) needed, "
                        + (data.length - pos) + " left)");
            }
        }

        int u1(String item) throws IOException {
            require(1, item);
            return data[pos++] & 0xFF;
        }

        int u2(String item) throws IOException {
            require(2, item);
            int v = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
            pos += 2;
            return v;
        }

        int u4(String item) throws IOException {
            require(4, item);
            int v = ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                    | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
            pos += 4;
            return v;
        }

        byte[] bytes(int n, String item) throws IOException {
            if (n < 0) {
                throw new IOException("Malformed export file: negative length for " + item
                        + " at offset " + pos);
            }
            require(n, item);
            byte[] b = new byte[n];
            System.arraycopy(data, pos, b, 0, n);
            pos += n;
            return b;
        }

        void expectEnd() throws IOException {
            if (pos != data.length) {
                throw new IOException("Malformed export file: " + (data.length - pos)
                        + " unexpected trailing byte(s) after offset " + pos);
            }
        }
    }
}
