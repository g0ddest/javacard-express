package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses a Java Card CAP file (JAR/ZIP) and prepares the Load File Data Block for the GlobalPlatform
 * LOAD command.
 *
 * <p>A CAP file contains one file per component in a {@code javacard} directory (JCVM 3.1 section
 * 6.2.1, Table 6-2). Components of the Extended format, and the Static Resources component, use the
 * {@code .capx} extension. This class extracts the components in the Reference Component Install Order
 * (JCVM 3.1 section 6.3) and wraps them in the 'C4' Load File Data Block (GPCS v2.3.1 Table 11-58).</p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * CAPFile cap = CAPFile.fromFile(Path.of("applet.cap"));
 *
 * String pkgAid = cap.packageAidHex();         // Load File AID, e.g. "A0000000031010"
 * List&lt;byte[]&gt; applets = cap.appletAids();   // Executable Module AIDs (Applet component)
 * byte[] loadData = cap.loadFileData();        // C4 || BER-len || components
 *
 * gp.loadAndInstall(cap, null, 0x00, null);    // single-applet CAP
 * </pre>
 *
 * <h2>Component order (JCVM 3.1 section 6.3):</h2>
 * <p>Header, Directory, Import, Applet, Class, Method, StaticField, Export, ConstantPool,
 * ReferenceLocation, StaticResources, Descriptor (optional for loading). Debug is never loaded.</p>
 *
 * @see GPSession#load(CAPFile)
 */
public final class CAPFile {

    /** Component files in the Reference Component Install Order (JCVM 3.1 section 6.3, Table 6-2). */
    private static final String[] COMPONENT_NAMES = {
            "Header", "Directory", "Import", "Applet", "Class", "Method",
            "StaticField", "Export", "ConstantPool", "RefLocation", "StaticResources", "Descriptor"
    };

    /** Components every CAP file contains (JCVM 3.1 section 6.2; Applet, Export, Debug, StaticResources optional). */
    private static final List<String> REQUIRED = List.of("Header", "Directory", "Import", "Class", "Method",
            "StaticField", "ConstantPool", "RefLocation", "Descriptor");

    private static final String DESCRIPTOR = "Descriptor";
    private static final int ACC_EXTENDED = 0x08;
    private static final int TAG_APPLET = 3;

    private final Map<String, byte[]> components;
    private final byte[] packageAid;
    private final int majorVersion;
    private final int minorVersion;
    private final boolean extended;

    private CAPFile(Map<String, byte[]> components, byte[] packageAid, int majorVersion, int minorVersion,
                    boolean extended) {
        this.components = components;
        this.packageAid = packageAid;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.extended = extended;
    }

    /**
     * Parses a CAP file from raw ZIP bytes.
     *
     * @param zipBytes the CAP file content (ZIP format)
     * @return parsed CAP file
     * @throws GPException if the ZIP is invalid, a required component is missing, a component is present
     *                     twice ({@code .cap} and {@code .capx}), or the Header is malformed
     */
    public static CAPFile from(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new GPException("CAP file data must not be null or empty");
        }
        Map<String, byte[]> entries = readZipEntries(zipBytes);
        if (entries.isEmpty()) {
            throw new GPException("CAP file contains no entries");
        }
        String packageDir = findPackageDir(entries);
        if (packageDir == null) {
            throw new GPException("CAP file is missing Header.cap component");
        }
        Map<String, byte[]> components = components(entries, packageDir);
        byte[] header = components.get("Header");
        if (header.length < 14) {
            throw new GPException("Header component is too short");
        }
        // header_component: tag(1) size(2) magic(4) minor(1) major(1) flags(1) | minor major AID_length AID
        boolean extended = (header[9] & ACC_EXTENDED) != 0;
        int aidLength = header[12] & 0xFF;
        if (header.length < 13 + aidLength) {
            throw new GPException("Header component too short for AID (need "
                    + (13 + aidLength) + " bytes, got " + header.length + ")");
        }
        byte[] packageAid = new byte[aidLength];
        System.arraycopy(header, 13, packageAid, 0, aidLength);
        requireComponents(components);
        return new CAPFile(components, packageAid, header[11] & 0xFF, header[10] & 0xFF, extended);
    }

    /**
     * Parses a CAP file from a file path.
     *
     * @param path the path to the .cap file
     * @return parsed CAP file
     * @throws GPException if reading or parsing fails
     */
    public static CAPFile fromFile(Path path) {
        try {
            return from(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new GPException("Failed to read CAP file: " + path, e);
        }
    }

    /**
     * Returns the Load File AID: the package AID of a Compact CAP file, or the CAP AID of an Extended one
     * (JCVM 3.1 section 6.4).
     *
     * @return the package AID
     */
    public byte[] packageAid() {
        return packageAid.clone();
    }

    /**
     * Returns the package AID as an uppercase hex string.
     *
     * @return hex-encoded package AID
     */
    public String packageAidHex() {
        return Hex.encode(packageAid);
    }

    /**
     * Returns the package (or CAP) major version from the Header component.
     *
     * @return major version
     */
    public int majorVersion() {
        return majorVersion;
    }

    /**
     * Returns the package (or CAP) minor version from the Header component.
     *
     * @return minor version
     */
    public int minorVersion() {
        return minorVersion;
    }

    /**
     * Returns true for a CAP file in Extended format (Header flag ACC_EXTENDED, JCVM 3.1 Table 6-3).
     *
     * @return true for the Extended format
     */
    public boolean isExtended() {
        return extended;
    }

    /**
     * Returns the AIDs of the applets defined in this CAP file, read from the Applet component (JCVM 3.1
     * section 6.6). These are the Executable Module AIDs of INSTALL [for install] (GPCS v2.3.1 11.5.2.3.2).
     *
     * @return the applet AIDs in component order (empty for a library without Applet component)
     * @throws GPException if the Applet component is malformed
     */
    public List<byte[]> appletAids() {
        byte[] applet = components.get("Applet");
        if (applet == null) {
            return List.of();
        }
        try {
            return parseAppletComponent(applet, extended);
        } catch (IndexOutOfBoundsException e) {
            throw new GPException("Malformed Applet component (JCVM 3.1 section 6.6)", e);
        }
    }

    /**
     * Returns the AID of the only applet of this CAP file.
     *
     * @return the applet AID
     * @throws GPException if the CAP file defines no applet or more than one
     */
    public byte[] singleAppletAid() {
        List<byte[]> applets = appletAids();
        if (applets.size() != 1) {
            throw new GPException("CAP file " + packageAidHex() + " defines " + applets.size() + " applets "
                    + applets.stream().map(Hex::encode).toList() + "; choose the Executable Module AID explicitly");
        }
        return applets.getFirst().clone();
    }

    /**
     * Returns the names of the loadable components present in this CAP file, in load order.
     *
     * @return list of component names (e.g., ["Header", "Directory", "Import", ...]); Debug is excluded
     */
    public List<String> componentNames() {
        List<String> names = new ArrayList<>();
        for (String name : COMPONENT_NAMES) {
            if (components.containsKey(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Returns the Load File Data Block content including the Descriptor component.
     *
     * @return concatenated component bytes
     * @see #code(boolean)
     */
    public byte[] code() {
        return code(true);
    }

    /**
     * Returns the Load File Data Block content: the components concatenated in the Reference Component
     * Install Order (JCVM 3.1 section 6.3), skipping absent optional components. Debug is never included.
     *
     * @param includeDescriptor whether to load the Descriptor component, which is optional for loading
     *                          (JCVM 3.1 section 6.3, footnote 5)
     * @return concatenated component bytes
     */
    public byte[] code(boolean includeDescriptor) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String name : COMPONENT_NAMES) {
            byte[] component = components.get(name);
            if (component != null && (includeDescriptor || !DESCRIPTOR.equals(name))) {
                out.writeBytes(component);
            }
        }
        return out.toByteArray();
    }

    /**
     * Returns the Load File including the Descriptor component, see {@link #loadFileData(boolean)}.
     *
     * @return C4-wrapped load data
     */
    public byte[] loadFileData() {
        return loadFileData(true);
    }

    /**
     * Returns the Load File for the LOAD command: {@code 'C4' || BER length || code} (GPCS v2.3.1
     * Table 11-58).
     *
     * @param includeDescriptor whether to load the optional Descriptor component
     * @return C4-wrapped load data
     */
    public byte[] loadFileData(boolean includeDescriptor) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xC4);
        writeBerLength(out, code(includeDescriptor).length);
        out.writeBytes(code(includeDescriptor));
        return out.toByteArray();
    }

    /**
     * Computes the Load File Data Block Hash over the components, excluding tag 'C4' and its length
     * (GPCS v2.3.1 section C.2), for INSTALL [for load] (Table 11-42).
     *
     * @param algorithm         the hash algorithm of section B.5: "SHA-1", "SHA-256", "SHA-384" or "SHA-512"
     * @param includeDescriptor whether the Descriptor component is loaded
     * @return the hash
     */
    public byte[] loadFileDataBlockHash(String algorithm, boolean includeDescriptor) {
        try {
            return MessageDigest.getInstance(algorithm.toUpperCase(Locale.ROOT)).digest(code(includeDescriptor));
        } catch (NoSuchAlgorithmException e) {
            throw new GPException("Unsupported Load File Data Block Hash algorithm " + algorithm, e);
        }
    }

    /**
     * Splits the load file data (with Descriptor) into blocks of the given maximum size.
     *
     * @param maxBlockSize maximum bytes per block (247 with C-MAC, 239 with C-ENC)
     * @return list of data blocks
     */
    public List<byte[]> loadBlocks(int maxBlockSize) {
        byte[] data = loadFileData();
        List<byte[]> blocks = new ArrayList<>();
        for (int offset = 0; offset < data.length; offset += maxBlockSize) {
            byte[] block = new byte[Math.min(data.length - offset, maxBlockSize)];
            System.arraycopy(data, offset, block, 0, block.length);
            blocks.add(block);
        }
        return blocks;
    }

    @Override
    public String toString() {
        return "CAPFile[aid=" + packageAidHex()
                + ", version=" + majorVersion + "." + minorVersion
                + ", components=" + componentNames().size() + "]";
    }

    // ── Internal ──

    private static List<byte[]> parseAppletComponent(byte[] applet, boolean extended) {
        if ((applet[0] & 0xFF) != TAG_APPLET) {
            throw new GPException("Applet component has tag " + (applet[0] & 0xFF) + ", expected 3");
        }
        int count = applet[3] & 0xFF;
        List<byte[]> aids = new ArrayList<>(count);
        int offset = 4;
        for (int i = 0; i < count; i++) {
            int length = applet[offset] & 0xFF;
            if (length < 5 || length > 16) {
                throw new GPException("Applet component lists an AID of " + length + " bytes (must be 5-16)");
            }
            byte[] aid = new byte[length];
            System.arraycopy(applet, offset + 1, aid, 0, length);
            aids.add(aid);
            // compact: u2 install_method_offset; extended: u1 block index + u2 offset (JCVM 3.1 section 6.6)
            offset += 1 + length + (extended ? 3 : 2);
        }
        if (offset > applet.length) {
            throw new GPException("Applet component is truncated");
        }
        return aids;
    }

    /**
     * Maps component names to contents (file names are not case sensitive, JCVM 3.1 section 6.2.1); the
     * '.cap' and '.capx' files of one component may not coexist.
     */
    private static Map<String, byte[]> components(Map<String, byte[]> entries, String packageDir) {
        Map<String, byte[]> byLowerCaseName = new HashMap<>();
        entries.forEach((name, bytes) -> byLowerCaseName.put(name.toLowerCase(Locale.ROOT), bytes));
        String dir = packageDir.toLowerCase(Locale.ROOT);
        Map<String, byte[]> components = new LinkedHashMap<>();
        for (String name : COMPONENT_NAMES) {
            String file = dir + name.toLowerCase(Locale.ROOT);
            byte[] compact = byLowerCaseName.get(file + ".cap");
            byte[] extendedFile = byLowerCaseName.get(file + ".capx");
            if (compact != null && extendedFile != null) {
                throw new GPException("CAP file contains both " + name + ".cap and " + name + ".capx");
            }
            if (compact != null || extendedFile != null) {
                components.put(name, compact != null ? compact : extendedFile);
            }
        }
        return components;
    }

    private static void requireComponents(Map<String, byte[]> components) {
        List<String> missing = REQUIRED.stream().filter(name -> !components.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw new GPException("CAP file is missing required components " + missing
                    + " (JCVM 3.1 section 6.2)");
        }
    }

    private static Map<String, byte[]> readZipEntries(byte[] zipBytes) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zis.readAllBytes());
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new GPException("Failed to parse CAP file as ZIP", e);
        }
        return entries;
    }

    /**
     * Finds the {@code javacard} directory holding Header.cap; component file names are not case
     * sensitive (JCVM 3.1 section 6.2.1). A JAR with several CAP files is rejected as ambiguous.
     */
    private static String findPackageDir(Map<String, byte[]> entries) {
        String found = null;
        for (String name : entries.keySet()) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith("/javacard/header.cap") || lower.equals("javacard/header.cap")) {
                if (found != null) {
                    throw new GPException("JAR contains more than one CAP file (" + found + "Header.cap, "
                            + name + "); load them separately");
                }
                found = name.substring(0, name.length() - "Header.cap".length());
            }
        }
        return found;
    }

    private static void writeBerLength(ByteArrayOutputStream out, int length) {
        if (length <= 0x7F) {
            out.write(length);
        } else if (length <= 0xFF) {
            out.write(0x81);
            out.write(length);
        } else if (length <= 0xFFFF) {
            out.write(0x82);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        } else {
            out.write(0x83);
            out.write((length >> 16) & 0xFF);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
        }
    }
}
