package name.velikodniy.jcexpress.api.conformance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Known answers for the {@code javacard.framework.ISO7816} constants taken from ISO/IEC 7816-4, a source that
 * is independent of every Java Card implementation (the class-file comparison in {@link ApiConformanceTest}
 * would not notice a value that the stubs and the reference got wrong in the same way).
 *
 * <p>The values are read from the {@code ConstantValue} attributes of the compiled stubs, which is exactly what
 * javac inlines into an applet (JLS 13.1). Only constants whose ISO/IEC 7816-4 meaning matches their Java Card
 * name are listed; Java Card specific codes such as {@code SW_APPLET_SELECT_FAILED} are covered by the
 * conformance test alone.
 */
class Iso7816KnownAnswerTest {

    private static ApiSurface.Type iso7816;

    @BeforeAll
    static void readStubs() {
        iso7816 = ApiSurface.read(ApiLocations.stubClasses()).types().get("javacard/framework/ISO7816");
        assertThat(iso7816).as("javacard.framework.ISO7816 in the stubs").isNotNull();
    }

    /**
     * Command APDU layout of ISO/IEC 7816-4:2005 5.1: the four header bytes CLA INS P1 P2, then the Lc field,
     * which is one byte for a short command and three bytes ({@code '00'} plus two length bytes) for an
     * extended one, so the command data starts at offset 5 or 7. Class byte {@code '00'} is the interindustry
     * class without command chaining, secure messaging or logical channel (5.1.1, Table 2); SELECT is INS
     * {@code 'A4'} (7.1.1) and EXTERNAL AUTHENTICATE is INS {@code '82'} (7.5, basic security handling).
     *
     * @param constant name of the constant in {@code ISO7816}
     * @param expected value defined by ISO/IEC 7816-4, in hexadecimal
     */
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
        "OFFSET_CLA, 00", "OFFSET_INS, 01", "OFFSET_P1, 02", "OFFSET_P2, 03", "OFFSET_LC, 04",
        "OFFSET_CDATA, 05", "OFFSET_EXT_CDATA, 07", "CLA_ISO7816, 00",
        "INS_SELECT, A4", "INS_EXTERNAL_AUTHENTICATE, 82"
    })
    void commandApduLayoutFollowsIso7816Part4(String constant, String expected) {
        assertThat(String.format("%02X", byteConstant(constant) & 0xFF)).as("ISO7816." + constant).isEqualTo(expected);
    }

    /**
     * Interindustry status words of ISO/IEC 7816-4:2005 5.1.3 (Tables 5 and 6). For {@code '61XX'} and
     * {@code '6CXX'} the constant carries {@code SW2 = '00'}; applets add the length.
     *
     * @param constant name of the constant in {@code ISO7816}
     * @param expected SW1-SW2 defined by ISO/IEC 7816-4, in hexadecimal
     */
    @ParameterizedTest(name = "{0} = {1}")
    @CsvSource({
        "SW_NO_ERROR, 9000", "SW_BYTES_REMAINING_00, 6100", "SW_WARNING_STATE_UNCHANGED, 6200",
        "SW_WRONG_LENGTH, 6700", "SW_LOGICAL_CHANNEL_NOT_SUPPORTED, 6881",
        "SW_SECURE_MESSAGING_NOT_SUPPORTED, 6882", "SW_LAST_COMMAND_EXPECTED, 6883",
        "SW_COMMAND_CHAINING_NOT_SUPPORTED, 6884", "SW_SECURITY_STATUS_NOT_SATISFIED, 6982",
        "SW_CONDITIONS_NOT_SATISFIED, 6985", "SW_COMMAND_NOT_ALLOWED, 6986", "SW_WRONG_DATA, 6A80",
        "SW_FUNC_NOT_SUPPORTED, 6A81", "SW_FILE_NOT_FOUND, 6A82", "SW_RECORD_NOT_FOUND, 6A83",
        "SW_FILE_FULL, 6A84", "SW_INCORRECT_P1P2, 6A86", "SW_WRONG_P1P2, 6B00", "SW_CORRECT_LENGTH_00, 6C00",
        "SW_INS_NOT_SUPPORTED, 6D00", "SW_CLA_NOT_SUPPORTED, 6E00", "SW_UNKNOWN, 6F00"
    })
    void statusWordsFollowIso7816Part4(String constant, String expected) {
        assertThat(String.format("%04X", shortConstant(constant) & 0xFFFF)).as("ISO7816." + constant)
                .isEqualTo(expected);
    }

    private static byte byteConstant(String name) {
        return (byte) constant(name, "B");
    }

    private static short shortConstant(String name) {
        return (short) constant(name, "S");
    }

    private static int constant(String name, String descriptor) {
        ApiSurface.Field field = iso7816.fields().get(name);
        assertThat(field).as("ISO7816." + name).isNotNull();
        assertThat(field.descriptor()).as("type of ISO7816." + name).isEqualTo(descriptor);
        assertThat(field.constant()).as("ConstantValue of ISO7816." + name).isInstanceOf(Integer.class);
        return (Integer) field.constant();
    }
}
