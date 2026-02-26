package name.velikodniy.jcexpress.tlv;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TLVBuilderTest {

    @Test
    void shouldBuildPrimitive() {
        byte[] data = TLVBuilder.create()
                .add(0x84, new byte[]{(byte) 0xA0, 0x00, 0x00})
                .build();
        // 84 03 A00000
        assertThat(Hex.encode(data)).isEqualTo("8403A00000");
    }

    @Test
    void shouldBuildFromHex() {
        byte[] data = TLVBuilder.create()
                .add(Tags.DF_NAME, "A0000000031010")
                .build();
        TLVList parsed = TLVParser.parse(data);
        assertThat(parsed.size()).isEqualTo(1);
        assertThat(parsed.get(0).tag()).isEqualTo(0x84);
        assertThat(parsed.get(0).valueHex()).isEqualTo("A0000000031010");
    }

    @Test
    void shouldBuildConstructed() {
        byte[] data = TLVBuilder.create()
                .addConstructed(Tags.FCI_TEMPLATE, b -> b
                        .add(Tags.DF_NAME, "A0000000031010")
                )
                .build();
        TLVList parsed = TLVParser.parse(data);
        assertThat(parsed.size()).isEqualTo(1);
        TLV fci = parsed.get(0);
        assertThat(fci.tag()).isEqualTo(0x6F);
        assertThat(fci.isConstructed()).isTrue();
        assertThat(fci.children().get(0).tag()).isEqualTo(0x84);
    }

    @Test
    void shouldBuildNestedConstructed() {
        byte[] data = TLVBuilder.create()
                .addConstructed(Tags.FCI_TEMPLATE, fci -> fci
                        .add(Tags.DF_NAME, "A0000000031010")
                        .addConstructed(Tags.FCI_PROPRIETARY, prop -> prop
                                .add(Tags.SFI, new byte[]{0x01})
                        )
                )
                .build();
        TLVList parsed = TLVParser.parse(data);
        TLV sfi = parsed.findRecursive(Tags.SFI).orElseThrow();
        assertThat(sfi.valueHex()).isEqualTo("01");
    }

    @Test
    void shouldBuildMultiByteTag() {
        byte[] data = TLVBuilder.create()
                .add(0x9F38, new byte[]{0x01, 0x02, 0x03})
                .build();
        TLVList parsed = TLVParser.parse(data);
        assertThat(parsed.get(0).tag()).isEqualTo(0x9F38);
    }

    @Test
    void roundTripShouldPreserveData() {
        // Parse real data, then rebuild and parse again
        String hex = "6F 0E 84 07 A0000000031010 A5 03 88 01 01";
        TLVList original = TLVParser.parse(hex);

        TLV fci = original.get(0);
        TLV dfName = fci.find(Tags.DF_NAME).orElseThrow();
        TLV prop = fci.find(Tags.FCI_PROPRIETARY).orElseThrow();
        TLV sfi = prop.find(Tags.SFI).orElseThrow();

        byte[] rebuilt = TLVBuilder.create()
                .addConstructed(Tags.FCI_TEMPLATE, b -> b
                        .add(Tags.DF_NAME, dfName.value())
                        .addConstructed(Tags.FCI_PROPRIETARY, p -> p
                                .add(Tags.SFI, sfi.value())
                        )
                )
                .build();

        TLVList reparsed = TLVParser.parse(rebuilt);
        assertThat(reparsed.findRecursive(Tags.DF_NAME).orElseThrow().valueHex())
                .isEqualTo("A0000000031010");
        assertThat(reparsed.findRecursive(Tags.SFI).orElseThrow().valueHex())
                .isEqualTo("01");
    }

    @Test
    void shouldBuildEmptyValue() {
        byte[] data = TLVBuilder.create()
                .add(0x84, new byte[0])
                .build();
        assertThat(Hex.encode(data)).isEqualTo("8400");
    }
}
