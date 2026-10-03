package name.velikodniy.jcexpress.assertions;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.memory.MemoryInfo;
import name.velikodniy.jcexpress.tlv.TLV;
import name.velikodniy.jcexpress.tlv.TLVList;
import name.velikodniy.jcexpress.tlv.TLVParser;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThatThrownBy;

/**
 * One static import is enough: {@link JCXAssertions} extends AssertJ's {@code Assertions}, so the assertions of
 * AssertJ and those of JavaCard Express come from the same {@code assertThat}, and the types of JavaCard Express
 * still get their own assertion classes.
 */
class JCXAssertionsTest {

    private final APDUResponse response = APDUResponse.fromHex("6F 03 84 01 A0 9000");

    @Test
    void theAssertionsOfAssertJComeWithTheSameImport() {
        assertThat(new byte[]{1, 2}).hasSize(2).containsExactly(1, 2);
        assertThat(70).isEqualTo(70);
        assertThat("Hello").startsWith("He");
        assertThatThrownBy(() -> {
            throw new IllegalStateException("x");
        }).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theTypesOfJavaCardExpressStillGetTheirOwnAssertions() {
        TLVList list = TLVParser.parse(response.data());
        TLV tlv = list.find(0x6F).orElseThrow();

        APDUResponseAssert responseAssert = assertThat(response);
        TLVListAssert listAssert = assertThat(list);
        TLVAssert tlvAssert = assertThat(tlv);
        MemoryInfoAssert memoryAssert = assertThat(new MemoryInfo(100, 10, 10));

        responseAssert.isSuccess().tlvContains(0x6F);
        listAssert.containsTag(0x6F);
        tlvAssert.isConstructed().tag(0x84).hasValue("A0");
        memoryAssert.isNotNull();
    }
}
