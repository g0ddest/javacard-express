package name.velikodniy.jcexpress;

import javacard.framework.ISO7816;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SW}: the named status words carry the values of the Java Card API ({@code javacard.framework.ISO7816},
 * here jCardSim's) as two-byte ints, and {@link SW#describe(int)} gives their meaning after ISO/IEC 7816-4:2005
 * 5.1.3 (Tables 5 and 6).
 */
class SWTest {

    @Test
    void theConstantsHaveTheValuesOfTheJavaCardApiAsTwoByteInts() {
        assertThat(SW.NO_ERROR).isEqualTo(ISO7816.SW_NO_ERROR & 0xFFFF).isEqualTo(0x9000);
        assertThat(SW.BYTES_REMAINING_00).isEqualTo(ISO7816.SW_BYTES_REMAINING_00);
        assertThat(SW.WARNING_STATE_UNCHANGED).isEqualTo(ISO7816.SW_WARNING_STATE_UNCHANGED);
        assertThat(SW.WRONG_LENGTH).isEqualTo(ISO7816.SW_WRONG_LENGTH);
        assertThat(SW.LOGICAL_CHANNEL_NOT_SUPPORTED).isEqualTo(ISO7816.SW_LOGICAL_CHANNEL_NOT_SUPPORTED);
        assertThat(SW.SECURE_MESSAGING_NOT_SUPPORTED).isEqualTo(ISO7816.SW_SECURE_MESSAGING_NOT_SUPPORTED);
        assertThat(SW.LAST_COMMAND_EXPECTED).isEqualTo(ISO7816.SW_LAST_COMMAND_EXPECTED);
        assertThat(SW.COMMAND_CHAINING_NOT_SUPPORTED).isEqualTo(ISO7816.SW_COMMAND_CHAINING_NOT_SUPPORTED);
        assertThat(SW.SECURITY_STATUS_NOT_SATISFIED).isEqualTo(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        assertThat(SW.FILE_INVALID).isEqualTo(ISO7816.SW_FILE_INVALID);
        assertThat(SW.DATA_INVALID).isEqualTo(ISO7816.SW_DATA_INVALID);
        assertThat(SW.CONDITIONS_NOT_SATISFIED).isEqualTo(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        assertThat(SW.COMMAND_NOT_ALLOWED).isEqualTo(ISO7816.SW_COMMAND_NOT_ALLOWED);
        assertThat(SW.APPLET_SELECT_FAILED).isEqualTo(ISO7816.SW_APPLET_SELECT_FAILED);
        assertThat(SW.WRONG_DATA).isEqualTo(ISO7816.SW_WRONG_DATA);
        assertThat(SW.FUNC_NOT_SUPPORTED).isEqualTo(ISO7816.SW_FUNC_NOT_SUPPORTED);
        assertThat(SW.FILE_NOT_FOUND).isEqualTo(ISO7816.SW_FILE_NOT_FOUND);
        assertThat(SW.RECORD_NOT_FOUND).isEqualTo(ISO7816.SW_RECORD_NOT_FOUND);
        assertThat(SW.FILE_FULL).isEqualTo(ISO7816.SW_FILE_FULL);
        assertThat(SW.INCORRECT_P1P2).isEqualTo(ISO7816.SW_INCORRECT_P1P2);
        assertThat(SW.WRONG_P1P2).isEqualTo(ISO7816.SW_WRONG_P1P2);
        assertThat(SW.CORRECT_LENGTH_00).isEqualTo(ISO7816.SW_CORRECT_LENGTH_00);
        assertThat(SW.INS_NOT_SUPPORTED).isEqualTo(ISO7816.SW_INS_NOT_SUPPORTED);
        assertThat(SW.CLA_NOT_SUPPORTED).isEqualTo(ISO7816.SW_CLA_NOT_SUPPORTED);
        assertThat(SW.UNKNOWN).isEqualTo(ISO7816.SW_UNKNOWN);
    }

    @Test
    void theStatusWordsOfIso7816ThatTheJavaCardApiLacksAreNamedToo_iso7816_4_5_1_3() {
        assertThat(SW.SELECTED_FILE_INVALIDATED).isEqualTo(0x6283);
        assertThat(SW.VERIFICATION_FAILED).isEqualTo(0x63C0);
        assertThat(SW.EXECUTION_ERROR).isEqualTo(0x6400);
        assertThat(SW.MEMORY_FAILURE).isEqualTo(0x6581);
        assertThat(SW.REFERENCED_DATA_NOT_FOUND).isEqualTo(0x6A88);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            9000 | success
            6982 | security status not satisfied
            6983 | authentication method blocked
            6985 | conditions of use not satisfied
            6A80 | incorrect parameters in the command data field
            6A82 | file or application not found
            6A88 | referenced data not found
            6D00 | instruction code not supported or invalid
            6E00 | class not supported
            6F00 | no precise diagnosis
            6999 | applet selection failed
            """)
    void describeGivesTheMeaningOfAStatusWord_iso7816_4_5_1_3(String sw, String meaning) {
        assertThat(SW.describe(Integer.parseInt(sw, 16))).isEqualTo(meaning);
    }

    @Test
    void describeCountsTheBytesThatAre61XXAnd6CXX_iso7816_4_5_1_3() {
        assertThat(SW.describe(0x6110)).isEqualTo("16 response bytes still available");
        assertThat(SW.describe(0x6100)).isEqualTo("more response bytes available");
        assertThat(SW.describe(0x6C08)).isEqualTo("wrong Le field, 8 bytes available");
        // 5.1.3: SW2 is re-sent "as short Le field", where '00' stands for 256
        assertThat(SW.describe(0x6C00)).isEqualTo("wrong Le field, 256 bytes available");
    }

    @Test
    void describeCountsTheTriesThatAre63CX_iso7816_4_5_1_3() {
        assertThat(SW.describe(0x63C2)).isEqualTo("verification failed, 2 tries left");
        assertThat(SW.describe(0x63C1)).isEqualTo("verification failed, 1 try left");
        assertThat(SW.describe(0x63C0)).isEqualTo("verification failed, 0 tries left");
        assertThat(SW.describe(0x6300)).isEqualTo("verification failed");
    }

    @Test
    void anUnlistedStatusWordGetsTheMeaningOfItsGroupOrIsUnknown_iso7816_4_5_1_3() {
        assertThat(SW.describe(0x6A8F)).isEqualTo("wrong parameters P1-P2");
        assertThat(SW.describe(0x69FF)).isEqualTo("command not allowed");
        assertThat(SW.describe(0x6290)).isEqualTo("warning, state of non-volatile memory unchanged");
        assertThat(SW.describe(0x9F10)).isEqualTo("unknown status");
    }

    @Test
    void describeTakesTheShortConstantsOfTheJavaCardApi() {
        assertThat(SW.describe(ISO7816.SW_NO_ERROR)).isEqualTo("success");
        assertThat(SW.describe(ISO7816.SW_WRONG_DATA)).isEqualTo("incorrect parameters in the command data field");
    }

    @Test
    void aValueThatIsNotTwoBytesIsRejected() {
        assertThatThrownBy(() -> SW.describe(0x19000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0x19000");
        assertThatThrownBy(() -> SW.describe(-0x8001)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatShowsTheStatusWordWithItsMeaning() {
        assertThat(SW.format(0x6982)).isEqualTo("6982 (security status not satisfied)");
        assertThat(SW.format(ISO7816.SW_NO_ERROR)).isEqualTo("9000 (success)");
    }
}
