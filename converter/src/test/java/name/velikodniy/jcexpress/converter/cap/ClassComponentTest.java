package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for ClassComponent — verifies dispatch tables, interface mappings,
 * and inheritance handling in the generated Class component.
 */
class ClassComponentTest {

    private static final Path CLASSES_DIR = Path.of("target/test-classes");

    // ── Inherited virtual method dispatch tables ──

    @Test
    void inheritanceAppletShouldHaveThreeClassEntries() throws Exception {
        byte[] classData = convertAndExtractClass("com.example.inherit", "A000000062060101",
                "com.example.inherit.InheritanceApplet", "A00000006206010101");

        // Parse Class component: skip tag(1) + size(2), then parse class_info entries
        // 3 classes: BaseApplet, MiddleApplet, InheritanceApplet (sorted by token)
        int pos = 3; // skip tag + u2 size
        int classCount = 0;
        while (pos < classData.length) {
            int flags = classData[pos] & 0xFF;
            boolean isInterface = (flags & 0x80) != 0;
            if (isInterface) {
                // Interface: flags(1) + superinterfaces(interface_count * 2)
                int ifaceCount = flags & 0x0F;
                pos += 1 + ifaceCount * 2;
            } else {
                // Class: parse and skip all fields
                pos = skipClassInfo(classData, pos);
            }
            classCount++;
        }
        assertThat(classCount).as("Should have 3 class_info entries (Base, Middle, Inheritance)")
                .isEqualTo(3);
    }

    @Test
    void inheritanceAppletShouldHaveCorrectDispatchTable() throws Exception {
        byte[] classData = convertAndExtractClass("com.example.inherit", "A000000062060101",
                "com.example.inherit.InheritanceApplet", "A00000006206010101");

        // Parse the last class (InheritanceApplet) — it should have the most virtual methods
        int pos = 3; // skip tag + u2 size
        int[] publicMethodTableBase = new int[3];
        int[] publicMethodTableCount = new int[3];

        for (int i = 0; i < 3; i++) {
            int flags = classData[pos] & 0xFF;
            pos++; // flags

            pos += 2; // super_class_ref
            pos++;    // declared_instance_size
            pos++;    // first_reference_token
            pos++;    // reference_count

            publicMethodTableBase[i] = classData[pos] & 0xFF;
            pos++; // public_method_table_base
            publicMethodTableCount[i] = classData[pos] & 0xFF;
            pos++; // public_method_table_count
            pos += publicMethodTableCount[i] * 2; // skip method offsets

            int pkgBase = classData[pos] & 0xFF;
            pos++; // package_method_table_base
            int pkgCount = classData[pos] & 0xFF;
            pos++; // package_method_table_count
            pos += pkgCount * 2; // skip package method offsets

            // Skip implemented interfaces
            int interfaceCount = flags & 0x0F;
            for (int j = 0; j < interfaceCount; j++) {
                pos += 2; // interface_ref
                int mappingCount = classData[pos] & 0xFF;
                pos++; // mapping count
                pos += mappingCount; // mapping indices
            }
        }

        // InheritanceApplet (class 2) should have a dispatch table covering process
        assertThat(publicMethodTableCount[2]).as("InheritanceApplet should have virtual methods")
                .isGreaterThan(0);
    }

    // ── Interface-to-method mappings ──

    @Test
    void interfaceAppletShouldHaveInterfaceCount() throws Exception {
        byte[] classData = convertAndExtractClass("com.example.iface", "A000000062040101",
                "com.example.iface.InterfaceApplet", "A00000006204010101");

        // InterfaceApplet implements Shareable → interface_count should be 1
        int pos = 3; // skip tag + u2 size
        int flags = classData[pos] & 0xFF;
        int interfaceCount = flags & 0x0F;

        assertThat(interfaceCount)
                .as("InterfaceApplet implements 1 interface (Shareable)")
                .isEqualTo(1);
    }

    @Test
    void interfaceAppletShouldHaveShareableMappingEntry() throws Exception {
        byte[] classData = convertAndExtractClass("com.example.iface", "A000000062040101",
                "com.example.iface.InterfaceApplet", "A00000006204010101");

        // Skip to implemented_interface_info_table
        int pos = 3;
        int flags = classData[pos] & 0xFF;
        pos++; // flags
        pos += 2; // super_class_ref
        pos++;    // declared_instance_size
        pos++;    // first_reference_token
        pos++;    // reference_count

        int pubBase = classData[pos] & 0xFF;
        pos++; // public_method_table_base
        int pubCount = classData[pos] & 0xFF;
        pos++; // public_method_table_count
        pos += pubCount * 2; // skip method offsets

        pos++; // package_method_table_base
        int pkgCount = classData[pos] & 0xFF;
        pos++; // package_method_table_count
        pos += pkgCount * 2; // skip

        // Now at implemented_interface_info_table
        int interfaceCount = flags & 0x0F;
        assertThat(interfaceCount).isEqualTo(1);

        // Read interface ref
        int ifaceRef = ((classData[pos] & 0xFF) << 8) | (classData[pos + 1] & 0xFF);
        pos += 2;

        // Shareable is external: (0x80|pkg_token) << 8 | class_token=2
        // pkg_token for javacard.framework = 0
        assertThat(ifaceRef).as("Shareable interface ref")
                .isEqualTo(0x8002);

        // Shareable has 0 methods → mapping count = 0
        int mappingCount = classData[pos] & 0xFF;
        assertThat(mappingCount).as("Shareable has no methods")
                .isEqualTo(0);
    }

    @Test
    void testAppletClassComponentShouldHaveZeroInterfaces() throws Exception {
        byte[] classData = convertAndExtractClass("com.example", "A000000062010101",
                "com.example.TestApplet", "A00000006201010101");

        int pos = 3; // skip tag + u2 size
        int flags = classData[pos] & 0xFF;
        int interfaceCount = flags & 0x0F;

        assertThat(interfaceCount)
                .as("TestApplet implements no interfaces directly")
                .isEqualTo(0);
    }

    @Test
    void multiClassAppletShouldHaveTwoClassEntries() throws Exception {
        byte[] classData = convertAndExtractClass("com.example.multiclass", "A000000062030101",
                "com.example.multiclass.MultiClassApplet", "A00000006203010101");

        int pos = 3;
        int classCount = 0;
        while (pos < classData.length) {
            int flags = classData[pos] & 0xFF;
            boolean isInterface = (flags & 0x80) != 0;
            if (isInterface) {
                int ifaceCount = flags & 0x0F;
                pos += 1 + ifaceCount * 2;
            } else {
                pos = skipClassInfo(classData, pos);
            }
            classCount++;
        }

        assertThat(classCount)
                .as("Should have 2 class_info entries (Helper, MultiClassApplet)")
                .isEqualTo(2);
    }

    // ── Abstract class tests ──

    @Test
    void abstractClassAppletShouldHaveTwoClassEntries() throws Exception {
        byte[] classData = convertAndExtractClass("com.example.abstract_", "A000000062100101",
                "com.example.abstract_.ConcreteApplet", "A00000006210010101");

        int pos = 3; // skip tag + u2 size
        int classCount = 0;
        while (pos < classData.length) {
            int flags = classData[pos] & 0xFF;
            boolean isInterface = (flags & 0x80) != 0;
            if (isInterface) {
                int ifaceCount = flags & 0x0F;
                pos += 1 + ifaceCount * 2;
            } else {
                pos = skipClassInfo(classData, pos);
            }
            classCount++;
        }
        assertThat(classCount).as("Should have 2 class_info entries (AbstractBase, ConcreteApplet)")
                .isEqualTo(2);
    }

    @Test
    void abstractClassShouldHaveVirtualMethods() throws Exception {
        byte[] classData = convertAndExtractClass("com.example.abstract_", "A000000062100101",
                "com.example.abstract_.ConcreteApplet", "A00000006210010101");

        // Parse the first class (AbstractBase) — check it has virtual method entries
        int pos = 3; // skip tag + u2 size
        int flags = classData[pos] & 0xFF;
        pos++; // flags

        pos += 2; // super_class_ref
        pos++;    // declared_instance_size
        pos++;    // first_reference_token
        pos++;    // reference_count

        pos++; // public_method_table_base
        int pubCount = classData[pos] & 0xFF;
        pos++; // public_method_table_count

        // AbstractBase has handleCommand (abstract virtual) + process (virtual)
        // so dispatch table should have entries
        assertThat(pubCount).as("AbstractBase should have virtual methods in dispatch table")
                .isGreaterThan(0);
    }

    // ── Array ops applet test ──

    @Test
    void arrayOpsAppletShouldConvertClassComponent() throws Exception {
        byte[] classData = convertAndExtractClass("com.example.arrayops", "A000000062080101",
                "com.example.arrayops.ArrayOpsApplet", "A00000006208010101");

        int pos = 3;
        int flags = classData[pos] & 0xFF;
        int interfaceCount = flags & 0x0F;

        assertThat(interfaceCount)
                .as("ArrayOpsApplet implements no interfaces directly")
                .isEqualTo(0);
    }

    // ── Multi-exception applet test ──

    @Test
    void multiExceptionAppletShouldConvertClassComponent() throws Exception {
        byte[] classData = convertAndExtractClass("com.example.multiexc", "A000000062090101",
                "com.example.multiexc.MultiExceptionApplet", "A00000006209010101");

        assertThat(classData).isNotNull();
        assertThat(classData.length).isGreaterThan(3);
    }

    // ── Helper methods ──

    private byte[] convertAndExtractClass(String packageName, String packageAid,
                                           String appletClass, String appletAid) throws Exception {
        ConverterResult result = Converter.builder()
                .classesDirectory(CLASSES_DIR)
                .packageName(packageName)
                .packageAid(packageAid)
                .packageVersion(1, 0)
                .applet(appletClass, appletAid)
                .build()
                .convert();

        byte[] classData = extractComponent(result.capFile(), "Class.cap");
        assertThat(classData).as("Class.cap should exist").isNotNull();
        return classData;
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

    /**
     * Skips a class_info entry in the Class component binary data.
     * Returns the position after the entry.
     */
    private static int skipClassInfo(byte[] data, int startPos) {
        int pos = startPos;
        int flags = data[pos] & 0xFF;
        int interfaceCount = flags & 0x0F;
        pos++; // flags

        pos += 2; // super_class_ref
        pos++;    // declared_instance_size
        pos++;    // first_reference_token
        pos++;    // reference_count

        pos++; // public_method_table_base
        int pubCount = data[pos] & 0xFF;
        pos++; // public_method_table_count
        pos += pubCount * 2; // method offsets

        pos++; // package_method_table_base
        int pkgCount = data[pos] & 0xFF;
        pos++; // package_method_table_count
        pos += pkgCount * 2;

        // implemented_interface_info_table
        for (int i = 0; i < interfaceCount; i++) {
            pos += 2; // interface_ref
            int mappingCount = data[pos] & 0xFF;
            pos++; // mapping count
            pos += mappingCount; // indices
        }

        return pos;
    }
}
