package name.velikodniy.jcexpress.converter;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared test utilities for CAP file extraction and comparison.
 */
final class CapTestUtils {

    static final String[] COMPONENTS = {
            "Header.cap", "Directory.cap", "Applet.cap", "Import.cap",
            "ConstantPool.cap", "Class.cap", "Method.cap", "StaticField.cap",
            "RefLocation.cap", "Descriptor.cap"
    };

    private CapTestUtils() {}

    /**
     * Extracts all .cap component files from a ZIP archive into a map keyed by simple name.
     */
    static Map<String, byte[]> extractComponents(byte[] capFile) throws Exception {
        Map<String, byte[]> map = new LinkedHashMap<>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(capFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (!name.endsWith(".cap")) {
                    zis.closeEntry();
                    continue;
                }
                String simpleName = name.substring(name.lastIndexOf('/') + 1);
                map.put(simpleName, zis.readAllBytes());
                zis.closeEntry();
            }
        }
        return map;
    }
}
