package name.velikodniy.jcexpress.converter.capcheck;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Clean-room, test-only view of a CAP file, written from the public JCVM 3.1 specification
 * (Chapter 6) and independent of the converter's own serializers.
 *
 * <p>It is used by the converter test suite to parse generated CAP files with the layout the
 * specification mandates (for example the {@code class_info_compact} field order of §6.9.2),
 * so that tests assert spec behaviour instead of re-using the converter's assumptions.
 * Only the compact CAP formats 2.1, 2.2 and 2.3 are supported.
 */
public final class CapImage {

    /** Component tags (JCVM 3.1 §6.2, Table 6-2). */
    public static final int TAG_HEADER = 1;
    public static final int TAG_DIRECTORY = 2;
    public static final int TAG_APPLET = 3;
    public static final int TAG_IMPORT = 4;
    public static final int TAG_CONSTANT_POOL = 5;
    public static final int TAG_CLASS = 6;
    public static final int TAG_METHOD = 7;
    public static final int TAG_STATIC_FIELD = 8;
    public static final int TAG_REF_LOCATION = 9;
    public static final int TAG_EXPORT = 10;
    public static final int TAG_DESCRIPTOR = 11;

    private final Map<Integer, byte[]> bodies;
    private final int formatMajor;
    private final int formatMinor;
    private final int headerFlags;

    private CapImage(Map<Integer, byte[]> bodies) {
        this.bodies = bodies;
        byte[] header = requireBody(TAG_HEADER);
        this.formatMinor = header[4] & 0xFF;
        this.formatMajor = header[5] & 0xFF;
        this.headerFlags = header[6] & 0xFF;
    }

    /**
     * Parses a CAP file (JAR/ZIP archive) into its components.
     *
     * @param capFile CAP archive bytes
     * @return parsed view
     */
    public static CapImage parse(byte[] capFile) {
        Map<Integer, byte[]> bodies = new HashMap<>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(capFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".cap")) {
                    byte[] comp = zis.readAllBytes();
                    int size = ((comp[1] & 0xFF) << 8) | (comp[2] & 0xFF);
                    if (size != comp.length - 3) {
                        throw new IllegalArgumentException("Component " + entry.getName()
                                + ": size item " + size + " != body length " + (comp.length - 3));
                    }
                    bodies.put(comp[0] & 0xFF, Arrays.copyOfRange(comp, 3, comp.length));
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new CapImage(bodies);
    }

    /** @return {@code true} if the component with the given tag is present */
    public boolean has(int tag) {
        return bodies.containsKey(tag);
    }

    /**
     * Returns the component body (the {@code info} item, without tag and size).
     *
     * @param tag component tag
     * @return body bytes
     */
    public byte[] requireBody(int tag) {
        byte[] body = bodies.get(tag);
        if (body == null) {
            throw new IllegalStateException("Component with tag " + tag + " is missing");
        }
        return body;
    }

    /** @return CAP format major version (Header component, §6.4) */
    public int formatMajor() {
        return formatMajor;
    }

    /** @return CAP format minor version (Header component, §6.4) */
    public int formatMinor() {
        return formatMinor;
    }

    /** @return Header flags (ACC_INT 0x01, ACC_EXPORT 0x02, ACC_APPLET 0x04, §6.4 Table 6-3) */
    public int headerFlags() {
        return headerFlags;
    }

    /** @return {@code true} if the package defines applets (Applet component present) */
    public boolean hasApplets() {
        return has(TAG_APPLET);
    }

    /** @return {@code true} for CAP format 2.2 or later (signature pool in the Class component) */
    public boolean hasSignaturePool() {
        return formatMajor > 2 || (formatMajor == 2 && formatMinor >= 2);
    }

    /** @return {@code true} for CAP format 2.3 (token mapping tables in class_info) */
    public boolean isFormat23() {
        return formatMajor > 2 || (formatMajor == 2 && formatMinor >= 3);
    }

    /**
     * Parses the Constant Pool component (§6.8).
     *
     * @return constant pool entries in index order
     */
    public List<CpEntry> constantPool() {
        byte[] b = requireBody(TAG_CONSTANT_POOL);
        int count = u2(b, 0);
        List<CpEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int o = 2 + 4 * i;
            entries.add(new CpEntry(i, b[o] & 0xFF, b[o + 1] & 0xFF, b[o + 2] & 0xFF, b[o + 3] & 0xFF));
        }
        return List.copyOf(entries);
    }

    /** @return parsed Class component (§6.9) */
    public ClassComponentView classComponent() {
        return ClassComponentView.parse(requireBody(TAG_CLASS), hasSignaturePool(), isFormat23());
    }

    /** @return parsed Descriptor component (§6.14) */
    public DescriptorView descriptor() {
        return DescriptorView.parse(requireBody(TAG_DESCRIPTOR));
    }

    /**
     * Parses the Static Field component (§6.11).
     *
     * @return static field image description
     */
    public StaticFieldView staticField() {
        byte[] b = requireBody(TAG_STATIC_FIELD);
        int imageSize = u2(b, 0);
        int referenceCount = u2(b, 2);
        int arrayInitCount = u2(b, 4);
        int o = 6;
        List<ArrayInit> inits = new ArrayList<>(arrayInitCount);
        for (int i = 0; i < arrayInitCount; i++) {
            int type = b[o] & 0xFF;
            int count = u2(b, o + 1);
            inits.add(new ArrayInit(type, Arrays.copyOfRange(b, o + 3, o + 3 + count)));
            o += 3 + count;
        }
        int defaultValueCount = u2(b, o);
        int nonDefaultCount = u2(b, o + 2);
        byte[] nonDefault = Arrays.copyOfRange(b, o + 4, o + 4 + nonDefaultCount);
        if (o + 4 + nonDefaultCount != b.length) {
            throw new IllegalStateException("Static Field component has trailing bytes");
        }
        return new StaticFieldView(imageSize, referenceCount, List.copyOf(inits),
                defaultValueCount, nonDefault);
    }

    /**
     * Parses the exception handler table of the Method component (§6.10).
     *
     * @return handlers in table order
     */
    public List<Handler> exceptionHandlers() {
        byte[] b = requireBody(TAG_METHOD);
        int count = b[0] & 0xFF;
        List<Handler> handlers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int o = 1 + 8 * i;
            int bitfield = u2(b, o + 2);
            handlers.add(new Handler(u2(b, o), (bitfield & 0x8000) != 0, bitfield & 0x7FFF,
                    u2(b, o + 4), u2(b, o + 6)));
        }
        return List.copyOf(handlers);
    }

    /**
     * Parses the Export component (§6.13), if present.
     *
     * @return class exports, or an empty list when there is no Export component
     */
    public List<ClassExport> exports() {
        if (!has(TAG_EXPORT)) {
            return List.of();
        }
        byte[] b = requireBody(TAG_EXPORT);
        int count = b[0] & 0xFF;
        List<ClassExport> result = new ArrayList<>(count);
        int o = 1;
        for (int i = 0; i < count; i++) {
            int classOffset = u2(b, o);
            int sfCount = b[o + 2] & 0xFF;
            int smCount = b[o + 3] & 0xFF;
            o += 4;
            List<Integer> fields = new ArrayList<>();
            for (int f = 0; f < sfCount; f++, o += 2) {
                fields.add(u2(b, o));
            }
            List<Integer> methods = new ArrayList<>();
            for (int m = 0; m < smCount; m++, o += 2) {
                methods.add(u2(b, o));
            }
            result.add(new ClassExport(classOffset, List.copyOf(fields), List.copyOf(methods)));
        }
        return List.copyOf(result);
    }

    /**
     * Parses the Import component (§6.7).
     *
     * @return imported packages in package-token order
     */
    public List<ImportedPackage> imports() {
        byte[] b = requireBody(TAG_IMPORT);
        int count = b[0] & 0xFF;
        List<ImportedPackage> result = new ArrayList<>(count);
        int o = 1;
        for (int i = 0; i < count; i++) {
            int minor = b[o] & 0xFF;
            int major = b[o + 1] & 0xFF;
            int len = b[o + 2] & 0xFF;
            result.add(new ImportedPackage(major, minor, Arrays.copyOfRange(b, o + 3, o + 3 + len)));
            o += 3 + len;
        }
        return List.copyOf(result);
    }

    static int u2(byte[] b, int o) {
        if (o + 1 >= b.length) {
            throw new IllegalStateException("read past end at offset " + o + " (length " + b.length + ")");
        }
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    static int u1(byte[] b, int o) {
        if (o >= b.length) {
            throw new IllegalStateException("read past end at offset " + o + " (length " + b.length + ")");
        }
        return b[o] & 0xFF;
    }

    /**
     * A 4-byte constant pool entry (§6.8).
     *
     * @param index CP index
     * @param tag   CONSTANT_* tag (1..6)
     * @param b1    first info byte
     * @param b2    second info byte
     * @param b3    third info byte
     */
    public record CpEntry(int index, int tag, int b1, int b2, int b3) {
        /** CONSTANT_Classref. */
        public static final int CLASSREF = 1;
        /** CONSTANT_InstanceFieldref. */
        public static final int INSTANCE_FIELDREF = 2;
        /** CONSTANT_VirtualMethodref. */
        public static final int VIRTUAL_METHODREF = 3;
        /** CONSTANT_SuperMethodref. */
        public static final int SUPER_METHODREF = 4;
        /** CONSTANT_StaticFieldref. */
        public static final int STATIC_FIELDREF = 5;
        /** CONSTANT_StaticMethodref. */
        public static final int STATIC_METHODREF = 6;

        /** @return {@code true} if the entry refers to an imported package (high bit set) */
        public boolean isExternal() {
            return (b1 & 0x80) != 0;
        }

        /** @return the class_ref of tags 1-4 ({@code (b1 << 8) | b2}) */
        public int classRef() {
            return (b1 << 8) | b2;
        }

        /** @return the token of tags 2-4 */
        public int token() {
            return b3;
        }

        /** @return internal offset of tags 5 and 6 ({@code (b2 << 8) | b3}) */
        public int internalOffset() {
            return (b2 << 8) | b3;
        }
    }

    /**
     * Static field image description (§6.11).
     *
     * @param imageSize        image_size
     * @param referenceCount   reference_count (segments 1 and 2)
     * @param arrayInits       array_init entries (segment 1)
     * @param defaultValueCount default_value_count (segment 3 bytes)
     * @param nonDefaultValues non_default_values (segment 4 image)
     */
    public record StaticFieldView(int imageSize, int referenceCount, List<ArrayInit> arrayInits,
                                  int defaultValueCount, byte[] nonDefaultValues) {
        @Override
        public boolean equals(Object o) {
            return o instanceof StaticFieldView v && v.imageSize == imageSize
                    && v.referenceCount == referenceCount && v.arrayInits.equals(arrayInits)
                    && v.defaultValueCount == defaultValueCount
                    && Arrays.equals(v.nonDefaultValues, nonDefaultValues);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * imageSize + referenceCount) + Arrays.hashCode(nonDefaultValues);
        }

        @Override
        public String toString() {
            return "StaticFieldView[imageSize=" + imageSize + ", referenceCount=" + referenceCount
                    + ", arrayInits=" + arrayInits + ", defaultValueCount=" + defaultValueCount
                    + ", nonDefaultValues=" + java.util.HexFormat.of().formatHex(nonDefaultValues) + "]";
        }
    }

    /**
     * One array_init_info entry (§6.11).
     *
     * @param type   array type (2 boolean, 3 byte, 4 short, 5 int; Table 6-15)
     * @param values initial values (big-endian, element size per Table 6-14)
     */
    public record ArrayInit(int type, byte[] values) {
        @Override
        public boolean equals(Object o) {
            return o instanceof ArrayInit a && a.type == type && Arrays.equals(a.values, values);
        }

        @Override
        public int hashCode() {
            return 31 * type + Arrays.hashCode(values);
        }

        @Override
        public String toString() {
            return "ArrayInit[type=" + type + ", values=" + java.util.HexFormat.of().formatHex(values) + "]";
        }
    }

    /**
     * One exception_handler_info entry (§6.10.3).
     *
     * @param startOffset    start_offset
     * @param stopBit        stop_bit
     * @param activeLength   active_length
     * @param handlerOffset  handler_offset
     * @param catchTypeIndex catch_type_index
     */
    public record Handler(int startOffset, boolean stopBit, int activeLength,
                          int handlerOffset, int catchTypeIndex) {}

    /**
     * One class_export_info entry (§6.13).
     *
     * @param classOffset         class_offset into the Class component
     * @param staticFieldOffsets  static_field_offsets
     * @param staticMethodOffsets static_method_offsets
     */
    public record ClassExport(int classOffset, List<Integer> staticFieldOffsets,
                              List<Integer> staticMethodOffsets) {}

    /**
     * One package_info entry of the Import component (§6.7).
     *
     * @param major major version
     * @param minor minor version
     * @param aid   package AID
     */
    public record ImportedPackage(int major, int minor, byte[] aid) {
        @Override
        public boolean equals(Object o) {
            return o instanceof ImportedPackage p && p.major == major && p.minor == minor
                    && Arrays.equals(p.aid, aid);
        }

        @Override
        public int hashCode() {
            return 31 * (31 * major + minor) + Arrays.hashCode(aid);
        }

        @Override
        public String toString() {
            return "ImportedPackage[" + java.util.HexFormat.of().formatHex(aid) + " v" + major + "." + minor + "]";
        }
    }
}
