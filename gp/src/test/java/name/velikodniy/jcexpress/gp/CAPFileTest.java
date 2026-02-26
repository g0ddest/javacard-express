package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CAPFile} — CAP file parsing and load data generation.
 */
class CAPFileTest {

    private static final String PKG_DIR = "com/example/applet/javacard/";
    private static final byte[] SAMPLE_AID = Hex.decode("A0000000031010");

    /**
     * Creates a synthetic Header.cap component.
     *
     * Header layout (simplified):
     * [0..9]  = arbitrary header data
     * [10]    = minor version
     * [11]    = major version
     * [12]    = AID length
     * [13..]  = AID bytes
     */
    private static byte[] createHeader(byte[] aid, int major, int minor) {
        byte[] header = new byte[13 + aid.length];
        header[0] = 0x01; // tag
        header[1] = (byte) (header.length - 2); // component length
        header[2] = 0x00;
        header[3] = 0x02;
        header[4] = 0x00;
        header[5] = 0x01;
        header[10] = (byte) minor;
        header[11] = (byte) major;
        header[12] = (byte) aid.length;
        System.arraycopy(aid, 0, header, 13, aid.length);
        return header;
    }

    /** Creates a synthetic component with given tag and content. */
    private static byte[] createComponent(int tag, byte[] content) {
        byte[] comp = new byte[3 + content.length];
        comp[0] = (byte) tag;
        comp[1] = (byte) ((content.length >> 8) & 0xFF);
        comp[2] = (byte) (content.length & 0xFF);
        System.arraycopy(content, 0, comp, 3, content.length);
        return comp;
    }

    /** Builds a synthetic CAP file (ZIP) with the given components in order. */
    private static byte[] buildCapZip(String pkgDir, String[] names, byte[][] data)
            throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < names.length; i++) {
                zos.putNextEntry(new ZipEntry(pkgDir + names[i]));
                zos.write(data[i]);
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    /** Builds a minimal valid CAP file with Header, Directory, Class, and Method. */
    private static byte[] buildMinimalCap() throws IOException {
        byte[] header = createHeader(SAMPLE_AID, 1, 0);
        byte[] directory = createComponent(0x02, new byte[]{0x10, 0x20});
        byte[] classComp = createComponent(0x06, new byte[]{0x01, 0x02, 0x03});
        byte[] method = createComponent(0x07, new byte[]{0x04, 0x05, 0x06, 0x07});

        return buildCapZip(PKG_DIR,
                new String[]{"Header.cap", "Directory.cap", "Class.cap", "Method.cap"},
                new byte[][]{header, directory, classComp, method});
    }

    @Test
    void shouldParseCapFromZip() throws IOException {
        byte[] zip = buildMinimalCap();
        CAPFile cap = CAPFile.from(zip);

        assertThat(cap).isNotNull();
        assertThat(cap.packageAid()).isEqualTo(SAMPLE_AID);
    }

    @Test
    void shouldExtractPackageAid() throws IOException {
        byte[] zip = buildMinimalCap();
        CAPFile cap = CAPFile.from(zip);

        assertThat(cap.packageAidHex()).isEqualTo("A0000000031010");
        assertThat(cap.packageAid()).isEqualTo(SAMPLE_AID);
    }

    @Test
    void shouldExtractVersions() throws IOException {
        byte[] zip = buildMinimalCap();
        CAPFile cap = CAPFile.from(zip);

        assertThat(cap.majorVersion()).isEqualTo(1);
        assertThat(cap.minorVersion()).isEqualTo(0);
    }

    @Test
    void shouldListComponents() throws IOException {
        byte[] zip = buildMinimalCap();
        CAPFile cap = CAPFile.from(zip);

        List<String> names = cap.componentNames();
        assertThat(names).containsExactly("Header", "Directory", "Class", "Method");
    }

    @Test
    void shouldConcatenateComponentsInOrder() throws IOException {
        byte[] header = createHeader(SAMPLE_AID, 1, 0);
        byte[] classComp = new byte[]{0x01, 0x02};
        byte[] method = new byte[]{0x03, 0x04};

        // Add Method BEFORE Class in ZIP order — code() should still follow spec order
        byte[] zip = buildCapZip(PKG_DIR,
                new String[]{"Header.cap", "Method.cap", "Class.cap"},
                new byte[][]{header, method, classComp});

        CAPFile cap = CAPFile.from(zip);
        byte[] code = cap.code();

        // code() should follow spec order: Header, Class, Method (not ZIP order)
        assertThat(code.length).isEqualTo(header.length + classComp.length + method.length);

        // Verify Class appears before Method in output
        byte[] expectedEnd = new byte[classComp.length + method.length];
        System.arraycopy(classComp, 0, expectedEnd, 0, classComp.length);
        System.arraycopy(method, 0, expectedEnd, classComp.length, method.length);

        byte[] actualEnd = new byte[expectedEnd.length];
        System.arraycopy(code, header.length, actualEnd, 0, expectedEnd.length);
        assertThat(actualEnd).isEqualTo(expectedEnd);
    }

    @Test
    void shouldSkipDebugComponent() throws IOException {
        byte[] header = createHeader(SAMPLE_AID, 1, 0);
        byte[] classComp = new byte[]{0x01};
        byte[] debug = new byte[]{(byte) 0xDE, (byte) 0xBF};

        byte[] zip = buildCapZip(PKG_DIR,
                new String[]{"Header.cap", "Class.cap", "Debug.cap"},
                new byte[][]{header, classComp, debug});

        CAPFile cap = CAPFile.from(zip);

        assertThat(cap.componentNames()).doesNotContain("Debug");

        byte[] code = cap.code();
        assertThat(code.length).isEqualTo(header.length + classComp.length);
    }

    @Test
    void shouldWrapWithC4Tag() throws IOException {
        byte[] zip = buildMinimalCap();
        CAPFile cap = CAPFile.from(zip);

        byte[] loadData = cap.loadFileData();
        byte[] code = cap.code();

        // Should start with C4 tag
        assertThat(loadData[0] & 0xFF).isEqualTo(0xC4);

        // For short data (< 128 bytes), BER length is single byte
        if (code.length <= 0x7F) {
            assertThat(loadData[1] & 0xFF).isEqualTo(code.length);
            assertThat(loadData.length).isEqualTo(2 + code.length);
        }

        // Verify the code portion matches
        byte[] extractedCode = new byte[code.length];
        System.arraycopy(loadData, loadData.length - code.length, extractedCode, 0, code.length);
        assertThat(extractedCode).isEqualTo(code);
    }

    @Test
    void shouldWrapLargeDataWithBerLength() throws IOException {
        byte[] header = createHeader(SAMPLE_AID, 1, 0);
        byte[] largeClass = new byte[200];
        Arrays.fill(largeClass, (byte) 0xAA);

        byte[] zip = buildCapZip(PKG_DIR,
                new String[]{"Header.cap", "Class.cap"},
                new byte[][]{header, largeClass});

        CAPFile cap = CAPFile.from(zip);
        byte[] loadData = cap.loadFileData();
        int codeLen = cap.code().length;

        assertThat(loadData[0] & 0xFF).isEqualTo(0xC4);

        // BER length: 0x81 for 128-255
        assertThat(loadData[1] & 0xFF).isEqualTo(0x81);
        assertThat(loadData[2] & 0xFF).isEqualTo(codeLen);

        // Total: tag(1) + length(2) + code
        assertThat(loadData.length).isEqualTo(3 + codeLen);
    }

    @Test
    void shouldGenerateCorrectBlocks() throws IOException {
        byte[] header = createHeader(SAMPLE_AID, 1, 0);
        byte[] largeClass = new byte[500];
        Arrays.fill(largeClass, (byte) 0xBB);

        byte[] zip = buildCapZip(PKG_DIR,
                new String[]{"Header.cap", "Class.cap"},
                new byte[][]{header, largeClass});

        CAPFile cap = CAPFile.from(zip);
        List<byte[]> blocks = cap.loadBlocks(247);

        assertThat(blocks).hasSizeGreaterThan(1);

        // All blocks except last should be maxBlockSize
        for (int i = 0; i < blocks.size() - 1; i++) {
            assertThat(blocks.get(i).length).isEqualTo(247);
        }
        // Last block should be <= maxBlockSize
        assertThat(blocks.get(blocks.size() - 1).length).isLessThanOrEqualTo(247);

        // Concatenated blocks should equal loadFileData
        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        for (byte[] block : blocks) {
            combined.writeBytes(block);
        }
        assertThat(combined.toByteArray()).isEqualTo(cap.loadFileData());
    }

    @Test
    void shouldHandleMissingOptionalComponents() throws IOException {
        byte[] header = createHeader(SAMPLE_AID, 2, 1);

        byte[] zip = buildCapZip(PKG_DIR,
                new String[]{"Header.cap"},
                new byte[][]{header});

        CAPFile cap = CAPFile.from(zip);

        assertThat(cap.componentNames()).containsExactly("Header");
        assertThat(cap.code()).isEqualTo(header);
        assertThat(cap.majorVersion()).isEqualTo(2);
        assertThat(cap.minorVersion()).isEqualTo(1);
    }

    @Test
    void shouldRejectInvalidZip() {
        assertThatThrownBy(() -> CAPFile.from(new byte[]{0x01, 0x02, 0x03}))
                .isInstanceOf(GPException.class);
    }

    @Test
    void shouldRejectNullOrEmpty() {
        assertThatThrownBy(() -> CAPFile.from(null))
                .isInstanceOf(GPException.class);
        assertThatThrownBy(() -> CAPFile.from(new byte[0]))
                .isInstanceOf(GPException.class);
    }

    @Test
    void shouldRejectMissingHeader() throws IOException {
        byte[] zip = buildCapZip(PKG_DIR,
                new String[]{"Class.cap"},
                new byte[][]{new byte[]{0x01, 0x02}});

        assertThatThrownBy(() -> CAPFile.from(zip))
                .isInstanceOf(GPException.class)
                .hasMessageContaining("Header");
    }

    @Test
    void toStringShouldContainAidAndVersion() throws IOException {
        byte[] zip = buildMinimalCap();
        CAPFile cap = CAPFile.from(zip);

        assertThat(cap.toString()).contains("A0000000031010");
        assertThat(cap.toString()).contains("1.0");
    }
}
