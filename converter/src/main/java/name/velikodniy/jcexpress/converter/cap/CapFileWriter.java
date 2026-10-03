package name.velikodniy.jcexpress.converter.cap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Assembles individually-generated CAP components into a single JAR (ZIP) archive,
 * producing the final {@code .cap} file (JCVM 3.1 §4.1.3, §6.2.1).
 *
 * <p>A CAP file in Compact format is a JAR archive in which each component is stored as a
 * separate entry at {@code <package/path>/javacard/<ComponentName>.cap} (Table 6-2). Each entry
 * contains the raw binary of the component, including its tag and size header. Components are
 * written in tag order.
 *
 * <p>The archive is reproducible: entries are written in a fixed order and carry a fixed
 * timestamp (1980-02-01 00:00, local DOS time, independent of the clock and the time zone), so
 * the same components always give the same bytes.
 *
 * <p>Component names by tag (JCVM 3.1 Table 6-1, Table 6-2):
 * <pre>
 *  Tag  Component         Spec Section
 *  ---  ----------------  ------------
 *   1   Header            JCVM 3.1 §6.4
 *   2   Directory         JCVM 3.1 §6.5
 *   3   Applet            JCVM 3.1 §6.6
 *   4   Import            JCVM 3.1 §6.7
 *   5   ConstantPool      JCVM 3.1 §6.8
 *   6   Class             JCVM 3.1 §6.9
 *   7   Method            JCVM 3.1 §6.10
 *   8   StaticField       JCVM 3.1 §6.11
 *   9   RefLocation       JCVM 3.1 §6.12
 *  10   Export            JCVM 3.1 §6.13
 *  11   Descriptor        JCVM 3.1 §6.14
 *  12   Debug             JCVM 3.1 §6.15
 * </pre>
 *
 * @see HeaderComponent#wrapComponent(int, byte[])
 */
public final class CapFileWriter {

    /**
     * Timestamp of every entry. DOS time cannot represent dates before 1980; February avoids
     * tools that convert 1980-01-01 00:00 to UTC and end up before the DOS epoch.
     */
    static final LocalDateTime ENTRY_TIME = LocalDateTime.of(1980, 2, 1, 0, 0);

    private static final String[] COMPONENT_NAMES = {
            null,           // 0 — unused
            "Header",       // 1
            "Directory",    // 2
            "Applet",       // 3
            "Import",       // 4
            "ConstantPool", // 5
            "Class",        // 6
            "Method",       // 7
            "StaticField",  // 8
            "RefLocation",  // 9
            "Export",       // 10
            "Descriptor",   // 11
            "Debug"         // 12
    };

    private CapFileWriter() {}

    /**
     * Writes a CAP file as a JAR containing component entries.
     *
     * @param packageName package name in slash notation (e.g. "com/example")
     * @param components  map of tag → component bytes (including tag+size header)
     * @return complete JAR file bytes
     * @throws IOException if writing fails
     * @throws IllegalArgumentException if {@code packageName} does not name a package directory
     *                                  (empty for the unnamed package, or with a leading or
     *                                  trailing {@code '/'})
     */
    public static byte[] write(String packageName, Map<Integer, byte[]> components) throws IOException {
        if (packageName.isEmpty() || packageName.startsWith("/") || packageName.endsWith("/")) {
            throw new IllegalArgumentException("Invalid package directory '" + packageName + "': CAP components"
                    + " are stored in <package>/javacard/ of a named package (JCVM 3.1 §4.1.3)");
        }
        var baos = new ByteArrayOutputStream();
        var manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        var manifestBytes = new ByteArrayOutputStream();
        manifest.write(manifestBytes);

        try (var jar = new JarOutputStream(baos)) {
            putEntry(jar, JarFile.MANIFEST_NAME, manifestBytes.toByteArray());

            // §4.1.3, §6.2.1: components are in the <package/path>/javacard/ directory
            String basePath = packageName + "/javacard/";
            putEntry(jar, basePath, null);

            // §6.2.1 Table 6-2: one <Name>.cap entry per component, in tag order
            for (int tag = 1; tag < COMPONENT_NAMES.length; tag++) {
                byte[] data = components.get(tag);
                if (data != null) {
                    putEntry(jar, basePath + COMPONENT_NAMES[tag] + ".cap", data);
                }
            }
        }

        return baos.toByteArray();
    }

    private static void putEntry(JarOutputStream jar, String name, byte[] data) throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setTimeLocal(ENTRY_TIME);
        jar.putNextEntry(entry);
        if (data != null) {
            jar.write(data);
        }
        jar.closeEntry();
    }
}
