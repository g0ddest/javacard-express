package name.velikodniy.jcexpress.plugin.testing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal, independent reader for CAP files in compact format, written from the public
 * JCVM 3.1 specification (chapter 6) for use in tests. It deliberately shares no code with
 * the converter so that tests do not validate the converter against itself.
 *
 * <p>Only the structures the plugin tests need are decoded: Header (&sect;6.4), Applet
 * (&sect;6.6), Import (&sect;6.7), Export (&sect;6.13) and the method/class tables of the
 * Descriptor component (&sect;6.14).
 */
public final class CapFile {

    /** Header flag ACC_INT (JCVM 3.1 &sect;6.4, Table 6-3). */
    public static final int ACC_INT = 0x01;
    /** Header flag ACC_EXPORT (JCVM 3.1 &sect;6.4, Table 6-3). */
    public static final int ACC_EXPORT = 0x02;
    /** Header flag ACC_APPLET (JCVM 3.1 &sect;6.4, Table 6-3). */
    public static final int ACC_APPLET = 0x04;
    /** Header flag ACC_EXTENDED (JCVM 3.1 &sect;6.4, Table 6-3). */
    public static final int ACC_EXTENDED = 0x08;

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private final Map<String, byte[]> components;

    private CapFile(Map<String, byte[]> components) {
        this.components = components;
    }

    /**
     * Reads a CAP file from disk.
     *
     * @param path the CAP file
     * @return the parsed CAP file
     */
    public static CapFile read(Path path) {
        try {
            return read(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Reads a CAP file (a JAR container, JCVM 3.1 &sect;4.1.3) from bytes.
     *
     * @param jar the CAP file bytes
     * @return the parsed CAP file
     */
    public static CapFile read(byte[] jar) {
        Map<String, byte[]> components = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(jar))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".cap")) {
                    String simple = name.substring(name.lastIndexOf('/') + 1, name.length() - 4);
                    components.put(simple, zip.readAllBytes());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new CapFile(components);
    }

    /** @return the names of the components present (e.g. {@code Header}, {@code Applet}) */
    public List<String> componentNames() {
        return List.copyOf(components.keySet());
    }

    /** @return {@code true} if the named component is present */
    public boolean has(String component) {
        return components.containsKey(component);
    }

    /** @return the Header component (JCVM 3.1 &sect;6.4) */
    public Header header() {
        byte[] h = component("Header");
        int aidLength = u1(h, 12);
        return new Header(u1(h, 8), u1(h, 7), u1(h, 9), u1(h, 11), u1(h, 10),
                HEX.formatHex(h, 13, 13 + aidLength));
    }

    /** @return the applets of the Applet component (JCVM 3.1 &sect;6.6), empty if absent */
    public List<Applet> applets() {
        if (!has("Applet")) {
            return List.of();
        }
        byte[] a = component("Applet");
        List<Applet> result = new ArrayList<>();
        int pos = 4;
        for (int i = 0; i < u1(a, 3); i++) {
            int length = u1(a, pos);
            String aid = HEX.formatHex(a, pos + 1, pos + 1 + length);
            result.add(new Applet(aid, u2(a, pos + 1 + length)));
            pos += 3 + length;
        }
        return result;
    }

    /** @return the imported packages of the Import component (JCVM 3.1 &sect;6.7) */
    public List<ImportedPackage> imports() {
        byte[] im = component("Import");
        List<ImportedPackage> result = new ArrayList<>();
        int pos = 4;
        for (int i = 0; i < u1(im, 3); i++) {
            int length = u1(im, pos + 2);
            result.add(new ImportedPackage(HEX.formatHex(im, pos + 3, pos + 3 + length),
                    u1(im, pos + 1), u1(im, pos)));
            pos += 3 + length;
        }
        return result;
    }

    /** @return the class_count of the Export component (JCVM 3.1 &sect;6.13), if present */
    public Optional<Integer> exportClassCount() {
        return has("Export") ? Optional.of(u1(component("Export"), 3)) : Optional.empty();
    }

    /** @return the class descriptors of the Descriptor component (JCVM 3.1 &sect;6.14) */
    public List<ClassDescriptor> classDescriptors() {
        return new DescriptorParser(component("Descriptor")).classes();
    }

    /**
     * Finds the method whose method_info starts at the given Method component offset.
     *
     * @param methodOffset offset into the info item of the Method component
     * @return the class and method descriptor, if any method starts there
     */
    public Optional<MethodLocation> methodAt(int methodOffset) {
        for (ClassDescriptor c : classDescriptors()) {
            for (MethodDescriptor m : c.methods()) {
                if ((c.flags() & ClassDescriptor.ACC_INTERFACE) == 0 && m.methodOffset() == methodOffset) {
                    return Optional.of(new MethodLocation(c, m));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the type_descriptor nibbles at an offset of type_descriptor_info
     * (JCVM 3.1 &sect;6.9.1, &sect;6.14.5), e.g. {@code "B431"} for {@code ([BSB)V}.
     *
     * @param typeOffset offset into the type_descriptor_info structure
     * @return the nibbles as upper-case hex digits (without padding)
     */
    public String typeNibbles(int typeOffset) {
        byte[] d = component("Descriptor");
        int base = new DescriptorParser(d).typesStart();
        int nibbleCount = u1(d, base + typeOffset);
        String nibbles = HEX.formatHex(d, base + typeOffset + 1, base + typeOffset + 1 + (nibbleCount + 1) / 2);
        return nibbles.substring(0, nibbleCount);
    }

    byte[] component(String name) {
        byte[] bytes = components.get(name);
        if (bytes == null) {
            throw new IllegalStateException("CAP file has no " + name + " component; present: "
                    + components.keySet());
        }
        return bytes;
    }

    static int u1(byte[] b, int pos) {
        return b[pos] & 0xFF;
    }

    static int u2(byte[] b, int pos) {
        return (u1(b, pos) << 8) | u1(b, pos + 1);
    }

    /**
     * Header component fields (JCVM 3.1 &sect;6.4).
     *
     * @param formatMajor  CAP format major version
     * @param formatMinor  CAP format minor version
     * @param flags        ACC_INT / ACC_EXPORT / ACC_APPLET / ACC_EXTENDED
     * @param packageMajor package major version
     * @param packageMinor package minor version
     * @param packageAid   package AID as upper-case hex
     */
    public record Header(int formatMajor, int formatMinor, int flags,
                         int packageMajor, int packageMinor, String packageAid) {
    }

    /**
     * An entry of the Applet component (JCVM 3.1 &sect;6.6).
     *
     * @param aid                 applet AID as upper-case hex
     * @param installMethodOffset offset of the install method in the Method component info
     */
    public record Applet(String aid, int installMethodOffset) {
    }

    /**
     * A package_info of the Import component (JCVM 3.1 &sect;6.7).
     *
     * @param aid   package AID as upper-case hex
     * @param major major version
     * @param minor minor version
     */
    public record ImportedPackage(String aid, int major, int minor) {
    }

    /**
     * A class_descriptor_info (JCVM 3.1 &sect;6.14.2).
     *
     * @param token   class token (0xFF for package-visible classes)
     * @param flags   ACC_PUBLIC / ACC_FINAL / ACC_INTERFACE / ACC_ABSTRACT (Table 6-17)
     * @param methods the method descriptors
     */
    public record ClassDescriptor(int token, int flags, List<MethodDescriptor> methods) {
        /** ACC_INTERFACE (JCVM 3.1 Table 6-17). */
        public static final int ACC_INTERFACE = 0x40;
        /** ACC_ABSTRACT (JCVM 3.1 Table 6-17). */
        public static final int ACC_ABSTRACT = 0x80;
    }

    /**
     * A method_descriptor_info (JCVM 3.1 &sect;6.14.4).
     *
     * @param token        method token (0xFF when none is assigned)
     * @param flags        access flags (Table 6-20)
     * @param methodOffset offset of the method_info in the Method component info
     * @param typeOffset   offset of the signature in type_descriptor_info
     */
    public record MethodDescriptor(int token, int flags, int methodOffset, int typeOffset) {
        /** ACC_PUBLIC (JCVM 3.1 Table 6-20). */
        public static final int ACC_PUBLIC = 0x01;
        /** ACC_STATIC (JCVM 3.1 Table 6-20). */
        public static final int ACC_STATIC = 0x08;
        /** ACC_ABSTRACT (JCVM 3.1 Table 6-20). */
        public static final int ACC_ABSTRACT = 0x40;
        /** ACC_INIT (JCVM 3.1 Table 6-20). */
        public static final int ACC_INIT = 0x80;
    }

    /**
     * A method together with the class that declares it.
     *
     * @param owner  the declaring class descriptor
     * @param method the method descriptor
     */
    public record MethodLocation(ClassDescriptor owner, MethodDescriptor method) {
    }

    /** Walks descriptor_component_compact (JCVM 3.1 &sect;6.14). */
    private static final class DescriptorParser {
        private final byte[] d;
        private int pos;
        private final List<ClassDescriptor> classes = new ArrayList<>();

        DescriptorParser(byte[] d) {
            this.d = d;
            this.pos = 4;
            int classCount = u1(d, 3);
            for (int i = 0; i < classCount; i++) {
                classes.add(readClass());
            }
        }

        private ClassDescriptor readClass() {
            int token = u1(d, pos);
            int flags = u1(d, pos + 1);
            int interfaceCount = u1(d, pos + 4);
            int fieldCount = u2(d, pos + 5);
            int methodCount = u2(d, pos + 7);
            pos += 9 + 2 * interfaceCount + 7 * fieldCount;
            List<MethodDescriptor> methods = new ArrayList<>();
            for (int i = 0; i < methodCount; i++) {
                methods.add(new MethodDescriptor(u1(d, pos), u1(d, pos + 1), u2(d, pos + 2), u2(d, pos + 4)));
                pos += 12;
            }
            return new ClassDescriptor(token, flags, List.copyOf(methods));
        }

        List<ClassDescriptor> classes() {
            return List.copyOf(classes);
        }

        int typesStart() {
            return pos;
        }
    }
}
