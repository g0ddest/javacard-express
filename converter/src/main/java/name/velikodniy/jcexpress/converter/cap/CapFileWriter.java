package name.velikodniy.jcexpress.converter.cap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Assembles individually-generated CAP components into a single JAR (ZIP) archive,
 * producing the final {@code .cap} file as defined by JCVM 3.0.5 spec section 6.2.
 *
 * <p>A CAP file is a JAR archive where each component is stored as a separate entry
 * at the path {@code <package/path>/javacard/<ComponentName>.cap}. Components are
 * written in tag order (1-12), and each entry contains the raw binary of the
 * corresponding component including its tag and size header.
 *
 * <p>This class is the final step in Stage 6 of the converter pipeline: after all
 * component generators ({@link HeaderComponent}, {@link DirectoryComponent},
 * {@link AppletComponent}, etc.) have produced their binary payloads, this writer
 * packages them into the JAR structure.
 *
 * <p>Component names by tag number:
 * <pre>
 *  Tag  Component         Spec Section
 *  ---  ----------------  ------------
 *   1   Header            JCVM 3.0.5 §6.3
 *   2   Directory         JCVM 3.0.5 §6.4
 *   3   Applet            JCVM 3.0.5 §6.5
 *   4   Import            JCVM 3.0.5 §6.6
 *   5   ConstantPool      JCVM 3.0.5 §6.7
 *   6   Class             JCVM 3.0.5 §6.8
 *   7   Method            JCVM 3.0.5 §6.9
 *   8   StaticField       JCVM 3.0.5 §6.10
 *   9   RefLocation       JCVM 3.0.5 §6.11
 *  10   Export            JCVM 3.0.5 §6.12
 *  11   Descriptor        JCVM 3.0.5 §6.14
 *  12   Debug             JCVM 3.0.5 §6.13
 * </pre>
 *
 * @see HeaderComponent#wrapComponent(int, byte[])
 */
public final class CapFileWriter {

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
     */
    public static byte[] write(String packageName, Map<Integer, byte[]> components) throws IOException {
        // §6.2: A CAP file is a JAR (ZIP) archive with component entries
        var baos = new ByteArrayOutputStream();
        var manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");

        try (var jar = new JarOutputStream(baos, manifest)) {
            // §6.2: entries are at <package/path>/javacard/<ComponentName>.cap
            String basePath = packageName + "/javacard/";

            // §6.2: directory entry for the javacard/ folder
            jar.putNextEntry(new JarEntry(basePath));
            jar.closeEntry();

            // §6.2: components written in tag order (1-12)
            for (int tag = 1; tag <= 12; tag++) {
                byte[] data = components.get(tag);
                if (data == null) continue;

                // §6.2: each entry is <pkg>/javacard/<Name>.cap containing raw component bytes
                String entryName = basePath + COMPONENT_NAMES[tag] + ".cap";
                jar.putNextEntry(new JarEntry(entryName));
                jar.write(data);
                jar.closeEntry();
            }
        }

        return baos.toByteArray();
    }
}
