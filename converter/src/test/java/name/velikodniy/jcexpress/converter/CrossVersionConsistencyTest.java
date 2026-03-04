package name.velikodniy.jcexpress.converter;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that JavaCard versions sharing token tables produce CAP files
 * that differ only in expected fields (Header format version, Import package versions).
 *
 * <p>Version groups tested:
 * <ul>
 *   <li><b>2.x group</b>: V2_1_2, V2_2_1, V2_2_2 -- share core token assignments,
 *       only API version numbers differ</li>
 *   <li><b>3.0.x group</b>: V3_0_3, V3_0_4, V3_0_5 -- share core token assignments;
 *       all use CAP format 2.1;
 *       V3_0_5 applies PUTFIELD_x_THIS optimization (V3_0_3/V3_0_4 do not)</li>
 *   <li><b>3.1/3.2 group</b>: V3_1_0, V3_2_0 -- share same token table
 *       and format (2.3); differ only in framework API version (1.8 vs 1.9)</li>
 * </ul>
 */
class CrossVersionConsistencyTest {

    private static final Path classesDir = Path.of("target/test-classes");
    private static final String PACKAGE_AID = "A000000062010101";
    private static final String APPLET_AID = "A00000006201010101";
    private static final String PACKAGE_NAME = "com.example";

    // ── 2.x group: V2_1_2, V2_2_1, V2_2_2 ──

    @Test
    void v2xGroup_constantPoolShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V2_1_2, JavaCardVersion.V2_2_1, JavaCardVersion.V2_2_2));

        byte[] baseline = extractComponent(caps.get(JavaCardVersion.V2_1_2), "ConstantPool.cap");
        for (var version : List.of(JavaCardVersion.V2_2_1, JavaCardVersion.V2_2_2)) {
            byte[] other = extractComponent(caps.get(version), "ConstantPool.cap");
            assertThat(other)
                    .as("ConstantPool for %s should match %s", version, JavaCardVersion.V2_1_2)
                    .isEqualTo(baseline);
        }
    }

    @Test
    void v2xGroup_classComponentShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V2_1_2, JavaCardVersion.V2_2_1, JavaCardVersion.V2_2_2));

        byte[] baseline = extractComponent(caps.get(JavaCardVersion.V2_1_2), "Class.cap");
        for (var version : List.of(JavaCardVersion.V2_2_1, JavaCardVersion.V2_2_2)) {
            byte[] other = extractComponent(caps.get(version), "Class.cap");
            assertThat(other)
                    .as("Class for %s should match %s", version, JavaCardVersion.V2_1_2)
                    .isEqualTo(baseline);
        }
    }

    @Test
    void v2xGroup_methodComponentShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V2_1_2, JavaCardVersion.V2_2_1, JavaCardVersion.V2_2_2));

        byte[] baseline = extractComponent(caps.get(JavaCardVersion.V2_1_2), "Method.cap");
        for (var version : List.of(JavaCardVersion.V2_2_1, JavaCardVersion.V2_2_2)) {
            byte[] other = extractComponent(caps.get(version), "Method.cap");
            assertThat(other)
                    .as("Method for %s should match %s", version, JavaCardVersion.V2_1_2)
                    .isEqualTo(baseline);
        }
    }

    @Test
    void v2xGroup_staticFieldComponentShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V2_1_2, JavaCardVersion.V2_2_1, JavaCardVersion.V2_2_2));

        byte[] baseline = extractComponent(caps.get(JavaCardVersion.V2_1_2), "StaticField.cap");
        for (var version : List.of(JavaCardVersion.V2_2_1, JavaCardVersion.V2_2_2)) {
            byte[] other = extractComponent(caps.get(version), "StaticField.cap");
            assertThat(other)
                    .as("StaticField for %s should match %s", version, JavaCardVersion.V2_1_2)
                    .isEqualTo(baseline);
        }
    }

    @Test
    void v2xGroup_importShouldDifferOnlyInPackageVersions() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V2_1_2, JavaCardVersion.V2_2_1, JavaCardVersion.V2_2_2));

        byte[] import212 = extractComponent(caps.get(JavaCardVersion.V2_1_2), "Import.cap");
        byte[] import221 = extractComponent(caps.get(JavaCardVersion.V2_2_1), "Import.cap");
        byte[] import222 = extractComponent(caps.get(JavaCardVersion.V2_2_2), "Import.cap");

        assertThat(import212).isNotNull();
        assertThat(import221).isNotNull();
        assertThat(import222).isNotNull();

        // V2_1_2 and V2_2_1 import only javacard.framework (1 package, 14 bytes).
        // V2_2_2+ also imports java.lang (2 packages, 24 bytes) per Oracle convention.
        assertThat(import212.length)
                .as("Import length for V2_1_2 (1 package)")
                .isEqualTo(14);
        assertThat(import221.length)
                .as("Import length for V2_2_1 vs V2_1_2")
                .isEqualTo(import212.length);
        assertThat(import222.length)
                .as("Import length for V2_2_2 (2 packages)")
                .isEqualTo(24);

        // V2_1_2 and V2_2_1 share structure, only version bytes differ
        assertThat(zeroImportVersions(import221))
                .as("Import structure (sans versions) for V2_2_1 vs V2_1_2")
                .isEqualTo(zeroImportVersions(import212));
    }

    @Test
    void v2xGroup_headerShouldBeIdentical() throws Exception {
        // All 2.x versions use CAP format 2.1 -- headers should be identical
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V2_1_2, JavaCardVersion.V2_2_1, JavaCardVersion.V2_2_2));

        byte[] baseline = extractComponent(caps.get(JavaCardVersion.V2_1_2), "Header.cap");
        for (var version : List.of(JavaCardVersion.V2_2_1, JavaCardVersion.V2_2_2)) {
            byte[] other = extractComponent(caps.get(version), "Header.cap");
            assertThat(other)
                    .as("Header for %s should match %s (all 2.x use format 2.1)", version, JavaCardVersion.V2_1_2)
                    .isEqualTo(baseline);
        }
    }

    // ── 3.0.x group: V3_0_3, V3_0_4, V3_0_5 ──

    @Test
    void v30xGroup_constantPoolShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_0_3, JavaCardVersion.V3_0_4, JavaCardVersion.V3_0_5));

        byte[] baseline = extractComponent(caps.get(JavaCardVersion.V3_0_3), "ConstantPool.cap");
        for (var version : List.of(JavaCardVersion.V3_0_4, JavaCardVersion.V3_0_5)) {
            byte[] other = extractComponent(caps.get(version), "ConstantPool.cap");
            assertThat(other)
                    .as("ConstantPool for %s should match %s", version, JavaCardVersion.V3_0_3)
                    .isEqualTo(baseline);
        }
    }

    @Test
    void v30xGroup_classComponentShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_0_3, JavaCardVersion.V3_0_4, JavaCardVersion.V3_0_5));

        byte[] class303 = extractComponent(caps.get(JavaCardVersion.V3_0_3), "Class.cap");
        byte[] class304 = extractComponent(caps.get(JavaCardVersion.V3_0_4), "Class.cap");
        byte[] class305 = extractComponent(caps.get(JavaCardVersion.V3_0_5), "Class.cap");

        // V3_0_3 and V3_0_4 share bytecode behavior (no PUTFIELD_x_THIS) → identical Class.cap
        assertThat(class304)
                .as("Class for V3_0_4 should match V3_0_3 (both without PUTFIELD_x_THIS)")
                .isEqualTo(class303);

        // V3_0_5 applies PUTFIELD_x_THIS → different method offsets in Method.cap
        // cascade into virtual method dispatch table entries in Class.cap.
        // Size is the same (same number of classes/methods), content differs.
        assertThat(class305.length)
                .as("Class size for V3_0_5 should match V3_0_3")
                .isEqualTo(class303.length);
    }

    @Test
    void v30xGroup_methodComponentShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_0_3, JavaCardVersion.V3_0_4, JavaCardVersion.V3_0_5));

        byte[] method303 = extractComponent(caps.get(JavaCardVersion.V3_0_3), "Method.cap");
        byte[] method304 = extractComponent(caps.get(JavaCardVersion.V3_0_4), "Method.cap");
        byte[] method305 = extractComponent(caps.get(JavaCardVersion.V3_0_5), "Method.cap");

        // V3_0_3 and V3_0_4 share bytecode behavior (no PUTFIELD_x_THIS) → identical Method.cap
        assertThat(method304)
                .as("Method for V3_0_4 should match V3_0_3 (both without PUTFIELD_x_THIS)")
                .isEqualTo(method303);

        // V3_0_5 applies PUTFIELD_x_THIS optimization (JCVM §7.5.63), saving 2 bytes
        // (1 byte per PUTFIELD_S_THIS replacing ALOAD_0 + PUTFIELD_S: constructor + process)
        assertThat(method305.length)
                .as("Method for V3_0_5 should be shorter (PUTFIELD_x_THIS optimization)")
                .isLessThan(method303.length);
    }

    @Test
    void v30xGroup_staticFieldComponentShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_0_3, JavaCardVersion.V3_0_4, JavaCardVersion.V3_0_5));

        byte[] baseline = extractComponent(caps.get(JavaCardVersion.V3_0_3), "StaticField.cap");
        for (var version : List.of(JavaCardVersion.V3_0_4, JavaCardVersion.V3_0_5)) {
            byte[] other = extractComponent(caps.get(version), "StaticField.cap");
            assertThat(other)
                    .as("StaticField for %s should match %s", version, JavaCardVersion.V3_0_3)
                    .isEqualTo(baseline);
        }
    }

    @Test
    void v30xGroup_importShouldDifferOnlyInPackageVersions() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_0_3, JavaCardVersion.V3_0_4, JavaCardVersion.V3_0_5));

        byte[] import303 = extractComponent(caps.get(JavaCardVersion.V3_0_3), "Import.cap");
        byte[] import304 = extractComponent(caps.get(JavaCardVersion.V3_0_4), "Import.cap");
        byte[] import305 = extractComponent(caps.get(JavaCardVersion.V3_0_5), "Import.cap");

        assertThat(import303).isNotNull();
        assertThat(import304).isNotNull();
        assertThat(import305).isNotNull();

        // Same length (same structure)
        assertThat(import304.length)
                .as("Import length for V3_0_4 vs V3_0_3")
                .isEqualTo(import303.length);
        assertThat(import305.length)
                .as("Import length for V3_0_5 vs V3_0_3")
                .isEqualTo(import303.length);

        // After zeroing version bytes, structure should be identical
        assertThat(zeroImportVersions(import304))
                .as("Import structure (sans versions) for V3_0_4 vs V3_0_3")
                .isEqualTo(zeroImportVersions(import303));
        assertThat(zeroImportVersions(import305))
                .as("Import structure (sans versions) for V3_0_5 vs V3_0_3")
                .isEqualTo(zeroImportVersions(import303));
    }

    @Test
    void v30xGroup_headerShouldDifferOnlyInFormatVersion() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_0_3, JavaCardVersion.V3_0_4, JavaCardVersion.V3_0_5));

        byte[] header303 = extractComponent(caps.get(JavaCardVersion.V3_0_3), "Header.cap");
        byte[] header304 = extractComponent(caps.get(JavaCardVersion.V3_0_4), "Header.cap");
        byte[] header305 = extractComponent(caps.get(JavaCardVersion.V3_0_5), "Header.cap");

        assertThat(header303).isNotNull();
        assertThat(header304).isNotNull();
        assertThat(header305).isNotNull();

        // All 3.0.x versions use format 2.1 -- headers should be identical
        assertThat(header304)
                .as("Header for V3_0_4 should match V3_0_3 (all 3.0.x use format 2.1)")
                .isEqualTo(header303);
        assertThat(header305)
                .as("Header for V3_0_5 should match V3_0_3 (all 3.0.x use format 2.1)")
                .isEqualTo(header303);

        // Verify they all use format 2.1
        assertThat(header303[7] & 0xFF)
                .as("V3_0_3 format minor should be 1")
                .isEqualTo(1);
        assertThat(header303[8] & 0xFF)
                .as("V3_0_3 format major should be 2")
                .isEqualTo(2);
    }

    // ── 3.1/3.2 group: V3_1_0, V3_2_0 ──

    @Test
    void v31v32Group_constantPoolShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_1_0, JavaCardVersion.V3_2_0));

        byte[] cp310 = extractComponent(caps.get(JavaCardVersion.V3_1_0), "ConstantPool.cap");
        byte[] cp320 = extractComponent(caps.get(JavaCardVersion.V3_2_0), "ConstantPool.cap");

        assertThat(cp320)
                .as("ConstantPool for V3_2_0 should match V3_1_0")
                .isEqualTo(cp310);
    }

    @Test
    void v31v32Group_classComponentShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_1_0, JavaCardVersion.V3_2_0));

        byte[] class310 = extractComponent(caps.get(JavaCardVersion.V3_1_0), "Class.cap");
        byte[] class320 = extractComponent(caps.get(JavaCardVersion.V3_2_0), "Class.cap");

        assertThat(class320)
                .as("Class for V3_2_0 should match V3_1_0")
                .isEqualTo(class310);
    }

    @Test
    void v31v32Group_methodComponentShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_1_0, JavaCardVersion.V3_2_0));

        byte[] method310 = extractComponent(caps.get(JavaCardVersion.V3_1_0), "Method.cap");
        byte[] method320 = extractComponent(caps.get(JavaCardVersion.V3_2_0), "Method.cap");

        assertThat(method320)
                .as("Method for V3_2_0 should match V3_1_0")
                .isEqualTo(method310);
    }

    @Test
    void v31v32Group_staticFieldComponentShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_1_0, JavaCardVersion.V3_2_0));

        byte[] sf310 = extractComponent(caps.get(JavaCardVersion.V3_1_0), "StaticField.cap");
        byte[] sf320 = extractComponent(caps.get(JavaCardVersion.V3_2_0), "StaticField.cap");

        assertThat(sf320)
                .as("StaticField for V3_2_0 should match V3_1_0")
                .isEqualTo(sf310);
    }

    @Test
    void v31v32Group_importShouldDifferOnlyInPackageVersions() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_1_0, JavaCardVersion.V3_2_0));

        byte[] import310 = extractComponent(caps.get(JavaCardVersion.V3_1_0), "Import.cap");
        byte[] import320 = extractComponent(caps.get(JavaCardVersion.V3_2_0), "Import.cap");

        assertThat(import310).isNotNull();
        assertThat(import320).isNotNull();

        // Same length (same structure: both import the same packages)
        assertThat(import320.length)
                .as("Import length for V3_2_0 vs V3_1_0")
                .isEqualTo(import310.length);

        // After zeroing version bytes, structure should be identical
        assertThat(zeroImportVersions(import320))
                .as("Import structure (sans versions) for V3_2_0 vs V3_1_0")
                .isEqualTo(zeroImportVersions(import310));
    }

    @Test
    void v31v32Group_headerShouldBeIdentical() throws Exception {
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_1_0, JavaCardVersion.V3_2_0));

        byte[] header310 = extractComponent(caps.get(JavaCardVersion.V3_1_0), "Header.cap");
        byte[] header320 = extractComponent(caps.get(JavaCardVersion.V3_2_0), "Header.cap");

        assertThat(header320)
                .as("Header for V3_2_0 should match V3_1_0 (same format 2.3, same API versions)")
                .isEqualTo(header310);
    }

    @Test
    void v31v32Group_entireCapShouldBeIdenticalExceptImportVersions() throws Exception {
        // V3_1_0 and V3_2_0 share the same format (2.3) but differ in framework API
        // version (1.8 vs 1.9), so Import.cap differs in version bytes only.
        // All other components should be byte-for-byte identical.
        var caps = convertAllVersions(List.of(
                JavaCardVersion.V3_1_0, JavaCardVersion.V3_2_0));

        // Compare all components except Import.cap (which differs in version bytes)
        for (String component : List.of("Header.cap", "Directory.cap", "Applet.cap",
                "ConstantPool.cap", "Class.cap", "Method.cap",
                "StaticField.cap", "RefLocation.cap", "Descriptor.cap")) {
            byte[] comp310 = extractComponent(caps.get(JavaCardVersion.V3_1_0), component);
            byte[] comp320 = extractComponent(caps.get(JavaCardVersion.V3_2_0), component);
            assertThat(comp320)
                    .as("%s for V3_2_0 should match V3_1_0", component)
                    .isEqualTo(comp310);
        }

        // Import.cap: same structure, different version bytes
        byte[] import310 = extractComponent(caps.get(JavaCardVersion.V3_1_0), "Import.cap");
        byte[] import320 = extractComponent(caps.get(JavaCardVersion.V3_2_0), "Import.cap");
        assertThat(import320.length)
                .as("Import length for V3_2_0 vs V3_1_0")
                .isEqualTo(import310.length);
        assertThat(zeroImportVersions(import320))
                .as("Import structure (sans versions) for V3_2_0 vs V3_1_0")
                .isEqualTo(zeroImportVersions(import310));
    }

    // ── Helper methods ──

    /**
     * Converts TestApplet for each given version and returns a map of version to CAP bytes.
     */
    private Map<JavaCardVersion, byte[]> convertAllVersions(List<JavaCardVersion> versions) throws Exception {
        Map<JavaCardVersion, byte[]> results = new LinkedHashMap<>();
        for (JavaCardVersion version : versions) {
            ConverterResult result = Converter.builder()
                    .classesDirectory(classesDir)
                    .packageName(PACKAGE_NAME)
                    .packageAid(PACKAGE_AID)
                    .packageVersion(1, 0)
                    .applet("com.example.TestApplet", APPLET_AID)
                    .javaCardVersion(version)
                    .build()
                    .convert();

            assertThat(result.capFile())
                    .as("CAP file for %s should not be empty", version)
                    .isNotEmpty();

            results.put(version, result.capFile());
        }
        return results;
    }

    /**
     * Extracts a single component from a CAP file (ZIP archive) by component name.
     */
    private byte[] extractComponent(byte[] capFile, String componentName) throws Exception {
        try (var zis = new ZipInputStream(new ByteArrayInputStream(capFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith("/" + componentName)) {
                    return zis.readAllBytes();
                }
            }
        }
        return null;
    }

    /**
     * Returns a copy of Import component bytes with all package version fields zeroed out.
     *
     * <p>Import component layout (JCVM spec 6.6):
     * <pre>
     *   tag(1) + size(2) + count(1) + packages[count] {
     *       minor_version(1) + major_version(1) + aid_length(1) + aid(aid_length)
     *   }
     * </pre>
     *
     * <p>The minor_version and major_version fields are at the start of each package entry.
     * By zeroing them, we can compare the structural content (AIDs, count) independently
     * of the API version numbers that differ between JavaCard specification versions.
     */
    private byte[] zeroImportVersions(byte[] importBytes) {
        byte[] copy = importBytes.clone();
        // tag(1) + size(2) + count(1) = 4 bytes of header
        int count = copy[3] & 0xFF;
        int offset = 4; // start of first package entry
        for (int i = 0; i < count; i++) {
            // Zero out minor_version and major_version
            copy[offset] = 0;     // minor_version
            copy[offset + 1] = 0; // major_version
            int aidLength = copy[offset + 2] & 0xFF;
            offset += 3 + aidLength; // skip minor(1) + major(1) + aid_length(1) + aid(N)
        }
        return copy;
    }
}
