package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses a JavaCard CAP file (ZIP archive) and prepares load data for the
 * GlobalPlatform LOAD command.
 *
 * <p>A CAP file is a JAR/ZIP containing component files (Header.cap, Class.cap, etc.)
 * organized under a package directory. This class reads the ZIP, extracts components
 * in the order defined by the JCVM specification, and produces the load file data
 * wrapped in a C4 BER-TLV structure ready for transmission.</p>
 *
 * <h2>Usage:</h2>
 * <pre>
 * CAPFile cap = CAPFile.from(Files.readAllBytes(Path.of("applet.cap")));
 *
 * String pkgAid = cap.packageAidHex();   // "A0000000031010"
 * byte[] loadData = cap.loadFileData();   // C4 || BER-len || code
 *
 * // Or use with GPSession:
 * gp.load(cap);
 * </pre>
 *
 * <h2>Component order (JCVM specification):</h2>
 * <p>Header, Directory, Import, Applet, Class, Method, StaticField, Export,
 * ConstantPool, RefLocation, Descriptor. Debug is excluded.</p>
 *
 * @see GPSession#load(CAPFile)
 */
public final class CAPFile {

    private static final String[] COMPONENT_NAMES = {
            "Header", "Directory", "Import", "Applet", "Class", "Method",
            "StaticField", "Export", "ConstantPool", "RefLocation", "Descriptor"
    };

    private final Map<String, byte[]> entries;
    private final String packageDir;
    private final byte[] packageAid;
    private final int majorVersion;
    private final int minorVersion;

    private CAPFile(Map<String, byte[]> entries, String packageDir,
                    byte[] packageAid, int majorVersion, int minorVersion) {
        this.entries = entries;
        this.packageDir = packageDir;
        this.packageAid = packageAid;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    /**
     * Parses a CAP file from raw ZIP bytes.
     *
     * @param zipBytes the CAP file content (ZIP format)
     * @return parsed CAP file
     * @throws GPException if the ZIP is invalid or missing required components
     */
    public static CAPFile from(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new GPException("CAP file data must not be null or empty");
        }

        Map<String, byte[]> entries = readZipEntries(zipBytes);
        if (entries.isEmpty()) {
            throw new GPException("CAP file contains no entries");
        }

        // Find the package directory by locating Header.cap
        String packageDir = findPackageDir(entries);
        if (packageDir == null) {
            throw new GPException("CAP file is missing Header.cap component");
        }

        byte[] header = entries.get(packageDir + "Header.cap");
        if (header == null || header.length < 14) {
            throw new GPException("Header component is too short");
        }

        // Extract package AID from Header component
        // offset [10] = minor version, [11] = major version
        // offset [12] = AID length, [13..13+len] = AID bytes
        int minorVersion = header[10] & 0xFF;
        int majorVersion = header[11] & 0xFF;
        int aidLength = header[12] & 0xFF;

        if (header.length < 13 + aidLength) {
            throw new GPException("Header component too short for AID (need "
                    + (13 + aidLength) + " bytes, got " + header.length + ")");
        }

        byte[] packageAid = new byte[aidLength];
        System.arraycopy(header, 13, packageAid, 0, aidLength);

        return new CAPFile(entries, packageDir, packageAid, majorVersion, minorVersion);
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
     * Returns the package AID as raw bytes.
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
     * Returns the package major version from the Header component.
     *
     * @return major version
     */
    public int majorVersion() {
        return majorVersion;
    }

    /**
     * Returns the package minor version from the Header component.
     *
     * @return minor version
     */
    public int minorVersion() {
        return minorVersion;
    }

    /**
     * Returns the names of components present in this CAP file.
     *
     * <p>Only components in the standard order are listed.
     * Debug components are excluded.</p>
     *
     * @return list of component names (e.g., ["Header", "Directory", "Class", ...])
     */
    public List<String> componentNames() {
        List<String> names = new ArrayList<>();
        for (String name : COMPONENT_NAMES) {
            if (entries.containsKey(packageDir + name + ".cap")) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * Returns the raw IJC load data (concatenation of components in spec order).
     *
     * <p>Components are concatenated in JCVM specification order, skipping
     * absent optional components (e.g., Export). Debug is always excluded.</p>
     *
     * @return concatenated component bytes
     */
    public byte[] code() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String name : COMPONENT_NAMES) {
            byte[] component = entries.get(packageDir + name + ".cap");
            if (component != null) {
                out.writeBytes(component);
            }
        }
        return out.toByteArray();
    }

    /**
     * Returns the load file data wrapped in a C4 BER-TLV structure.
     *
     * <p>Format: {@code C4 || BER-TLV-length || code()}</p>
     *
     * <p>This is the data format expected by the GlobalPlatform LOAD command.</p>
     *
     * @return C4-wrapped load data
     */
    public byte[] loadFileData() {
        byte[] code = code();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xC4);
        writeBerLength(out, code.length);
        out.writeBytes(code);
        return out.toByteArray();
    }

    /**
     * Splits the load file data into blocks of the given maximum size.
     *
     * <p>Useful for manual LOAD command construction. Each block is a portion
     * of the C4-wrapped load data, ready to be sent as LOAD command data.</p>
     *
     * @param maxBlockSize maximum bytes per block (typically 247 for C-MAC)
     * @return list of data blocks
     */
    public List<byte[]> loadBlocks(int maxBlockSize) {
        byte[] data = loadFileData();
        List<byte[]> blocks = new ArrayList<>();
        int offset = 0;
        while (offset < data.length) {
            int size = Math.min(data.length - offset, maxBlockSize);
            byte[] block = new byte[size];
            System.arraycopy(data, offset, block, 0, size);
            blocks.add(block);
            offset += size;
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

    private static String findPackageDir(Map<String, byte[]> entries) {
        for (String name : entries.keySet()) {
            if (name.endsWith("/javacard/Header.cap") || name.equals("javacard/Header.cap")) {
                return name.substring(0, name.length() - "Header.cap".length());
            }
        }
        return null;
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
