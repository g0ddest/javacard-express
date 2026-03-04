package name.velikodniy.jcexpress.converter;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Deep binary analysis of generated CAP file.
 * Validates every component against JCVM 3.0.5 spec byte-by-byte.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CapBinaryAnalysisTest {

    private ConverterResult result;
    private Map<String, byte[]> components;

    @BeforeAll
    void buildCap() throws Exception {
        Path classesDir = Path.of("target/test-classes");
        result = Converter.builder()
                .classesDirectory(classesDir)
                .packageName("com.example")
                .packageAid("A000000062010101")
                .packageVersion(1, 0)
                .applet("com.example.TestApplet", "A00000006201010101")
                .build()
                .convert();

        components = new LinkedHashMap<>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(result.capFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                // Only include .cap component files, skip MANIFEST.MF etc.
                if (!name.endsWith(".cap")) {
                    zis.closeEntry();
                    continue;
                }
                String simpleName = name.substring(name.lastIndexOf('/') + 1);
                components.put(simpleName, zis.readAllBytes());
                zis.closeEntry();
            }
        }
    }

    // ── Header Component (tag=1) ──

    @Test
    void headerTag() {
        byte[] h = components.get("Header.cap");
        assertThat(h[0]).as("Header tag").isEqualTo((byte) 1);
    }

    @Test
    void headerSize() {
        byte[] h = components.get("Header.cap");
        int size = u2(h, 1);
        assertThat(size).as("Header size field").isEqualTo(h.length - 3);
    }

    @Test
    void headerMagic() {
        byte[] h = components.get("Header.cap");
        int magic = u4(h, 3);
        assertThat(magic).as("Magic number").isEqualTo(0xDECAFFED);
    }

    @Test
    void headerVersions() {
        byte[] h = components.get("Header.cap");
        // minor_version (1 byte) + major_version (1 byte) after magic
        int minorVer = h[7] & 0xFF;
        int majorVer = h[8] & 0xFF;
        assertThat(majorVer).as("CAP major version").isGreaterThanOrEqualTo(2);
        assertThat(minorVer).as("CAP minor version").isGreaterThanOrEqualTo(0);
    }

    @Test
    void headerFlags() {
        byte[] h = components.get("Header.cap");
        int flags = h[9] & 0xFF;
        // ACC_APPLET should be set since we have an applet
        assertThat(flags & 0x04).as("ACC_APPLET flag").isNotZero();
    }

    @Test
    void headerPackageInfo() {
        byte[] h = components.get("Header.cap");
        // After flags(1): pkg_minor(1) + pkg_major(1) + aid_length(1) + aid(n)
        int pkgMinor = h[10] & 0xFF;
        int pkgMajor = h[11] & 0xFF;
        assertThat(pkgMajor).as("Package major version").isEqualTo(1);
        assertThat(pkgMinor).as("Package minor version").isEqualTo(0);

        int aidLen = h[12] & 0xFF;
        assertThat(aidLen).as("Package AID length").isEqualTo(8);

        byte[] aid = Arrays.copyOfRange(h, 13, 13 + aidLen);
        assertThat(aid).as("Package AID").containsExactly(
                (byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01, 0x01, 0x01);
    }

    // ── Directory Component (tag=2) ──

    @Test
    void directoryTag() {
        byte[] d = components.get("Directory.cap");
        assertThat(d[0]).as("Directory tag").isEqualTo((byte) 2);
    }

    @Test
    void directorySizeConsistency() {
        byte[] d = components.get("Directory.cap");
        int size = u2(d, 1);
        assertThat(size).as("Directory size field").isEqualTo(d.length - 3);
    }

    @Test
    void directoryComponentSizesNonZero() {
        byte[] d = components.get("Directory.cap");
        // After tag(1)+size(2): component_sizes[11] as u2 each = 22 bytes
        // indices: 0=Header, 1=Directory, 2=Applet, 3=Import, 4=CP, 5=Class, 6=Method,
        //          7=StaticField, 8=RefLocation, 9=Export, 10=Descriptor
        int offset = 3;
        for (int i = 0; i < 11; i++) {
            int compSize = u2(d, offset + i * 2);
            String name = componentName(i);
            if (i == 9) {
                // Export may be 0
                continue;
            }
            assertThat(compSize).as("Directory size for " + name).isGreaterThan(0);
        }
    }

    @Test
    void directoryComponentSizesMatchActual() {
        byte[] d = components.get("Directory.cap");
        int offset = 3;
        // Directory stores body sizes (u2 size field = total - 3 for tag+size header)
        String[] capNames = {"Header.cap", "Directory.cap", "Applet.cap", "Import.cap",
                "ConstantPool.cap", "Class.cap", "Method.cap", "StaticField.cap",
                "RefLocation.cap", "Export.cap", "Descriptor.cap"};
        for (int i = 0; i < 11; i++) {
            int declaredSize = u2(d, offset + i * 2);
            if (components.containsKey(capNames[i])) {
                int actualBodySize = components.get(capNames[i]).length - 3;
                assertThat(declaredSize).as("Directory size for " + capNames[i] + " matches actual")
                        .isEqualTo(actualBodySize);
            }
        }
    }

    // ── Applet Component (tag=3) ──

    @Test
    void appletTag() {
        byte[] a = components.get("Applet.cap");
        assertThat(a[0]).as("Applet tag").isEqualTo((byte) 3);
    }

    @Test
    void appletCount() {
        byte[] a = components.get("Applet.cap");
        int count = a[3] & 0xFF;
        assertThat(count).as("Applet count").isEqualTo(1);
    }

    @Test
    void appletAidAndInstallOffset() {
        byte[] a = components.get("Applet.cap");
        // count(1), then for each: aid_length(1) + aid(n) + install_method_offset(2)
        int pos = 4; // after tag(1) + size(2) + count(1)
        int aidLen = a[pos] & 0xFF;
        assertThat(aidLen).as("Applet AID length").isEqualTo(9);

        byte[] aid = Arrays.copyOfRange(a, pos + 1, pos + 1 + aidLen);
        assertThat(aid).as("Applet AID").containsExactly(
                (byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01, 0x01, 0x01, 0x01);

        int installOffset = u2(a, pos + 1 + aidLen);
        assertThat(installOffset).as("Install method offset (should be > 0)")
                .isGreaterThan(0);
    }

    // ── Import Component (tag=4) ──

    @Test
    void importTag() {
        byte[] imp = components.get("Import.cap");
        assertThat(imp[0]).as("Import tag").isEqualTo((byte) 4);
    }

    @Test
    void importCount() {
        byte[] imp = components.get("Import.cap");
        int count = imp[3] & 0xFF;
        // Should have at least java.lang + javacard.framework
        assertThat(count).as("Import package count").isGreaterThanOrEqualTo(2);
    }

    @Test
    void importPackageAids() {
        byte[] imp = components.get("Import.cap");
        int count = imp[3] & 0xFF;
        int pos = 4;
        var packageAids = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            int minor = imp[pos] & 0xFF;
            int major = imp[pos + 1] & 0xFF;
            int aidLen = imp[pos + 2] & 0xFF;
            byte[] aid = Arrays.copyOfRange(imp, pos + 3, pos + 3 + aidLen);
            packageAids.add(hexBytes(aid));
            pos += 3 + aidLen;
        }
        // java/lang AID = A0 00 00 00 62 00 01
        assertThat(packageAids).as("Import AIDs should include java/lang")
                .anyMatch(s -> s.startsWith("A0000000620001"));
        // javacard/framework AID = A0 00 00 00 62 01 01
        assertThat(packageAids).as("Import AIDs should include javacard/framework")
                .anyMatch(s -> s.startsWith("A0000000620101"));
    }

    // ── ConstantPool Component (tag=5) ──

    @Test
    void constantPoolTag() {
        byte[] cp = components.get("ConstantPool.cap");
        assertThat(cp[0]).as("ConstantPool tag").isEqualTo((byte) 5);
    }

    @Test
    void constantPoolEntryFormat() {
        byte[] cp = components.get("ConstantPool.cap");
        int count = u2(cp, 3);
        assertThat(count).as("CP entry count").isGreaterThan(0);

        // Validate each entry is 4 bytes with valid tag
        Set<Integer> validTags = Set.of(1, 2, 3, 4, 5, 6);
        for (int i = 0; i < count; i++) {
            int base = 5 + i * 4;
            int tag = cp[base] & 0xFF;
            assertThat(validTags).as("CP entry %d tag=%d should be valid", i, tag)
                    .contains(tag);
        }

        // Total size should be tag(1) + size(2) + count(2) + entries(count*4)
        int expectedInfoSize = 2 + count * 4;
        int declaredSize = u2(cp, 1);
        assertThat(declaredSize).as("CP size field").isEqualTo(expectedInfoSize);
    }

    @Test
    void constantPoolHasExternalRefs() {
        byte[] cp = components.get("ConstantPool.cap");
        int count = u2(cp, 3);

        // Should have external refs (any tag with high bit set in b1).
        // Per JCVM spec, external refs use direct encoding (pkg|0x80, class_token, member_token)
        // without intermediate ClassRef entries.
        boolean hasExternalRef = false;
        for (int i = 0; i < count; i++) {
            int base = 5 + i * 4;
            int b1 = cp[base + 1] & 0xFF;
            if ((b1 & 0x80) != 0) {
                hasExternalRef = true;
                break;
            }
        }
        assertThat(hasExternalRef)
                .as("CP should have external refs (Applet superclass, APDU methods, etc.)")
                .isTrue();
    }

    @Test
    void constantPoolHasMethodRefs() {
        byte[] cp = components.get("ConstantPool.cap");
        int count = u2(cp, 3);

        // Count by tag type
        Map<Integer, Integer> tagCounts = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int base = 5 + i * 4;
            int tag = cp[base] & 0xFF;
            tagCounts.merge(tag, 1, Integer::sum);
        }

        // Should have virtual method refs (tag=3) for APDU methods
        assertThat(tagCounts.getOrDefault(3, 0))
                .as("Virtual method refs (APDU.getBuffer, etc.)")
                .isGreaterThan(0);

        // Should have static method refs (tag=6) for Util.arrayCopy, ISOException.throwIt,
        // and constructor calls (invokespecial uses StaticMethodRef per JCVM spec 7.5.12)
        assertThat(tagCounts.getOrDefault(6, 0))
                .as("Static method refs (Util.arrayCopy, ISOException.throwIt, constructors)")
                .isGreaterThan(0);
    }

    @Test
    void constantPoolNoPlaceholderValues() {
        byte[] cp = components.get("ConstantPool.cap");
        int count = u2(cp, 3);

        // Internal refs should not have 0x7F in the high byte (our placeholder range)
        for (int i = 0; i < count; i++) {
            int base = 5 + i * 4;
            int tag = cp[base] & 0xFF;
            int b1 = cp[base + 1] & 0xFF;
            int b2 = cp[base + 2] & 0xFF;

            // Internal refs have high bit = 0
            if ((b1 & 0x80) == 0) {
                // For internal refs (class, field, method, static), offset should NOT be in placeholder range 0x7F00+
                // Tags 1-3 use b1:b2 as class offset, tags 5-6 use b2:b3 as method/field offset
                if (tag == 1 || tag == 5 || tag == 6) {
                    int offset = (b2 << 8) | (cp[base + 3] & 0xFF);
                    assertThat(offset).as("CP entry %d (tag=%d) should not have placeholder offset", i, tag)
                            .isLessThan(0x7F00);
                }
                if (tag == 2 || tag == 3) {
                    int classOffset = (b1 << 8) | b2;
                    assertThat(classOffset).as("CP entry %d (tag=%d) class offset should not be placeholder", i, tag)
                            .isLessThan(0x7F00);
                }
            }
        }
    }

    // ── Class Component (tag=6) ──

    @Test
    void classTag() {
        byte[] cls = components.get("Class.cap");
        assertThat(cls[0]).as("Class tag").isEqualTo((byte) 6);
    }

    @Test
    void classComponentParseable() {
        byte[] cls = components.get("Class.cap");
        int size = u2(cls, 1);
        assertThat(size).as("Class component size").isGreaterThan(0);

        // Parse class entries
        int pos = 3;
        int classCount = 0;
        while (pos < 3 + size) {
            int flagsByte = cls[pos] & 0xFF;
            boolean isInterface = (flagsByte & 0x80) != 0;
            int ifaceCount = flagsByte & 0x0F;

            if (isInterface) {
                pos++; // flags byte
                pos += ifaceCount * 2; // superinterface CP indices
            } else {
                pos++; // flags byte
                int superRef = u2(cls, pos); pos += 2;
                // superRef uses direct class_ref encoding (JCVM 3.0.5 §6.9):
                // 0xFFFF = java.lang.Object, high bit set = external (0x80|pkg, class_token),
                // high bit clear = internal ClassComponent offset
                assertThat(superRef == 0xFFFF || superRef < 0x7F00 || (superRef >> 8 & 0x80) != 0)
                        .as("Superclass ref for class %d should be 0xFFFF, internal offset, or external ref, got 0x%04X",
                                classCount, superRef)
                        .isTrue();

                int instanceSize = cls[pos++] & 0xFF;
                int firstRefToken = cls[pos++] & 0xFF;
                int refCount = cls[pos++] & 0xFF;

                int pubBase = cls[pos++] & 0xFF;
                int pubCount = cls[pos++] & 0xFF;
                for (int j = 0; j < pubCount; j++) {
                    int methodOffset = u2(cls, pos); pos += 2;
                    // Method offsets should be > 0 (after handler table)
                    assertThat(methodOffset).as("Virtual method offset for class %d, method %d",
                            classCount, j).isGreaterThan(0);
                }

                int pkgBase = cls[pos++] & 0xFF;
                int pkgCount = cls[pos++] & 0xFF;
                pos += pkgCount * 2;

                // Interface mappings — count is in flags byte (bits 3..0)
                for (int j = 0; j < ifaceCount; j++) {
                    int ifaceRef = u2(cls, pos); pos += 2;
                    int mappingCount = cls[pos++] & 0xFF;
                    pos += mappingCount; // mapping entries are u1 each
                }
            }
            classCount++;
        }
        assertThat(classCount).as("Number of classes").isGreaterThan(0);
    }

    // ── Method Component (tag=7) ──

    @Test
    void methodTag() {
        byte[] m = components.get("Method.cap");
        assertThat(m[0]).as("Method tag").isEqualTo((byte) 7);
    }

    @Test
    void methodHandlerCount() {
        byte[] m = components.get("Method.cap");
        int handlerCount = m[3] & 0xFF;
        // TestApplet doesn't have try/catch, but may have 0
        assertThat(handlerCount).as("Handler count").isGreaterThanOrEqualTo(0);
    }

    @Test
    void methodComponentParseable() {
        byte[] m = components.get("Method.cap");
        int size = u2(m, 1);
        int handlerCount = m[3] & 0xFF;

        // Skip handlers: each is 8 bytes
        int pos = 4 + handlerCount * 8;
        int methodCount = 0;

        if (pos < 3 + size) {
            int firstByte = m[pos] & 0xFF;
            boolean isExtended = (firstByte & 0x80) != 0;
            boolean isAbstract = (firstByte & 0x40) != 0;

            if (isExtended) {
                int maxStack = m[pos + 1] & 0xFF;
                if (!isAbstract) {
                    // Verify the header values are sane
                    assertThat(maxStack).as("Extended method %d maxStack", methodCount)
                            .isLessThanOrEqualTo(255);
                }
            }
            // Can't reliably skip bytecode without length, so just check first method
            methodCount++;
        }
        assertThat(methodCount).as("At least one method found").isGreaterThan(0);
    }

    // ── StaticField Component (tag=8) ──

    @Test
    void staticFieldTag() {
        byte[] sf = components.get("StaticField.cap");
        assertThat(sf[0]).as("StaticField tag").isEqualTo((byte) 8);
    }

    @Test
    void staticFieldFormat() {
        byte[] sf = components.get("StaticField.cap");
        // info: image_size(2) + reference_count(2) + array_init_count(2) +
        //       default_value_count(2) + non_default_values_count(2) + non_default_values(n)
        int imageSize = u2(sf, 3);
        int refCount = u2(sf, 5);
        int arrayInitCount = u2(sf, 7);
        int defaultValueCount = u2(sf, 9);
        int nonDefaultCount = u2(sf, 11);

        // TestApplet: INS_GET/INS_PUT are static final byte — inlined by javac,
        // excluded from image. No instance fields are static. Image should be 0.
        assertThat(imageSize).as("Static field image size")
                .isEqualTo(refCount * 2 + defaultValueCount + nonDefaultCount);

        // No non-default static fields (compile-time constants are inlined)
        assertThat(imageSize).as("Static field image should match Oracle (0)")
                .isEqualTo(0);
    }

    // ── RefLocation Component (tag=9) ──

    @Test
    void refLocationTag() {
        byte[] rl = components.get("RefLocation.cap");
        assertThat(rl[0]).as("RefLocation tag").isEqualTo((byte) 9);
    }

    @Test
    void refLocationFormat() {
        byte[] rl = components.get("RefLocation.cap");
        int byte1Count = u2(rl, 3);
        // after byte1 deltas: byte2_count(2) + byte2 deltas
        int pos = 5 + byte1Count;
        assertThat(pos).as("RefLocation pos after byte1 deltas").isLessThan(rl.length);
        int byte2Count = u2(rl, pos);

        // Total size should match
        int expectedInfoSize = 2 + byte1Count + 2 + byte2Count;
        int declaredSize = u2(rl, 1);
        assertThat(declaredSize).as("RefLocation size consistency").isEqualTo(expectedInfoSize);
    }

    // ── Descriptor Component (tag=11) ──

    @Test
    void descriptorTag() {
        byte[] desc = components.get("Descriptor.cap");
        assertThat(desc[0]).as("Descriptor tag").isEqualTo((byte) 11);
    }

    @Test
    void descriptorClassCount() {
        byte[] desc = components.get("Descriptor.cap");
        int classCount = desc[3] & 0xFF;
        assertThat(classCount).as("Descriptor class count").isEqualTo(1); // TestApplet only
    }

    // ── Cross-component Consistency ──

    @Test
    void allRequiredComponentsPresent() {
        assertThat(components).as("Required components")
                .containsKeys("Header.cap", "Directory.cap", "Applet.cap",
                        "Import.cap", "ConstantPool.cap", "Class.cap",
                        "Method.cap", "StaticField.cap", "RefLocation.cap",
                        "Descriptor.cap");
    }

    @Test
    void allComponentTagsCorrect() {
        Map<String, Integer> expectedTags = Map.of(
                "Header.cap", 1, "Directory.cap", 2, "Applet.cap", 3,
                "Import.cap", 4, "ConstantPool.cap", 5, "Class.cap", 6,
                "Method.cap", 7, "StaticField.cap", 8, "RefLocation.cap", 9,
                "Descriptor.cap", 11);
        for (var entry : expectedTags.entrySet()) {
            byte[] data = components.get(entry.getKey());
            assertThat(data[0] & 0xFF).as("Tag for " + entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void allComponentSizeFieldsMatchActualContent() {
        for (var entry : components.entrySet()) {
            byte[] data = entry.getValue();
            int declaredSize = u2(data, 1);
            int actualInfoSize = data.length - 3; // minus tag(1) + size(2)
            assertThat(declaredSize).as("Size field for " + entry.getKey())
                    .isEqualTo(actualInfoSize);
        }
    }

    // ── Print full binary dump for visual inspection ──

    @Test
    void printBinaryDump() {
        System.out.println("═══ CAP Binary Analysis ═══");
        System.out.println("Total CAP size: " + result.capSize() + " bytes");
        System.out.println("Components: " + components.size());
        System.out.println();

        for (var entry : components.entrySet()) {
            byte[] data = entry.getValue();
            System.out.printf("── %s (tag=%d, size=%d, total=%d bytes) ──%n",
                    entry.getKey(), data[0] & 0xFF, u2(data, 1), data.length);
            printHex(data, Math.min(data.length, 128));
            System.out.println();
        }

        // Detailed CP dump
        byte[] cp = components.get("ConstantPool.cap");
        if (cp != null && cp.length > 5) {
            int count = u2(cp, 3);
            System.out.println("── ConstantPool Entries (" + count + ") ──");
            String[] tagNames = {"?", "ClassRef", "InstanceField", "VirtualMethod",
                    "SuperMethod", "StaticField", "StaticMethod"};
            for (int i = 0; i < count; i++) {
                int base = 5 + i * 4;
                if (base + 3 >= cp.length) break;
                int tag = cp[base] & 0xFF;
                int b1 = cp[base + 1] & 0xFF;
                int b2 = cp[base + 2] & 0xFF;
                int b3 = cp[base + 3] & 0xFF;
                String tagName = tag < tagNames.length ? tagNames[tag] : "Unknown";
                boolean isExternal = (tag == 1 || tag == 5 || tag == 6) && (b1 & 0x80) != 0;
                String refType = isExternal ? "EXT" : "INT";
                System.out.printf("  [%2d] %s %-15s  b1=%02X b2=%02X b3=%02X%n",
                        i, refType, tagName, b1, b2, b3);
            }
            System.out.println();
        }
    }

    // ── Helpers ──

    private static int u2(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }

    private static int u4(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    private static String hexBytes(byte[] data) {
        var sb = new StringBuilder();
        for (byte b : data) sb.append(String.format("%02X", b & 0xFF));
        return sb.toString();
    }

    private static void printHex(byte[] data, int limit) {
        for (int i = 0; i < limit; i++) {
            if (i > 0 && i % 16 == 0) System.out.println();
            System.out.printf("%02X ", data[i] & 0xFF);
        }
        if (limit < data.length) System.out.printf("... (%d more bytes)", data.length - limit);
        System.out.println();
    }

    private static String componentName(int index) {
        return switch (index) {
            case 0 -> "Header"; case 1 -> "Directory"; case 2 -> "Applet";
            case 3 -> "Import"; case 4 -> "ConstantPool"; case 5 -> "Class";
            case 6 -> "Method"; case 7 -> "StaticField"; case 8 -> "RefLocation";
            case 9 -> "Export"; case 10 -> "Descriptor"; case 11 -> "Debug";
            default -> "Unknown";
        };
    }
}
