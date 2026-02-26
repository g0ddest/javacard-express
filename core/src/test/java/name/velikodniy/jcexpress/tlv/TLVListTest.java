package name.velikodniy.jcexpress.tlv;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TLVListTest {

    // 6F (FCI) containing 84 (DF Name) + A5 (FCI Proprietary containing 88 SFI)
    // 6F 0E = 14 bytes: 84(9) + A5(5)
    private static final String FCI_HEX =
            "6F 0E 84 07 A0000000031010 A5 03 88 01 01";

    @Test
    void findShouldReturnDirectChild() {
        TLVList list = TLVParser.parse(FCI_HEX);
        TLV fci = list.find(Tags.FCI_TEMPLATE).orElseThrow();
        assertThat(fci.tag()).isEqualTo(0x6F);
    }

    @Test
    void findShouldReturnEmptyWhenNotFound() {
        TLVList list = TLVParser.parse(FCI_HEX);
        assertThat(list.find(0x99)).isEmpty();
    }

    @Test
    void containsShouldReturnTrue() {
        TLVList list = TLVParser.parse(FCI_HEX);
        assertThat(list.contains(Tags.FCI_TEMPLATE)).isTrue();
    }

    @Test
    void containsShouldReturnFalse() {
        TLVList list = TLVParser.parse(FCI_HEX);
        assertThat(list.contains(0x99)).isFalse();
    }

    @Test
    void findRecursiveShouldFindNestedTag() {
        TLVList list = TLVParser.parse(FCI_HEX);
        // SFI (0x88) is nested: 6F → A5 → 88
        TLV sfi = list.findRecursive(Tags.SFI).orElseThrow();
        assertThat(sfi.tag()).isEqualTo(0x88);
        assertThat(sfi.valueHex()).isEqualTo("01");
    }

    @Test
    void findRecursiveShouldFindDfNameInsideFci() {
        TLVList list = TLVParser.parse(FCI_HEX);
        TLV dfName = list.findRecursive(Tags.DF_NAME).orElseThrow();
        assertThat(dfName.valueHex()).isEqualTo("A0000000031010");
    }

    @Test
    void findRecursiveShouldReturnEmptyWhenNotFound() {
        TLVList list = TLVParser.parse(FCI_HEX);
        assertThat(list.findRecursive(0x99)).isEmpty();
    }

    @Test
    void streamShouldWork() {
        TLVList list = TLVParser.parse("84 02 AABB 50 02 CCDD");
        List<Integer> tags = list.stream().map(TLV::tag).collect(Collectors.toList());
        assertThat(tags).containsExactly(0x84, 0x50);
    }

    @Test
    void iteratorShouldWork() {
        TLVList list = TLVParser.parse("84 02 AABB 50 02 CCDD");
        int count = 0;
        for (TLV ignored : list) {
            count++;
        }
        assertThat(count).isEqualTo(2);
    }

    @Test
    void emptyShouldHaveSizeZero() {
        TLVList list = TLVParser.parse(new byte[0]);
        assertThat(list.size()).isZero();
        assertThat(list.isEmpty()).isTrue();
    }
}
