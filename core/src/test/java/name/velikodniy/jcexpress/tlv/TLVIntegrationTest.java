package name.velikodniy.jcexpress.tlv;

import name.velikodniy.jcexpress.APDUResponse;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

/**
 * Integration tests using real-world SELECT FCI response data.
 */
class TLVIntegrationTest {

    @Test
    void shouldParseVisaSelectResponse() {
        // Typical Visa SELECT response FCI
        // 6F 1D (29 bytes)
        //   84 07 A0000000031010          (DF Name = Visa AID) = 9 bytes
        //   A5 12 (18 bytes)              (FCI Proprietary) = 20 bytes
        //     50 04 56495341              (Application Label = "VISA") = 6
        //     87 01 01                    (Application Priority = 1) = 3
        //     9F38 06 9F33039F3501        (PDOL) = 9
        String hex = "6F 1D 84 07 A0000000031010 A5 12 50 04 56495341 87 01 01 9F38 06 9F33039F3501";
        byte[] fciData = hexToBytes(hex);

        APDUResponse response = new APDUResponse(fciData, 0x9000);

        assertThat(response).isSuccess();
        assertThat(response).tlv()
                .containsTag(Tags.FCI_TEMPLATE)
                .tag(Tags.FCI_TEMPLATE)
                    .isConstructed()
                    .tag(Tags.DF_NAME)
                        .hasValue("A0000000031010")
                        .hasLength(7);
    }

    @Test
    void shouldParseMastercardSelectResponse() {
        // Simplified Mastercard FCI
        // 6F 11 (17 bytes)
        //   84 07 A0000000041010  (DF Name = Mastercard AID) = 9
        //   A5 06                 (FCI Proprietary) = 8
        //     50 04 4D432020      (Application Label = "MC  ") = 6
        String hex = "6F 11 84 07 A0000000041010 A5 06 50 04 4D432020";
        TLVList fci = TLVParser.parse(hex);

        TLV dfName = fci.findRecursive(Tags.DF_NAME).orElseThrow();
        assertThat(dfName).hasValue("A0000000041010");

        TLV label = fci.findRecursive(Tags.APPLICATION_LABEL).orElseThrow();
        assertThat(label).hasLength(4);
    }

    @Test
    void shouldParseTlvFromApduResponse() {
        // Build an APDUResponse with TLV data and use tlv() method
        byte[] data = TLVBuilder.create()
                .addConstructed(Tags.FCI_TEMPLATE, b -> b
                        .add(Tags.DF_NAME, "A0000000031010")
                )
                .build();
        APDUResponse response = new APDUResponse(data, 0x9000);

        TLVList tlv = response.tlv();
        TLV dfName = tlv.findRecursive(Tags.DF_NAME).orElseThrow();
        assertThat(dfName).hasValue("A0000000031010");
    }

    private static byte[] hexToBytes(String hex) {
        return name.velikodniy.jcexpress.Hex.decode(hex);
    }
}
