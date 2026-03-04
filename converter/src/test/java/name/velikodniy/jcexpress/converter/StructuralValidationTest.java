package name.velikodniy.jcexpress.converter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parameterized structural validation of CAP files for all 8 JavaCard versions.
 *
 * <p>Verifies that the converter produces structurally correct CAP files regardless
 * of the target JavaCard version: valid ZIP archive, correct component paths,
 * header magic, format versions, component tags, size fields, and API import versions.
 */
class StructuralValidationTest {

    private static final Path CLASSES_DIR = Path.of("target/test-classes");
    private static final String PACKAGE_AID = "A000000062010101";
    private static final String APPLET_AID = "A00000006201010101";
    private static final String PACKAGE_NAME = "com.example";

    private static final List<String> ALL_COMPONENT_NAMES = List.of(
            "Header.cap", "Directory.cap", "Applet.cap", "Import.cap",
            "ConstantPool.cap", "Class.cap", "Method.cap", "StaticField.cap",
            "RefLocation.cap", "Export.cap", "Descriptor.cap"
    );

    private static final Map<String, Integer> COMPONENT_TAGS = Map.ofEntries(
            Map.entry("Header.cap", 1),
            Map.entry("Directory.cap", 2),
            Map.entry("Applet.cap", 3),
            Map.entry("Import.cap", 4),
            Map.entry("ConstantPool.cap", 5),
            Map.entry("Class.cap", 6),
            Map.entry("Method.cap", 7),
            Map.entry("StaticField.cap", 8),
            Map.entry("RefLocation.cap", 9),
            Map.entry("Export.cap", 10),
            Map.entry("Descriptor.cap", 11)
    );

    /**
     * Expected framework minor version for each JavaCard version.
     * Major version is always 1 for the framework package.
     */
    private static int expectedFrameworkMinor(JavaCardVersion version) {
        return switch (version) {
            case V2_1_2 -> 0;
            case V2_2_1 -> 2;
            case V2_2_2 -> 3;
            case V3_0_3 -> 4;
            case V3_0_4 -> 5;
            case V3_0_5 -> 6;
            case V3_1_0 -> 8;
            case V3_2_0 -> 9;
        };
    }

    private ConverterResult convertForVersion(JavaCardVersion version) throws Exception {
        return Converter.builder()
                .classesDirectory(CLASSES_DIR)
                .packageName(PACKAGE_NAME)
                .packageAid(PACKAGE_AID)
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", APPLET_AID)
                .generateExport(true)
                .javaCardVersion(version)
                .build()
                .convert();
    }

    // ── 1. Valid ZIP archive with correct entry paths ──

    @ParameterizedTest(name = "{0}: CAP file is a valid ZIP with correct entry paths")
    @EnumSource(JavaCardVersion.class)
    void capFileShouldBeValidZipWithCorrectPaths(JavaCardVersion version) throws Exception {
        ConverterResult result = convertForVersion(version);
        Map<String, byte[]> components = extractAllComponents(result.capFile());

        assertThat(components).isNotEmpty();

        // All entries should follow the path pattern com/example/javacard/<Component>.cap
        try (var zis = new ZipInputStream(new ByteArrayInputStream(result.capFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.endsWith(".cap")) {
                    assertThat(name)
                            .as("Entry path for %s in %s", name, version)
                            .startsWith("com/example/javacard/");
                }
            }
        }
    }

    // ── 2. All 11 components present ──

    @ParameterizedTest(name = "{0}: all 11 components present")
    @EnumSource(JavaCardVersion.class)
    void allElevenComponentsShouldBePresent(JavaCardVersion version) throws Exception {
        ConverterResult result = convertForVersion(version);
        Map<String, byte[]> components = extractAllComponents(result.capFile());

        for (String componentName : ALL_COMPONENT_NAMES) {
            assertThat(components)
                    .as("Component %s should be present for %s", componentName, version)
                    .containsKey(componentName);
        }
    }

    // ── 3. Header magic 0xDECAFFED ──

    @ParameterizedTest(name = "{0}: header magic is 0xDECAFFED")
    @EnumSource(JavaCardVersion.class)
    void headerShouldContainCorrectMagic(JavaCardVersion version) throws Exception {
        ConverterResult result = convertForVersion(version);
        Map<String, byte[]> components = extractAllComponents(result.capFile());

        byte[] header = components.get("Header.cap");
        assertThat(header).as("Header component for %s", version).isNotNull();
        assertThat(header.length).as("Header length for %s", version).isGreaterThanOrEqualTo(7);

        // Bytes at offset 3-6 contain magic 0xDECAFFED
        int magic = ((header[3] & 0xFF) << 24)
                | ((header[4] & 0xFF) << 16)
                | ((header[5] & 0xFF) << 8)
                | (header[6] & 0xFF);
        assertThat(magic)
                .as("Header magic for %s", version)
                .isEqualTo(0xDECAFFED);
    }

    // ── 4. Header CAP format version ──

    @ParameterizedTest(name = "{0}: header format version matches JavaCardVersion")
    @EnumSource(JavaCardVersion.class)
    void headerFormatVersionShouldMatchJavaCardVersion(JavaCardVersion version) throws Exception {
        ConverterResult result = convertForVersion(version);
        Map<String, byte[]> components = extractAllComponents(result.capFile());

        byte[] header = components.get("Header.cap");
        assertThat(header).as("Header component for %s", version).isNotNull();
        assertThat(header.length).as("Header length for %s", version).isGreaterThanOrEqualTo(9);

        // Byte 7 = minor_version, byte 8 = major_version (after tag + u2 size + 4-byte magic)
        int minor = header[7] & 0xFF;
        int major = header[8] & 0xFF;

        assertThat(minor)
                .as("CAP format minor version for %s", version)
                .isEqualTo(version.formatMinor());
        assertThat(major)
                .as("CAP format major version for %s", version)
                .isEqualTo(version.formatMajor());
    }

    // ── 5. Component tags ──

    @ParameterizedTest(name = "{0}: each component has the correct tag byte")
    @EnumSource(JavaCardVersion.class)
    void componentsShouldHaveCorrectTags(JavaCardVersion version) throws Exception {
        ConverterResult result = convertForVersion(version);
        Map<String, byte[]> components = extractAllComponents(result.capFile());

        for (var entry : COMPONENT_TAGS.entrySet()) {
            String componentName = entry.getKey();
            int expectedTag = entry.getValue();

            byte[] data = components.get(componentName);
            assertThat(data)
                    .as("Component %s should exist for %s", componentName, version)
                    .isNotNull();
            assertThat(data[0] & 0xFF)
                    .as("Tag of %s for %s", componentName, version)
                    .isEqualTo(expectedTag);
        }
    }

    // ── 6. Component sizes ──

    @ParameterizedTest(name = "{0}: component u2 size field matches actual data length")
    @EnumSource(JavaCardVersion.class)
    void componentSizeFieldsShouldMatchActualLength(JavaCardVersion version) throws Exception {
        ConverterResult result = convertForVersion(version);
        Map<String, byte[]> components = extractAllComponents(result.capFile());

        for (String componentName : ALL_COMPONENT_NAMES) {
            byte[] data = components.get(componentName);
            assertThat(data)
                    .as("Component %s should exist for %s", componentName, version)
                    .isNotNull();
            assertThat(data.length)
                    .as("Component %s minimum length for %s", componentName, version)
                    .isGreaterThanOrEqualTo(3);

            // u2 size at bytes [1,2] represents the body size (total - 3 byte header)
            int declaredSize = ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
            int actualBodySize = data.length - 3;

            assertThat(declaredSize)
                    .as("Declared size of %s for %s (declared=%d, actual body=%d)",
                            componentName, version, declaredSize, actualBodySize)
                    .isEqualTo(actualBodySize);
        }
    }

    // ── 7. Import API version numbers ──

    @ParameterizedTest(name = "{0}: import component has correct framework API version")
    @EnumSource(JavaCardVersion.class)
    void importComponentShouldHaveCorrectFrameworkVersion(JavaCardVersion version) throws Exception {
        ConverterResult result = convertForVersion(version);
        Map<String, byte[]> components = extractAllComponents(result.capFile());

        byte[] importData = components.get("Import.cap");
        assertThat(importData)
                .as("Import component for %s", version)
                .isNotNull();
        assertThat(importData.length)
                .as("Import component length for %s", version)
                .isGreaterThanOrEqualTo(6);

        // Import component layout: tag(1) + size(2) + count(1) + first package_info:
        //   minor_version(1) + major_version(1) + AID_length(1) + AID[...]
        // The first imported package is javacard.framework
        int frameworkMinor = importData[4] & 0xFF;
        int frameworkMajor = importData[5] & 0xFF;

        assertThat(frameworkMajor)
                .as("Framework major version for %s", version)
                .isEqualTo(1);
        assertThat(frameworkMinor)
                .as("Framework minor version for %s", version)
                .isEqualTo(expectedFrameworkMinor(version));
    }

    // ── Helper ──

    private Map<String, byte[]> extractAllComponents(byte[] capFile) throws Exception {
        Map<String, byte[]> components = new LinkedHashMap<>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(capFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                int lastSlash = name.lastIndexOf('/');
                String componentName = lastSlash >= 0 ? name.substring(lastSlash + 1) : name;
                components.put(componentName, zis.readAllBytes());
            }
        }
        return components;
    }
}
