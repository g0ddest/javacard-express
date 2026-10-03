package name.velikodniy.jcexpress.converter.testutil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Minimal, independent CAP file inspector for tests (JCVM 3.1 Chapter 6): extracts component
 * files from the JAR container and decodes the Header, Import and Applet components.
 */
public final class CapInspector {

    private CapInspector() {}

    /** An imported or defined package: version and AID (package_info, JCVM 3.1 §6.4). */
    public record PackageRef(int major, int minor, String aidHex) {
        /** Returns {@code "major.minor"}. */
        public String version() {
            return major + "." + minor;
        }
    }

    /** An applet entry of the Applet component (JCVM 3.1 §6.6). */
    public record AppletRef(String aidHex, int installMethodOffset) {}

    /**
     * Returns all JAR entry names in archive order.
     *
     * @param cap CAP file bytes
     * @return entry names
     */
    public static List<String> entryNames(byte[] cap) {
        List<String> names = new ArrayList<>();
        forEachEntry(cap, (e, data) -> names.add(e.getName()));
        return names;
    }

    /**
     * Extracts all {@code .cap} component files keyed by their simple file name.
     *
     * @param cap CAP file bytes
     * @return component bytes keyed by e.g. {@code "Import.cap"}
     */
    public static Map<String, byte[]> components(byte[] cap) {
        Map<String, byte[]> map = new LinkedHashMap<>();
        forEachEntry(cap, (e, data) -> {
            String name = e.getName();
            if (name.endsWith(".cap")) map.put(name.substring(name.lastIndexOf('/') + 1), data);
        });
        return map;
    }

    /**
     * Decodes the Import component (JCVM 3.1 §6.7).
     *
     * @param cap CAP file bytes
     * @return imported packages in package-token order
     */
    public static List<PackageRef> imports(byte[] cap) {
        byte[] c = components(cap).get("Import.cap");
        List<PackageRef> result = new ArrayList<>();
        int pos = 3;
        int count = c[pos++] & 0xFF;
        for (int i = 0; i < count; i++) {
            int minor = c[pos++] & 0xFF;
            int major = c[pos++] & 0xFF;
            int len = c[pos++] & 0xFF;
            result.add(new PackageRef(major, minor, hex(c, pos, len)));
            pos += len;
        }
        return result;
    }

    /**
     * Decodes the package_info of the Header component (JCVM 3.1 §6.4, compact format).
     *
     * @param cap CAP file bytes
     * @return the package defined by the CAP file
     */
    public static PackageRef headerPackage(byte[] cap) {
        byte[] c = components(cap).get("Header.cap");
        int pos = 3 + 4 + 2 + 1;
        int minor = c[pos++] & 0xFF;
        int major = c[pos++] & 0xFF;
        int len = c[pos++] & 0xFF;
        return new PackageRef(major, minor, hex(c, pos, len));
    }

    /**
     * Returns the Header component flags (JCVM 3.1 §6.4 Table 6-3).
     *
     * @param cap CAP file bytes
     * @return the flags byte
     */
    public static int headerFlags(byte[] cap) {
        return components(cap).get("Header.cap")[3 + 4 + 2] & 0xFF;
    }

    /**
     * Decodes the Applet component (JCVM 3.1 §6.6, compact format).
     *
     * @param cap CAP file bytes
     * @return applets in component order, or an empty list if there is no Applet component
     */
    public static List<AppletRef> applets(byte[] cap) {
        byte[] c = components(cap).get("Applet.cap");
        List<AppletRef> result = new ArrayList<>();
        if (c == null) return result;
        int pos = 3;
        int count = c[pos++] & 0xFF;
        for (int i = 0; i < count; i++) {
            int len = c[pos++] & 0xFF;
            String aid = hex(c, pos, len);
            pos += len;
            int offset = ((c[pos] & 0xFF) << 8) | (c[pos + 1] & 0xFF);
            pos += 2;
            result.add(new AppletRef(aid, offset));
        }
        return result;
    }

    /**
     * An entry of the ConstantPool component (JCVM 3.1 §6.8): a tag and three info bytes.
     *
     * @param tag CP tag (1 ClassRef, 2 InstanceFieldRef, 3 VirtualMethodRef, 4 SuperMethodRef,
     *            5 StaticFieldRef, 6 StaticMethodRef)
     * @param b1  first info byte
     * @param b2  second info byte
     * @param b3  third info byte
     */
    public record CpEntry(int tag, int b1, int b2, int b3) {}

    /**
     * An external reference of the constant pool resolved to the imported package's AID.
     *
     * @param tag         CP tag
     * @param packageAid  AID of the imported package (hex)
     * @param classToken  class token in that package
     * @param memberToken member token (or -1 for class references)
     */
    public record ExternalRef(int tag, String packageAid, int classToken, int memberToken) {}

    /**
     * Decodes the ConstantPool component (JCVM 3.1 §6.8).
     *
     * @param cap CAP file bytes
     * @return the constant pool entries in index order
     */
    public static List<CpEntry> constantPool(byte[] cap) {
        byte[] c = components(cap).get("ConstantPool.cap");
        int count = ((c[3] & 0xFF) << 8) | (c[4] & 0xFF);
        List<CpEntry> result = new ArrayList<>(count);
        for (int i = 0, pos = 5; i < count; i++, pos += 4) {
            result.add(new CpEntry(c[pos] & 0xFF, c[pos + 1] & 0xFF, c[pos + 2] & 0xFF, c[pos + 3] & 0xFF));
        }
        return result;
    }

    /**
     * Returns the external references of the constant pool (JCVM 3.1 §6.8.1-§6.8.6), with the
     * package token replaced by the AID from the Import component.
     *
     * @param cap CAP file bytes
     * @return external class, field and method references
     */
    public static List<ExternalRef> externalRefs(byte[] cap) {
        List<PackageRef> imports = imports(cap);
        List<ExternalRef> result = new ArrayList<>();
        for (CpEntry e : constantPool(cap)) {
            // every external reference starts with package_token | 0x80, then class_token
            if ((e.b1() & 0x80) == 0) continue;
            String aid = imports.get(e.b1() & 0x7F).aidHex();
            result.add(new ExternalRef(e.tag(), aid, e.b2(), e.tag() == 1 ? -1 : e.b3()));
        }
        return result;
    }

    /**
     * Returns the number of argument words of the method at an offset of the Method component
     * info (method_header_info nargs, JCVM 3.1 §6.10; extended headers are handled).
     *
     * @param cap    CAP file bytes
     * @param offset offset of the method_info within the Method component info
     * @return nargs (including {@code this} for instance methods)
     */
    public static int methodNargs(byte[] cap, int offset) {
        byte[] c = components(cap).get("Method.cap");
        int header = 3 + offset;
        boolean extended = (c[header] & 0x80) != 0; // ACC_EXTENDED flag in the high nibble
        return extended ? c[header + 2] & 0xFF : (c[header + 1] >> 4) & 0x0F;
    }

    private static String hex(byte[] b, int off, int len) {
        return HexFormat.of().withUpperCase().formatHex(b, off, off + len);
    }

    private interface EntryConsumer {
        void accept(ZipEntry entry, byte[] data);
    }

    private static void forEachEntry(byte[] cap, EntryConsumer consumer) {
        try (var zis = new ZipInputStream(new ByteArrayInputStream(cap))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                consumer.accept(entry, zis.readAllBytes());
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
