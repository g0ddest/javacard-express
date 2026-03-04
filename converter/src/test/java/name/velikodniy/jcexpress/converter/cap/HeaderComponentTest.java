package name.velikodniy.jcexpress.converter.cap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderComponentTest {

    @Test
    void shouldStartWithTagAndContainMagic() {
        byte[] aid = {(byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01};
        byte[] header = HeaderComponent.generate(aid, 1, 0, HeaderComponent.ACC_APPLET, "com/example");

        // tag = 1
        assertThat(header[0]).isEqualTo((byte) 1);

        // magic at offset 3 = 0xDECAFFED
        int magic = ((header[3] & 0xFF) << 24) | ((header[4] & 0xFF) << 16)
                | ((header[5] & 0xFF) << 8) | (header[6] & 0xFF);
        assertThat(magic).isEqualTo(0xDECAFFED);
    }

    @Test
    void shouldContainPackageAid() {
        byte[] aid = {(byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x01};
        byte[] header = HeaderComponent.generate(aid, 1, 0, 0, "com/example");

        // Search for AID in output (should appear after version fields)
        boolean found = false;
        for (int i = 0; i < header.length - aid.length; i++) {
            boolean match = true;
            for (int j = 0; j < aid.length; j++) {
                if (header[i + j] != aid[j]) { match = false; break; }
            }
            if (match) { found = true; break; }
        }
        assertThat(found).isTrue();
    }

    @Test
    void shouldContainPackageName() {
        byte[] aid = {(byte) 0xA0, 0x00, 0x00, 0x00};
        String pkgName = "com/example";
        byte[] header = HeaderComponent.generate(aid, 1, 0, 0, pkgName);

        // Package name should be in the output
        String headerStr = new String(header);
        assertThat(headerStr).contains(pkgName);
    }
}
