package name.velikodniy.jcexpress;

import javacard.framework.ISO7816;
import name.velikodniy.jcexpress.assertions.APDUResponseAssert;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class APDUResponseAssertTest {

    @Test
    void isSuccessShouldPassFor9000() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65}, 0x9000);
        assertThat(response).isSuccess();
    }

    @Test
    void isSuccessShouldFailForNon9000() {
        APDUResponse response = new APDUResponse(new byte[0], 0x6D00);
        assertThatThrownBy(() -> assertThat(response).isSuccess())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("9000")
                .hasMessageContaining("6D00");
    }

    @Test
    void statusWordShouldPassOnMatch() {
        APDUResponse response = new APDUResponse(new byte[0], 0x6D00);
        assertThat(response).statusWord(0x6D00);
    }

    @Test
    void statusWordShouldFailOnMismatch() {
        APDUResponse response = new APDUResponse(new byte[0], 0x6A82);
        assertThatThrownBy(() -> assertThat(response).statusWord(0x9000))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("6A82")
                .hasMessageContaining("file or application not found");
    }

    @Test
    void hasDataLengthShouldPass() {
        APDUResponse response = new APDUResponse(new byte[]{1, 2, 3}, 0x9000);
        assertThat(response).hasDataLength(3);
    }

    @Test
    void hasDataLengthShouldFail() {
        APDUResponse response = new APDUResponse(new byte[]{1, 2}, 0x9000);
        assertThatThrownBy(() -> assertThat(response).hasDataLength(5))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void dataEqualsShouldPass() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}, 0x9000);
        assertThat(response).dataEquals(0x48, 0x65, 0x6C, 0x6C, 0x6F);
    }

    @Test
    void dataEqualsShouldFail() {
        APDUResponse response = new APDUResponse(new byte[]{0x01}, 0x9000);
        assertThatThrownBy(() -> assertThat(response).dataEquals(0xFF))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void dataAsStringShouldPass() {
        APDUResponse response = new APDUResponse("Hello".getBytes(), 0x9000);
        assertThat(response).dataAsString().isEqualTo("Hello");
    }

    @Test
    void dataAsHexShouldPass() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}, 0x9000);
        assertThat(response).dataAsHex().isEqualTo("48656C6C6F");
    }

    @Test
    void chainingAssertionsShouldWork() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}, 0x9000);
        assertThat(response)
                .isSuccess()
                .hasDataLength(5)
                .dataEquals(0x48, 0x65, 0x6C, 0x6C, 0x6F);
    }

    // The status bytes SW1-SW2 are two bytes (ISO/IEC 7816-4 5.1.3); the Java Card API declares them as short
    // constants (javacard.framework.ISO7816.SW_*), so SW_NO_ERROR reaches an int parameter as 0xFFFF9000.

    @Test
    void statusWordAcceptsTheShortConstantsOfTheJavaCardApi() {
        APDUResponse response = new APDUResponse(new byte[0], 0x9000);

        assertThat(response).statusWord(ISO7816.SW_NO_ERROR);
    }

    @Test
    void aShortConstantThatDoesNotMatchIsReportedAsTwoBytes() {
        APDUResponse response = new APDUResponse(new byte[0], 0x6982);

        assertThatThrownBy(() -> assertThat(response).statusWord(ISO7816.SW_NO_ERROR))
                .isInstanceOf(AssertionError.class)
                .hasMessage("Expected SW=9000 (success) but was SW=6982 (security status not satisfied)");
    }

    @Test
    void hasSw1AcceptsAByteValue() {
        APDUResponse response = new APDUResponse(new byte[0], 0x9000);

        assertThat(response).hasSw1((byte) 0x90);
        assertThatThrownBy(() -> assertThat(response).hasSw1((byte) 0x6A))
                .isInstanceOf(AssertionError.class)
                .hasMessageStartingWith("Expected SW1=6A but was SW1=90");
    }

    @Test
    void statusBytesOutsideTheirRangeAreRejected() {
        APDUResponse response = new APDUResponse(new byte[0], 0x9000);

        assertThatThrownBy(() -> assertThat(response).statusWord(0x19000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0x19000");
        assertThatThrownBy(() -> assertThat(response).statusWord(-0x8001))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assertThat(response).hasSw1(0x190))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0x190");
        assertThatThrownBy(() -> assertThat(response).hasSw1(-129))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Names in AssertJ style next to the older ones, which stay. */
    @Nested
    class AssertJStyleNames {

        private final APDUResponse balance = APDUResponse.fromHex("0046 9000");
        private final APDUResponse denied = APDUResponse.fromHex("6982");

        @Test
        void hasStatusWordIsStatusWord() {
            assertThat(denied).hasStatusWord(SW.SECURITY_STATUS_NOT_SATISFIED).hasStatusWord(0x6982);
            assertThat(balance).hasStatusWord(ISO7816.SW_NO_ERROR);
            assertThatThrownBy(() -> assertThat(denied).hasStatusWord(SW.NO_ERROR))
                    .hasMessage("Expected SW=9000 (success) but was SW=6982 (security status not satisfied)");
        }

        @Test
        void hasStatusWordInAcceptsAnyListedStatusWord() {
            assertThat(APDUResponse.fromHex("6310")).hasStatusWordIn(0x9000, 0x6310);
            assertThatThrownBy(() -> assertThat(denied).hasStatusWordIn(SW.NO_ERROR, 0x6310))
                    .hasMessage("Expected SW in [9000 (success), 6310 (warning, state of non-volatile memory"
                            + " changed)] but was SW=6982 (security status not satisfied)");
        }

        @Test
        void hasSw2AndIsNotSuccess() {
            assertThat(APDUResponse.fromHex("63C2")).hasSw1(0x63).hasSw2(0xC2).isNotSuccess();
            assertThatThrownBy(() -> assertThat(denied).hasSw2(0x00))
                    .hasMessageStartingWith("Expected SW2=00 but was SW2=82");
            assertThatThrownBy(() -> assertThat(balance).isNotSuccess())
                    .hasMessage("Expected a status word other than 9000 (success) but was SW=9000");
        }

        @Test
        void hasDataHasDataHexAndHasNoData() {
            assertThat(balance).hasData(new byte[]{0x00, 0x46}).hasDataHex("00 46");
            assertThat(denied).hasNoData();
            assertThatThrownBy(() -> assertThat(balance).hasDataHex("0047"))
                    .hasMessage("Expected data [0047] but was [0046]");
            assertThatThrownBy(() -> assertThat(balance).hasNoData())
                    .hasMessage("Expected no data but was [0046] (2 bytes)");
        }
    }

    /** Navigation into the data with the assertions of AssertJ. */
    @Nested
    class Navigation {

        private final APDUResponse response = APDUResponse.fromHex("0046 FFFE 9000");

        @Test
        void dataIsAByteArrayAssertion() {
            assertThat(response).data().hasSize(4).startsWith((byte) 0x00, (byte) 0x46);
            assertThatThrownBy(() -> assertThat(response).data().hasSize(2))
                    .hasMessageContaining("data of")
                    .hasMessageContaining("0046FFFE");
        }

        @Test
        void numbersAreIntegerAssertions() {
            assertThat(response).isSuccess().u16(0).isEqualTo(70);
            assertThat(response).u8(2).isEqualTo(0xFF);
            assertThat(response).s16(2).isEqualTo(-2);
            assertThatThrownBy(() -> assertThat(response).u16(0).isEqualTo(71))
                    .hasMessageContaining("u16 at offset 0")
                    .hasMessageContaining("71");
        }

        @Test
        void aNumberBeyondTheDataFailsTheAssertionAndShowsTheStatusWord() {
            assertThatThrownBy(() -> assertThat(APDUResponse.fromHex("6982")).u16(0))
                    .isInstanceOf(AssertionError.class)
                    .hasMessage("Expected response data with 2 bytes at offset 0 for u16, but the data has 0 bytes"
                            + " (SW=6982, security status not satisfied)");
        }
    }

    /** Mismatches of status word and data carry expected and actual values, so IDEs can show a diff. */
    @Nested
    class Diffs {

        @Test
        void aStatusWordMismatchIsAnAssertionFailedErrorWithBothValues() {
            assertThatThrownBy(() -> assertThat(APDUResponse.fromHex("6982")).isSuccess())
                    .isInstanceOfSatisfying(AssertionFailedError.class, error -> {
                        assertThat(error.getExpected().getValue())
                                .isEqualTo("9000 (success)");
                        assertThat(error.getActual().getValue())
                                .isEqualTo("6982 (security status not satisfied)");
                    });
        }

        @Test
        void aDataMismatchIsAnAssertionFailedErrorWithBothValuesAsHex() {
            assertThatThrownBy(() -> assertThat(APDUResponse.fromHex("0046 9000")).dataEquals(0x00, 0x47))
                    .isInstanceOfSatisfying(AssertionFailedError.class, error -> {
                        assertThat(error.getExpected().getValue()).isEqualTo("0047");
                        assertThat(error.getActual().getValue()).isEqualTo("0046");
                    });
        }
    }
}
