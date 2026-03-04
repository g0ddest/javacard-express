package name.velikodniy.jcexpress.converter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end test: compiles TestApplet.class → CAP file.
 * Verifies the full conversion pipeline from .class to .cap.
 */
class ConverterTest {

    @Test
    void shouldConvertTestAppletToCapFile() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .build()
                .convert();

        // CAP file should not be empty
        assertThat(result.capFile()).isNotEmpty();
        assertThat(result.capSize()).isGreaterThan(0);

        // Export file should not be empty
        assertThat(result.exportFile()).isNotEmpty();

        // No warnings for a clean applet
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void shouldProduceValidZipFile() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .build()
                .convert();

        // Verify it's a valid JAR/ZIP
        var entries = new java.util.ArrayList<String>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(result.capFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                zis.closeEntry();
            }
        }

        // Should contain component files at correct paths
        assertThat(entries).anyMatch(e -> e.contains("javacard/Header.cap"));
        assertThat(entries).anyMatch(e -> e.contains("javacard/Directory.cap"));
        assertThat(entries).anyMatch(e -> e.contains("javacard/Method.cap"));
        assertThat(entries).anyMatch(e -> e.contains("javacard/ConstantPool.cap"));
        assertThat(entries).anyMatch(e -> e.contains("javacard/Applet.cap"));
        assertThat(entries).anyMatch(e -> e.contains("javacard/Import.cap"));
        assertThat(entries).anyMatch(e -> e.contains("javacard/Class.cap"));
        assertThat(entries).anyMatch(e -> e.contains("javacard/StaticField.cap"));
    }

    @Test
    void shouldContainCorrectMagicInHeader() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .build()
                .convert();

        // Extract Header.cap from ZIP and verify magic
        byte[] headerData = extractComponent(result.capFile(), "Header.cap");
        assertThat(headerData).isNotNull();
        assertThat(headerData.length).isGreaterThanOrEqualTo(7);

        // tag=1, then u2 size, then magic 0xDECAFFED
        assertThat(headerData[0]).isEqualTo((byte) 1); // tag
        // bytes 3-6: magic number 0xDECAFFED
        int magic = ((headerData[3] & 0xFF) << 24) | ((headerData[4] & 0xFF) << 16)
                | ((headerData[5] & 0xFF) << 8) | (headerData[6] & 0xFF);
        assertThat(magic).isEqualTo(0xDECAFFED);
    }

    @Test
    void shouldAutoGenerateAidWhenNotSpecified() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();
    }

    @Test
    void shouldFailOnForbiddenUsages() {
        Path classesDir = Path.of("target/test-classes");

        assertThatThrownBy(() -> Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example.forbidden")
                .packageAid("A000000062020101")
                .packageVersion(1, 0)
                .build()
                .convert())
                .isInstanceOf(ConverterException.class);
    }

    @Test
    void shouldProduceRoundTrippableExportFile() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .build()
                .convert();

        // Verify export file can be read back
        var exportFile = name.velikodniy.jcexpress.converter.token.ExportFileReader.read(result.exportFile());
        assertThat(exportFile.packageName()).isEqualTo("com/example");
        assertThat(exportFile.aid()).containsExactly(
                (byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01, 0x01, 0x01);
    }

    @Test
    void shouldGenerateAidFromPackageName() {
        byte[] aid = Converter.Builder.generateAid("com.example");
        assertThat(aid).hasSize(8);
        assertThat(aid[0]).isEqualTo((byte) 0xF0); // Prefix
    }

    @Test
    void shouldProduceNonZeroConstantPoolEntries() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .build()
                .convert();

        // Extract ConstantPool component
        byte[] cpData = extractComponent(result.capFile(), "ConstantPool.cap");
        assertThat(cpData).isNotNull();
        assertThat(cpData.length).isGreaterThan(5); // tag(1) + size(2) + count(2) + at least one entry

        // tag=5, then u2 size, then u2 count
        assertThat(cpData[0]).isEqualTo((byte) 5);
        int count = ((cpData[3] & 0xFF) << 8) | (cpData[4] & 0xFF);
        assertThat(count).isGreaterThan(0);

        // Each entry is 4 bytes: tag(1) + info(3)
        // Verify at least some entries have non-zero info bytes (real offsets, not placeholders)
        boolean hasNonZeroEntry = false;
        for (int i = 0; i < count; i++) {
            int base = 5 + i * 4;
            int b1 = cpData[base + 1] & 0xFF;
            int b2 = cpData[base + 2] & 0xFF;
            int b3 = cpData[base + 3] & 0xFF;
            if (b1 != 0 || b2 != 0 || b3 != 0) {
                hasNonZeroEntry = true;
                break;
            }
        }
        assertThat(hasNonZeroEntry)
                .as("CP should contain entries with non-zero info (real offsets/tokens)")
                .isTrue();
    }

    @Test
    void shouldProduceClassComponentWithSuperclassRef() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .build()
                .convert();

        // Extract Class component
        byte[] classData = extractComponent(result.capFile(), "Class.cap");
        assertThat(classData).isNotNull();
        assertThat(classData.length).isGreaterThan(3);

        // tag=6, then u2 size
        assertThat(classData[0]).isEqualTo((byte) 6);

        // The Class component should have non-trivial content
        // (not just zeros for dispatch tables and superclass refs)
        int size = ((classData[1] & 0xFF) << 8) | (classData[2] & 0xFF);
        assertThat(size).isGreaterThan(5);
    }

    // ── Multi-class applet (cross-class field access + method calls) ──

    @Test
    void shouldConvertMultiClassApplet() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example.multiclass")
                .packageAid("A000000062030101")
                .packageVersion(1, 0)
                .applet("com.example.multiclass.MultiClassApplet", "A00000006203010101")
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();
        assertThat(result.warnings()).isEmpty();

        // Verify ZIP structure includes all expected components
        var entries = zipEntries(result.capFile());
        assertThat(entries).anyMatch(e -> e.contains("javacard/Header.cap"));
        assertThat(entries).anyMatch(e -> e.contains("javacard/Class.cap"));
        assertThat(entries).anyMatch(e -> e.contains("javacard/Method.cap"));
        assertThat(entries).anyMatch(e -> e.contains("javacard/ConstantPool.cap"));
        assertThat(entries).anyMatch(e -> e.contains("javacard/Applet.cap"));

        // Class component must be larger — contains both MultiClassApplet and Helper
        byte[] classData = extractComponent(result.capFile(), "Class.cap");
        assertThat(classData).isNotNull();
        int classSize = ((classData[1] & 0xFF) << 8) | (classData[2] & 0xFF);
        assertThat(classSize).as("Class component for 2 classes must be larger").isGreaterThan(15);

        // Header magic
        byte[] headerData = extractComponent(result.capFile(), "Header.cap");
        int magic = ((headerData[3] & 0xFF) << 24) | ((headerData[4] & 0xFF) << 16)
                | ((headerData[5] & 0xFF) << 8) | (headerData[6] & 0xFF);
        assertThat(magic).isEqualTo(0xDECAFFED);
    }

    // ── Interface applet (implements Shareable) ──

    @Test
    void shouldConvertInterfaceApplet() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example.iface")
                .packageAid("A000000062040101")
                .packageVersion(1, 0)
                .applet("com.example.iface.InterfaceApplet", "A00000006204010101")
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();
        assertThat(result.warnings()).isEmpty();

        // Verify Class component — InterfaceApplet implements Shareable
        byte[] classData = extractComponent(result.capFile(), "Class.cap");
        assertThat(classData).isNotNull();
        assertThat(classData[0]).isEqualTo((byte) 6); // tag

        // Header magic
        byte[] headerData = extractComponent(result.capFile(), "Header.cap");
        int magic = ((headerData[3] & 0xFF) << 24) | ((headerData[4] & 0xFF) << 16)
                | ((headerData[5] & 0xFF) << 8) | (headerData[6] & 0xFF);
        assertThat(magic).isEqualTo(0xDECAFFED);
    }

    // ── Exception applet (try/catch handlers) ──

    @Test
    void shouldConvertExceptionApplet() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example.exception")
                .packageAid("A000000062050101")
                .packageVersion(1, 0)
                .applet("com.example.exception.ExceptionApplet", "A00000006205010101")
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();
        assertThat(result.warnings()).isEmpty();

        // Method component should be larger due to exception handler table
        byte[] methodData = extractComponent(result.capFile(), "Method.cap");
        assertThat(methodData).isNotNull();
        assertThat(methodData[0]).isEqualTo((byte) 7); // tag
        int methodSize = ((methodData[1] & 0xFF) << 8) | (methodData[2] & 0xFF);
        assertThat(methodSize).as("Method component with exception handlers").isGreaterThan(10);

        // Header magic
        byte[] headerData = extractComponent(result.capFile(), "Header.cap");
        int magic = ((headerData[3] & 0xFF) << 24) | ((headerData[4] & 0xFF) << 16)
                | ((headerData[5] & 0xFF) << 8) | (headerData[6] & 0xFF);
        assertThat(magic).isEqualTo(0xDECAFFED);
    }

    // ── Inheritance applet (Base → Middle → InheritanceApplet) ──

    @Test
    void shouldConvertInheritanceApplet() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example.inherit")
                .packageAid("A000000062060101")
                .packageVersion(1, 0)
                .applet("com.example.inherit.InheritanceApplet", "A00000006206010101")
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();
        assertThat(result.warnings()).isEmpty();

        // Class component must contain 3 class_info entries (Base, Middle, Inheritance)
        byte[] classData = extractComponent(result.capFile(), "Class.cap");
        assertThat(classData).isNotNull();
        int classSize = ((classData[1] & 0xFF) << 8) | (classData[2] & 0xFF);
        assertThat(classSize).as("Class component for 3-level inheritance").isGreaterThan(20);

        // Header magic
        byte[] headerData = extractComponent(result.capFile(), "Header.cap");
        int magic = ((headerData[3] & 0xFF) << 24) | ((headerData[4] & 0xFF) << 16)
                | ((headerData[5] & 0xFF) << 8) | (headerData[6] & 0xFF);
        assertThat(magic).isEqualTo(0xDECAFFED);
    }

    // ── Verify all applet CAPs have correct component tags ──

    @Test
    void allAppletCapsShouldHaveCorrectComponentTags() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        String[][] testCases = {
            {"com.example.multiclass", "A000000062030101", "com.example.multiclass.MultiClassApplet", "A00000006203010101"},
            {"com.example.iface", "A000000062040101", "com.example.iface.InterfaceApplet", "A00000006204010101"},
            {"com.example.exception", "A000000062050101", "com.example.exception.ExceptionApplet", "A00000006205010101"},
            {"com.example.inherit", "A000000062060101", "com.example.inherit.InheritanceApplet", "A00000006206010101"},
        };

        for (String[] tc : testCases) {
            ConverterResult result = Converter.builder()
                    .classesDirectory(classesDir)
                    .packageName(tc[0])
                    .packageAid(tc[1])
                    .packageVersion(1, 0)
                    .applet(tc[2], tc[3])
                    .build()
                    .convert();

            // Verify each expected component has correct tag byte
            assertTag(result.capFile(), "Header.cap", 1, tc[0]);
            assertTag(result.capFile(), "Directory.cap", 2, tc[0]);
            assertTag(result.capFile(), "Applet.cap", 3, tc[0]);
            assertTag(result.capFile(), "Import.cap", 4, tc[0]);
            assertTag(result.capFile(), "ConstantPool.cap", 5, tc[0]);
            assertTag(result.capFile(), "Class.cap", 6, tc[0]);
            assertTag(result.capFile(), "Method.cap", 7, tc[0]);
            assertTag(result.capFile(), "StaticField.cap", 8, tc[0]);
            assertTag(result.capFile(), "RefLocation.cap", 9, tc[0]);
            assertTag(result.capFile(), "Descriptor.cap", 11, tc[0]);
        }
    }

    private void assertTag(byte[] capFile, String component, int expectedTag, String context) throws Exception {
        byte[] data = extractComponent(capFile, component);
        assertThat(data)
                .as("Component %s should exist for %s", component, context)
                .isNotNull();
        assertThat(data[0] & 0xFF)
                .as("Component %s tag for %s", component, context)
                .isEqualTo(expectedTag);
    }

    private static java.util.List<String> zipEntries(byte[] zipData) throws Exception {
        var entries = new java.util.ArrayList<String>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.add(entry.getName());
                zis.closeEntry();
            }
        }
        return entries;
    }

    private static byte[] extractComponent(byte[] capFile, String componentName) throws Exception {
        try (var zis = new ZipInputStream(new ByteArrayInputStream(capFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(componentName)) {
                    return zis.readAllBytes();
                }
                zis.closeEntry();
            }
        }
        return null;
    }

    // ── Multi-version support (T027, T046) ──

    @Test
    void shouldConvertWithJc212Version() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .javaCardVersion(JavaCardVersion.V2_1_2)
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();

        // Header format version: 2.1
        byte[] header = extractComponent(result.capFile(), "Header.cap");
        assertThat(header[7] & 0xFF).as("JC 2.1.2 format minor").isEqualTo(1);
        assertThat(header[8] & 0xFF).as("JC 2.1.2 format major").isEqualTo(2);

        // Import: framework version 1.0 for JC 2.1.2 (per Oracle SDK reference)
        byte[] importData = extractComponent(result.capFile(), "Import.cap");
        assertThat(importData[4] & 0xFF).as("framework minor version for JC 2.1.2").isEqualTo(0);
        assertThat(importData[5] & 0xFF).as("framework major version for JC 2.1.2").isEqualTo(1);
    }

    @Test
    void shouldConvertWithJc221Version() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .javaCardVersion(JavaCardVersion.V2_2_1)
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();

        // Header format version: 2.1
        byte[] header = extractComponent(result.capFile(), "Header.cap");
        assertThat(header[7] & 0xFF).as("JC 2.2.1 format minor").isEqualTo(1);
        assertThat(header[8] & 0xFF).as("JC 2.2.1 format major").isEqualTo(2);

        // Import: framework version 1.2 for JC 2.2.1
        byte[] importData = extractComponent(result.capFile(), "Import.cap");
        assertThat(importData[4] & 0xFF).as("framework minor version for JC 2.2.1").isEqualTo(2);
        assertThat(importData[5] & 0xFF).as("framework major version for JC 2.2.1").isEqualTo(1);
    }

    @Test
    void shouldConvertWithJc303Version() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .javaCardVersion(JavaCardVersion.V3_0_3)
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();

        // Header format version: 2.1
        byte[] header = extractComponent(result.capFile(), "Header.cap");
        assertThat(header[7] & 0xFF).as("JC 3.0.3 format minor").isEqualTo(1);
        assertThat(header[8] & 0xFF).as("JC 3.0.3 format major").isEqualTo(2);

        // Import: framework version 1.4 for JC 3.0.3
        byte[] importData = extractComponent(result.capFile(), "Import.cap");
        assertThat(importData[4] & 0xFF).as("framework minor version for JC 3.0.3").isEqualTo(4);
        assertThat(importData[5] & 0xFF).as("framework major version for JC 3.0.3").isEqualTo(1);
    }

    @Test
    void shouldConvertWithJc304Version() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .javaCardVersion(JavaCardVersion.V3_0_4)
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();

        // Header format version: 2.1 (same as other 3.0.x; Oracle SDK produces 2.1 despite spec mentioning 2.2)
        byte[] header = extractComponent(result.capFile(), "Header.cap");
        assertThat(header[7] & 0xFF).as("JC 3.0.4 format minor").isEqualTo(1);
        assertThat(header[8] & 0xFF).as("JC 3.0.4 format major").isEqualTo(2);

        // Import: framework version 1.5 for JC 3.0.4
        byte[] importData = extractComponent(result.capFile(), "Import.cap");
        assertThat(importData[4] & 0xFF).as("framework minor version for JC 3.0.4").isEqualTo(5);
        assertThat(importData[5] & 0xFF).as("framework major version for JC 3.0.4").isEqualTo(1);
    }

    @Test
    void shouldConvertWithJc222Version() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .javaCardVersion(JavaCardVersion.V2_2_2)
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();

        // Header format version: 2.1 (minor=1, major=2)
        byte[] header = extractComponent(result.capFile(), "Header.cap");
        assertThat(header[7] & 0xFF).as("JC 2.2.2 format minor").isEqualTo(1);
        assertThat(header[8] & 0xFF).as("JC 2.2.2 format major").isEqualTo(2);

        // Import component: framework version should be 1.3 for JC 2.2.2
        byte[] importData = extractComponent(result.capFile(), "Import.cap");
        // First import is javacard.framework: minor(1) + major(1) at offset 4
        assertThat(importData[4] & 0xFF).as("framework minor version for JC 2.2.2").isEqualTo(3);
        assertThat(importData[5] & 0xFF).as("framework major version for JC 2.2.2").isEqualTo(1);
    }

    @Test
    void shouldConvertWithJc305Version() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .javaCardVersion(JavaCardVersion.V3_0_5)
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();

        // Header format version: 2.1 (same as 2.2.2)
        byte[] header = extractComponent(result.capFile(), "Header.cap");
        assertThat(header[7] & 0xFF).as("JC 3.0.5 format minor").isEqualTo(1);
        assertThat(header[8] & 0xFF).as("JC 3.0.5 format major").isEqualTo(2);

        // Import: framework version 1.6 for JC 3.0.5
        byte[] importData = extractComponent(result.capFile(), "Import.cap");
        assertThat(importData[4] & 0xFF).as("framework minor version for JC 3.0.5").isEqualTo(6);
        assertThat(importData[5] & 0xFF).as("framework major version for JC 3.0.5").isEqualTo(1);
    }

    @Test
    void shouldConvertWithJc310Version() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .javaCardVersion(JavaCardVersion.V3_1_0)
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();

        // Header format version: 2.3
        byte[] header = extractComponent(result.capFile(), "Header.cap");
        assertThat(header[7] & 0xFF).as("JC 3.1.0 format minor").isEqualTo(3);
        assertThat(header[8] & 0xFF).as("JC 3.1.0 format major").isEqualTo(2);

        // Import: framework version 1.8 for JC 3.1.0
        byte[] importData = extractComponent(result.capFile(), "Import.cap");
        assertThat(importData[4] & 0xFF).as("framework minor version for JC 3.1.0").isEqualTo(8);
        assertThat(importData[5] & 0xFF).as("framework major version for JC 3.1.0").isEqualTo(1);
    }

    @Test
    void shouldConvertWithJc320Version() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .javaCardVersion(JavaCardVersion.V3_2_0)
                .build()
                .convert();

        assertThat(result.capFile()).isNotEmpty();

        // Header format version: 2.3 (same as 3.1.0)
        byte[] header = extractComponent(result.capFile(), "Header.cap");
        assertThat(header[7] & 0xFF).as("JC 3.2.0 format minor").isEqualTo(3);
        assertThat(header[8] & 0xFF).as("JC 3.2.0 format major").isEqualTo(2);
    }

    @Test
    void defaultVersionShouldBeJc305() throws Exception {
        Path classesDir = Path.of("target/test-classes");

        // Build without specifying javaCardVersion
        ConverterResult result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .build()
                .convert();

        // Default should be JC 3.0.5 = format 2.1
        byte[] header = extractComponent(result.capFile(), "Header.cap");
        assertThat(header[7] & 0xFF).as("default format minor (JC 3.0.5)").isEqualTo(1);
        assertThat(header[8] & 0xFF).as("default format major (JC 3.0.5)").isEqualTo(2);
    }
}
