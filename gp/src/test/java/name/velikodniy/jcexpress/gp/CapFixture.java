package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds synthetic CAP files (JAR/ZIP) with the component framing of JCVM 3.1 chapter 6 for CAPFile and
 * GPSession tests.
 *
 * <p>Each component is {@code tag || size || info}: a u2 size for the Compact format and a u4 size for the
 * Extended format components ({@code .capx}, section 6.2). The Header carries the package (or CAP) AID and
 * versions (section 6.4) and the Applet component lists applet AIDs (section 6.6); the info bytes of the
 * other components are filler, which is all the GlobalPlatform layer may rely on (it never interprets
 * them).</p>
 */
final class CapFixture {

    /** Component tags of JCVM 3.1 Table 6-1. */
    static final Map<String, Integer> TAGS = Map.ofEntries(
            Map.entry("Header", 1), Map.entry("Directory", 2), Map.entry("Applet", 3), Map.entry("Import", 4),
            Map.entry("ConstantPool", 5), Map.entry("Class", 6), Map.entry("Method", 7),
            Map.entry("StaticField", 8), Map.entry("RefLocation", 9), Map.entry("Export", 10),
            Map.entry("Descriptor", 11), Map.entry("Debug", 12), Map.entry("StaticResources", 13));

    private static final List<String> EXTENDED_CAPABLE = List.of("Method", "RefLocation", "Descriptor", "Debug");

    private final byte[] aid;
    private final List<byte[]> applets = new ArrayList<>();
    private final Map<String, Integer> fillerSizes = new LinkedHashMap<>();
    private final List<String> omitted = new ArrayList<>();
    private final List<String> duplicatedAsCapx = new ArrayList<>();
    private final Map<String, byte[]> replaced = new LinkedHashMap<>();
    private String directory = "com/example/applet/javacard/";
    private boolean extended;
    private boolean staticResources;
    private boolean debug;
    private boolean lowerCase;

    private CapFixture(String aidHex) {
        this.aid = Hex.decode(aidHex);
        for (String name : List.of("Directory", "Import", "Class", "Method", "StaticField", "Export",
                "ConstantPool", "RefLocation", "Descriptor")) {
            fillerSizes.put(name, 2);
        }
    }

    /**
     * Starts a CAP file for the given package (Compact format) or CAP (Extended format) AID.
     *
     * @param aidHex the AID written to the Header component
     * @return the builder
     */
    static CapFixture of(String aidHex) {
        return new CapFixture(aidHex);
    }

    CapFixture applets(String... aidHex) {
        for (String a : aidHex) {
            applets.add(Hex.decode(a));
        }
        return this;
    }

    CapFixture extended() {
        this.extended = true;
        return this;
    }

    CapFixture withStaticResources() {
        this.staticResources = true;
        return this;
    }

    CapFixture withDebug() {
        this.debug = true;
        return this;
    }

    CapFixture without(String component) {
        omitted.add(component);
        return this;
    }

    CapFixture alsoAsCapx(String component) {
        duplicatedAsCapx.add(component);
        return this;
    }

    CapFixture replace(String component, byte[] bytes) {
        replaced.put(component, bytes.clone());
        return this;
    }

    CapFixture lowerCaseFileNames() {
        this.lowerCase = true;
        return this;
    }

    CapFixture inDirectory(String javacardDirectory) {
        this.directory = javacardDirectory;
        return this;
    }

    CapFixture fillerSize(String component, int size) {
        fillerSizes.put(component, size);
        return this;
    }

    /**
     * Returns the component bytes this fixture writes, by component name.
     *
     * @return the components in Reference Component Install Order (JCVM 3.1 section 6.3)
     */
    Map<String, byte[]> components() {
        Map<String, byte[]> all = new LinkedHashMap<>();
        all.put("Header", header());
        all.put("Directory", component("Directory"));
        all.put("Import", component("Import"));
        if (!applets.isEmpty()) {
            all.put("Applet", appletComponent());
        }
        for (String name : List.of("Class", "Method", "StaticField", "Export", "ConstantPool", "RefLocation")) {
            all.put(name, component(name));
        }
        if (staticResources) {
            all.put("StaticResources", frame(13, true, new byte[]{0x00, 0x01, 0x00, 0x07, 0, 0, 0, 1, 0x42}));
        }
        all.put("Descriptor", component("Descriptor"));
        if (debug) {
            all.put("Debug", component("Debug"));
        }
        replaced.forEach((name, bytes) -> all.computeIfPresent(name, (n, old) -> bytes));
        omitted.forEach(all::remove);
        return all;
    }

    /**
     * Builds the CAP file.
     *
     * @return the ZIP bytes
     */
    byte[] build() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write("Manifest-Version: 1.0\r\n".getBytes(StandardCharsets.US_ASCII));
            zip.closeEntry();
            // alphabetical ZIP order, deliberately different from the install order
            Map<String, byte[]> files = new TreeMap<>();
            components().forEach((name, content) -> files.put(fileName(name + extension(name)), content));
            duplicatedAsCapx.forEach(name -> files.put(fileName(name + ".capx"), components().get(name)));
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(directory + e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /**
     * Returns the file extension of a component: '.capx' for StaticResources and, in the Extended format,
     * for Method, RefLocation, Descriptor and Debug (JCVM 3.1 Table 6-2).
     */
    String extension(String component) {
        boolean x = component.equals("StaticResources") || (extended && EXTENDED_CAPABLE.contains(component));
        return x ? ".capx" : ".cap";
    }

    private String fileName(String name) {
        return lowerCase ? name.toLowerCase(Locale.ROOT) : name;
    }

    private byte[] header() {
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        info.writeBytes(Hex.decode("DECAFFED"));
        info.write(extended ? 3 : 2);           // CAP_Format_minor_version
        info.write(2);                           // CAP_Format_major_version
        int flags = (applets.isEmpty() ? 0 : 0x04) | (extended ? 0x08 : 0);
        info.write(flags);
        info.write(0);                           // package (or CAP) minor_version
        info.write(1);                           // package (or CAP) major_version
        info.write(aid.length);
        info.writeBytes(aid);
        if (extended) {
            info.write(1);                       // package_count
            info.writeBytes(new byte[]{0, 1, (byte) aid.length});
            info.writeBytes(aid);
        }
        info.write(0);                           // package_name_info: name_length 0
        return frame(1, false, info.toByteArray());
    }

    private byte[] appletComponent() {
        ByteArrayOutputStream info = new ByteArrayOutputStream();
        info.write(applets.size());
        for (byte[] applet : applets) {
            info.write(applet.length);
            info.writeBytes(applet);
            if (extended) {
                info.write(0);                   // install_method_component_block_index
            }
            info.writeBytes(new byte[]{0x00, 0x10});  // install_method_offset
        }
        return frame(3, false, info.toByteArray());
    }

    private byte[] component(String name) {
        int size = fillerSizes.getOrDefault(name, 4);
        byte[] info = new byte[size];
        for (int i = 0; i < size; i++) {
            info[i] = (byte) (TAGS.get(name) * 16 + i);
        }
        return frame(TAGS.get(name), extended && EXTENDED_CAPABLE.contains(name), info);
    }

    private static byte[] frame(int tag, boolean u4Size, byte[] info) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (u4Size) {
            out.write(info.length >>> 24);
            out.write(info.length >>> 16);
        }
        out.write(info.length >>> 8);
        out.write(info.length);
        out.writeBytes(info);
        return out.toByteArray();
    }
}
