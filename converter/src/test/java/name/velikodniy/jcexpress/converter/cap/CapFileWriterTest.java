package name.velikodniy.jcexpress.converter.cap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CAP file container (JCVM 3.1 §4.1.3, §6.2.1): one JAR entry per component, named after
 * Table 6-2, in the {@code <package>/javacard/} directory. The archive must be reproducible:
 * the same components give the same bytes, whenever and wherever they are written.
 */
class CapFileWriterTest {

    private static Map<Integer, byte[]> components() {
        Map<Integer, byte[]> components = new LinkedHashMap<>();
        for (int tag : new int[]{11, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10}) {
            components.put(tag, new byte[]{(byte) tag, 0, 1, (byte) (0x40 + tag)});
        }
        return components;
    }

    private static List<ZipEntry> entries(byte[] cap) throws IOException {
        List<ZipEntry> entries = new ArrayList<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(cap))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                entries.add(e);
            }
        }
        return entries;
    }

    @Test
    void componentsAreStoredUnderTheirTable6_2NamesInTagOrder_jcvm31_6_2_1() throws IOException {
        byte[] cap = CapFileWriter.write("com/example", components());

        assertThat(entries(cap)).extracting(ZipEntry::getName).containsExactly(
                "META-INF/MANIFEST.MF", "com/example/javacard/",
                "com/example/javacard/Header.cap", "com/example/javacard/Directory.cap",
                "com/example/javacard/Applet.cap", "com/example/javacard/Import.cap",
                "com/example/javacard/ConstantPool.cap", "com/example/javacard/Class.cap",
                "com/example/javacard/Method.cap", "com/example/javacard/StaticField.cap",
                "com/example/javacard/RefLocation.cap", "com/example/javacard/Export.cap",
                "com/example/javacard/Descriptor.cap");
    }

    @Test
    void componentsOfTheUnnamedPackageHaveNoDirectoryAndAreRejected_jcvm31_4_1_3() {
        // "" would give entries "/javacard/Header.cap": a leading '/' is not a valid JAR entry name
        assertThatThrownBy(() -> CapFileWriter.write("", components()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<package>/javacard/");
        assertThatThrownBy(() -> CapFileWriter.write("/com/example", components()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyEntryHasTheFixedTimestamp() throws IOException {
        byte[] cap = CapFileWriter.write("com/example", components());

        assertThat(entries(cap)).extracting(ZipEntry::getTimeLocal)
                .containsOnly(LocalDateTime.of(1980, 2, 1, 0, 0));
    }

    @Test
    void outputDoesNotDependOnTheTimeZoneOrTheClock() throws IOException {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            byte[] first = CapFileWriter.write("com/example", components());
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            byte[] second = CapFileWriter.write("com/example", components());

            assertThat(second).isEqualTo(first);
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
