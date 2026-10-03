package name.velikodniy.jcexpress;

import javacard.framework.ISO7816;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class APDUResponseTest {

    @Test
    void shouldParseRawBytes() {
        byte[] raw = {0x48, 0x65, 0x6C, 0x6C, 0x6F, (byte) 0x90, 0x00};
        APDUResponse response = new APDUResponse(raw);

        assertThat(response.sw()).isEqualTo(0x9000);
        assertThat(response.sw1()).isEqualTo(0x90);
        assertThat(response.sw2()).isEqualTo(0x00);
        assertThat(response.data()).containsExactly(0x48, 0x65, 0x6C, 0x6C, 0x6F);
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void shouldCreateFromDataAndSw() {
        APDUResponse response = new APDUResponse(new byte[]{0x01, 0x02}, 0x9000);
        assertThat(response.sw()).isEqualTo(0x9000);
        assertThat(response.data()).containsExactly(0x01, 0x02);
    }

    @Test
    void aShortStatusWordConstantOfTheJavaCardApiIsTheTwoByteStatusWord() {
        // ISO/IEC 7816-4 5.1.3: SW1-SW2 are two bytes; javacard.framework.ISO7816.SW_NO_ERROR is the short 0x9000
        APDUResponse response = new APDUResponse(new byte[0], ISO7816.SW_NO_ERROR);

        assertThat(response.sw()).isEqualTo(0x9000);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response).isEqualTo(APDUResponse.fromHex("9000"));
    }

    @Test
    void aStatusWordOutsideTwoBytesIsRejected() {
        assertThatThrownBy(() -> new APDUResponse(new byte[0], 0x10000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0x10000");
        assertThatThrownBy(() -> new APDUResponse(new byte[0], -0x8001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnDataAsHex() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}, 0x9000);
        assertThat(response.dataAsHex()).isEqualTo("48656C6C6F");
    }

    @Test
    void shouldReturnDataAsString() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}, 0x9000);
        assertThat(response.dataAsString()).isEqualTo("Hello");
    }

    @Test
    void shouldDetectFailure() {
        APDUResponse response = new APDUResponse(new byte[0], 0x6D00);
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    void shouldHandleSwOnly() {
        byte[] raw = {(byte) 0x6D, 0x00};
        APDUResponse response = new APDUResponse(raw);
        assertThat(response.sw()).isEqualTo(0x6D00);
        assertThat(response.data()).isEmpty();
    }

    @Test
    void shouldHaveReadableToString() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65}, 0x9000);
        assertThat(response.toString()).contains("4865").contains("9000");
    }

    /** Responses are values: equal data and status word make equal responses (JUnit and AssertJ rely on it). */
    @Nested
    class ValueSemantics {

        @Test
        void responsesWithTheSameDataAndStatusWordAreEqual() {
            APDUResponse raw = new APDUResponse(Hex.decode("00059000"));
            APDUResponse parts = new APDUResponse(new byte[]{0x00, 0x05}, 0x9000);

            assertThat(raw).isEqualTo(parts).hasSameHashCodeAs(parts);
        }

        @Test
        void otherDataOrAnotherStatusWordIsAnotherResponse() {
            APDUResponse response = new APDUResponse(Hex.decode("00059000"));

            assertThat(response).isNotEqualTo(new APDUResponse(Hex.decode("00069000")));
            assertThat(response).isNotEqualTo(new APDUResponse(Hex.decode("00056A82")));
            assertThat(new APDUResponse(Hex.decode("9000"))).isNotEqualTo(new APDUResponse(Hex.decode("009000")));
        }

        @Test
        void fromHexTakesTheDataFollowedByTheStatusWord() {
            APDUResponse response = APDUResponse.fromHex("48 65 6C 6C 6F 90 00");

            assertThat(response.dataAsString()).isEqualTo("Hello");
            assertThat(response.sw()).isEqualTo(0x9000);
            assertThat(APDUResponse.fromHex("6a82")).isEqualTo(new APDUResponse(new byte[0], 0x6A82));
        }

        @Test
        void fromHexRejectsTextWithoutAStatusWord() {
            assertThatThrownBy(() -> APDUResponse.fromHex("90"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SW1 SW2");
            assertThatThrownBy(() -> APDUResponse.fromHex("90 0G")).isInstanceOf(IllegalArgumentException.class);
        }

        /** JUnit converts a String argument with the type's only static String factory (fromHex). */
        @ParameterizedTest
        @CsvSource({"9000, 0, 9000", "48656C6C6F9000, 5, 9000", "6A82, 0, 6A82"})
        void junitConvertsCsvColumnsImplicitly(APDUResponse response, int dataLength, String sw) {
            assertThat(response.data()).hasSize(dataLength);
            assertThat(response.sw()).isEqualTo(Integer.parseInt(sw, 16));
        }

        @Test
        void theCommandDoesNotTakePartInEquality() {
            APDUResponse response = APDUResponse.fromHex("9000");

            assertThat(response.inReplyTo(Hex.decode("80010000"))).isEqualTo(response).hasSameHashCodeAs(response);
        }
    }

    /** requireSuccess() is an assertion: a failure counts as a failed test and shows the exchange. */
    @Nested
    class RequireSuccess {

        @Test
        void returnsTheResponseOnSuccess() {
            APDUResponse response = APDUResponse.fromHex("01029000");

            assertThat(response.requireSuccess()).isSameAs(response);
        }

        @Test
        void failsWithAnAssertionErrorThatShowsTheCommandAndTheResponse() {
            APDUResponse response = APDUResponse.fromHex("6A82").inReplyTo(Hex.decode("00A4040005F000000001"));

            assertThatThrownBy(response::requireSuccess)
                    .isInstanceOf(AssertionError.class)
                    .isInstanceOf(UnexpectedStatusWordError.class)
                    .hasMessage("Expected SW 9000 (success) but was 6A82 (file or application not found)\n"
                            + "C: 00A4040005F000000001\nR: 6A82")
                    .satisfies(e -> assertThat(((UnexpectedStatusWordError) e).response()).isEqualTo(response));
        }

        @Test
        void withoutTheCommandTheMessageShowsTheResponse() {
            assertThatThrownBy(() -> APDUResponse.fromHex("01026985").requireSuccess())
                    .isInstanceOf(UnexpectedStatusWordError.class)
                    .hasMessage("Expected SW 9000 (success) but was 6985 (conditions of use not satisfied)"
                            + "\nR: 01026985");
        }

        @Test
        void theCommandIsCopied() {
            byte[] command = Hex.decode("80010000");
            APDUResponse response = APDUResponse.fromHex("6D00").inReplyTo(command);
            command[1] = 0x7F;

            assertThatThrownBy(response::requireSuccess).hasMessageContaining("C: 80010000");
        }
    }

    /**
     * Numbers in the response data, big-endian as the Java Card API writes them ({@code Util.setShort}); the
     * readers return {@code int}, so {@code assertThat(response.u16(0)).isEqualTo(70)} compares numbers.
     */
    @Nested
    class Numbers {

        private final APDUResponse response = APDUResponse.fromHex("00 46 FF FE 7F 9000");

        @Test
        void u8ReadsAnUnsignedByte() {
            assertThat(response.u8(0)).isZero();
            assertThat(response.u8(2)).isEqualTo(0xFF);
        }

        @Test
        void u16ReadsAnUnsignedBigEndianShort() {
            assertThat(response.u16(0)).isEqualTo(70);
            assertThat(response.u16(2)).isEqualTo(0xFFFE);
        }

        @Test
        void s16ReadsASignedShortLikeUtilGetShort() {
            assertThat(response.s16(0)).isEqualTo(70);
            assertThat(response.s16(2)).isEqualTo(-2);
        }

        @Test
        void dataFromToCopiesARange() {
            assertThat(response.data(1, 3)).containsExactly(0x46, 0xFF);
            assertThat(response.data(5, 5)).isEmpty();
        }

        @Test
        void aReadBeyondTheDataNamesOffsetWidthAndTheData() {
            assertThatThrownBy(() -> response.u16(4))
                    .isInstanceOf(IndexOutOfBoundsException.class)
                    .hasMessage("u16 at offset 4 needs 2 bytes, but the response data has 5 bytes: 0046FFFE7F");
            assertThatThrownBy(() -> response.u8(-1))
                    .isInstanceOf(IndexOutOfBoundsException.class)
                    .hasMessageContaining("offset -1");
            assertThatThrownBy(() -> APDUResponse.fromHex("6982").s16(0))
                    .isInstanceOf(IndexOutOfBoundsException.class)
                    .hasMessage("s16 at offset 0 needs 2 bytes, but the response data has 0 bytes");
            assertThatThrownBy(() -> response.data(3, 6))
                    .isInstanceOf(IndexOutOfBoundsException.class)
                    .hasMessageContaining("3 to 6");
            assertThatThrownBy(() -> response.data(3, 2)).isInstanceOf(IndexOutOfBoundsException.class);
        }
    }

    /** requireSw accepts the listed status words, also as short constants of the Java Card API. */
    @Nested
    class RequireSw {

        @Test
        void returnsTheResponseWhenItsStatusWordIsListed() {
            APDUResponse response = APDUResponse.fromHex("6310");

            assertThat(response.requireSw(0x9000, 0x6310)).isSameAs(response);
            assertThat(APDUResponse.fromHex("9000").requireSw(ISO7816.SW_NO_ERROR)).isNotNull();
        }

        @Test
        void failsWithTheListedStatusWordsAndTheirMeaning() {
            APDUResponse response = APDUResponse.fromHex("6A82").inReplyTo(Hex.decode("00A4040005F000000001"));

            assertThatThrownBy(() -> response.requireSw(SW.NO_ERROR, SW.SECURITY_STATUS_NOT_SATISFIED))
                    .isInstanceOf(UnexpectedStatusWordError.class)
                    .hasMessage("Expected SW 9000 (success) or 6982 (security status not satisfied) but was 6A82"
                            + " (file or application not found)\nC: 00A4040005F000000001\nR: 6A82");
        }

        @Test
        void needsAtLeastOneStatusWordInRange() {
            APDUResponse response = APDUResponse.fromHex("9000");

            assertThatThrownBy(response::requireSw).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> response.requireSw(0x19000))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0x19000");
        }
    }
}
