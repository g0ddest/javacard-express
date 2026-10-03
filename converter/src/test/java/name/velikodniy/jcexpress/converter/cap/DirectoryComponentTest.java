package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Directory component layout (JCVM 3.1 §6.5): component_size_info_compact has a u2 size for
 * each of the components Header..Descriptor, a u2 Debug_Component_Size since CAP format 2.2
 * and a u4 Static_Resource_Component_Size since CAP format 2.3, followed by
 * static_field_size_info, import_count, applet_count and custom_count.
 */
class DirectoryComponentTest {

    /** Sizes indexed by tag - 1: Header 1 .. Descriptor 11, Debug 12, Static Resources 13. */
    private static int[] sizes(int debugSize, int staticResourceSize) {
        return new int[]{0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88, 0x99, 0xAA, 0xBB,
                debugSize, staticResourceSize};
    }

    private static String hex(byte[] b) {
        return HexFormat.of().withUpperCase().formatHex(b);
    }

    @Test
    void capFormat2_1HasElevenComponentSizes_jcvm31_6_5() {
        byte[] dir = DirectoryComponent.generate(sizes(0, 0), 0x0102, 3, 0x0405, 4, 1, JavaCardVersion.V3_0_5);

        assertThat(hex(dir)).isEqualTo("02001F"
                + "00110022003300440055006600770088009900AA00BB"
                + "0102" + "0003" + "0405" + "04" + "01" + "00");
    }

    @Test
    void capFormat2_3AddsU2DebugAndU4StaticResourceSizes_jcvm31_6_5() {
        byte[] dir = DirectoryComponent.generate(sizes(0x0C0D, 0x00012345), 0, 0, 0, 2, 0,
                JavaCardVersion.V3_1_0);

        assertThat(hex(dir)).isEqualTo("020025"
                + "00110022003300440055006600770088009900AA00BB"
                + "0C0D" + "00012345"
                + "0000" + "0000" + "0000" + "02" + "00" + "00");
    }
}
