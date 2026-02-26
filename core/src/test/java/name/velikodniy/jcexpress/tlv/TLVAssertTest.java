package name.velikodniy.jcexpress.tlv;

import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TLVAssertTest {

    private static final String FCI_HEX =
            "6F 0E 84 07 A0000000031010 A5 03 88 01 01";

    @Test
    void tlvListAssertContainsTag() {
        TLVList list = TLVParser.parse(FCI_HEX);
        assertThat(list).containsTag(0x6F);
    }

    @Test
    void tlvListAssertDoesNotContainTag() {
        TLVList list = TLVParser.parse(FCI_HEX);
        assertThat(list).doesNotContainTag(0x99);
    }

    @Test
    void tlvListAssertHasSize() {
        TLVList list = TLVParser.parse(FCI_HEX);
        assertThat(list).hasSize(1);
    }

    @Test
    void tlvListAssertContainsTagFailure() {
        TLVList list = TLVParser.parse(FCI_HEX);
        assertThatThrownBy(() -> assertThat(list).containsTag(0x99))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("0x99");
    }

    @Test
    void tlvAssertIsConstructed() {
        TLV fci = TLVParser.parse(FCI_HEX).get(0);
        assertThat(fci).isConstructed();
    }

    @Test
    void tlvAssertIsPrimitive() {
        TLV dfName = TLVParser.parse("84 02 AABB").get(0);
        assertThat(dfName).isPrimitive();
    }

    @Test
    void tlvAssertHasValue() {
        TLV dfName = TLVParser.parse(FCI_HEX).findRecursive(Tags.DF_NAME).orElseThrow();
        assertThat(dfName).hasValue("A0000000031010");
    }

    @Test
    void tlvAssertHasLength() {
        TLV dfName = TLVParser.parse(FCI_HEX).findRecursive(Tags.DF_NAME).orElseThrow();
        assertThat(dfName).hasLength(7);
    }

    @Test
    void tlvAssertNavigation() {
        TLVList list = TLVParser.parse(FCI_HEX);
        assertThat(list).tag(0x6F)
                .isConstructed()
                .tag(Tags.DF_NAME)
                    .hasValue("A0000000031010")
                    .hasLength(7);
    }

    @Test
    void tlvAssertDeepNavigation() {
        TLVList list = TLVParser.parse(FCI_HEX);
        assertThat(list).tag(0x6F)
                .tag(Tags.FCI_PROPRIETARY)
                    .isConstructed()
                    .tag(Tags.SFI)
                        .isPrimitive()
                        .hasValue("01");
    }

    @Test
    void tlvAssertHasValueFailure() {
        TLV dfName = TLVParser.parse(FCI_HEX).findRecursive(Tags.DF_NAME).orElseThrow();
        assertThatThrownBy(() -> assertThat(dfName).hasValue("DEADBEEF"))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("A0000000031010")
                .hasMessageContaining("DEADBEEF");
    }
}
