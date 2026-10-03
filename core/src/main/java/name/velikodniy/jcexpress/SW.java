package name.velikodniy.jcexpress;

import java.util.Map;

/**
 * Status words (SW1-SW2, ISO/IEC 7816-4:2005 5.1.3) by name, and what they mean.
 *
 * <p>The names are those of the Java Card API interface {@code javacard.framework.ISO7816} without the {@code SW_}
 * prefix ({@link #SECURITY_STATUS_NOT_SATISFIED} is {@code ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED}), plus a few
 * values of ISO/IEC 7816-4 that the Java Card API does not name. The constants are {@code int}s from
 * {@code 0x0000} to {@code 0xFFFF}, like {@link APDUResponse#sw()}. The methods of JavaCard Express that take a
 * status word also accept the applet's {@code short} constants: {@code ISO7816.SW_NO_ERROR} reaches an
 * {@code int} parameter as {@code 0xFFFF9000} and counts as {@code 0x9000}.</p>
 *
 * <pre>
 * assertThat(card.send(0x80, 0x20, 0x00, 0x01)).hasStatusWord(SW.SECURITY_STATUS_NOT_SATISFIED);
 * SW.describe(0x6A82);   // "file or application not found"
 * SW.describe(0x63C2);   // "verification failed, 2 tries left"
 * SW.format(0x6982);     // "6982 (security status not satisfied)"
 * </pre>
 *
 * <p>For readers who know HTTP: {@code 9000} is the 200 of a card, {@code 6982} (security status not satisfied)
 * is close to 401/403, {@code 6A82} (file or application not found) to 404 and {@code 6D00} (instruction code not
 * supported) to 405.</p>
 *
 * <p>{@code 61XX} (more response bytes) and {@code 6CXX} (wrong Le) belong to the transmission (ISO/IEC 7816-4:2005
 * 5.1.3). Who completes them: the card of a {@link JavaCardTest} class in {@code send(...)},
 * {@code send(APDUCommand)} and {@code sendHex(...)}, on every backend, as the PC/SC provider of the JDK does on a
 * real reader (GET RESPONSE in the class of the command, the command again with the exact Le); on a reader,
 * javax.smartcardio itself, below every session; {@link name.velikodniy.jcexpress.apdu.APDUSequence} on any session.
 * {@code transmit(byte[])} of the card and sessions used directly ({@code EmbeddedSession}, {@code ContainerSession})
 * return them as the card answered.</p>
 */
public final class SW {

    /** {@code 9000}: normal processing, no further qualification. */
    public static final int NO_ERROR = 0x9000;
    /**
     * {@code 6100}: response bytes still available (SW2 counts them; GET RESPONSE fetches them: see the class
     * documentation for who sends it).
     */
    public static final int BYTES_REMAINING_00 = 0x6100;
    /** {@code 6200}: warning, the state of non-volatile memory is unchanged. */
    public static final int WARNING_STATE_UNCHANGED = 0x6200;
    /** {@code 6283}: selected file invalidated (ISO/IEC 7816-4; not named by the Java Card API). */
    public static final int SELECTED_FILE_INVALIDATED = 0x6283;
    /** {@code 63C0}: verification failed, no tries left; {@code 63CX} counts X tries left (ISO/IEC 7816-4). */
    public static final int VERIFICATION_FAILED = 0x63C0;
    /** {@code 6400}: execution error, state of non-volatile memory unchanged (ISO/IEC 7816-4). */
    public static final int EXECUTION_ERROR = 0x6400;
    /** {@code 6581}: memory failure (ISO/IEC 7816-4). */
    public static final int MEMORY_FAILURE = 0x6581;
    /** {@code 6700}: wrong length. */
    public static final int WRONG_LENGTH = 0x6700;
    /** {@code 6881}: logical channel not supported. */
    public static final int LOGICAL_CHANNEL_NOT_SUPPORTED = 0x6881;
    /** {@code 6882}: secure messaging not supported. */
    public static final int SECURE_MESSAGING_NOT_SUPPORTED = 0x6882;
    /** {@code 6883}: last command of the chain expected. */
    public static final int LAST_COMMAND_EXPECTED = 0x6883;
    /** {@code 6884}: command chaining not supported. */
    public static final int COMMAND_CHAINING_NOT_SUPPORTED = 0x6884;
    /** {@code 6982}: security status not satisfied (e.g. a PIN not verified). */
    public static final int SECURITY_STATUS_NOT_SATISFIED = 0x6982;
    /** {@code 6983}: authentication method blocked (Java Card name: file invalid). */
    public static final int FILE_INVALID = 0x6983;
    /** {@code 6984}: reference data not usable (Java Card name: data invalid). */
    public static final int DATA_INVALID = 0x6984;
    /** {@code 6985}: conditions of use not satisfied. */
    public static final int CONDITIONS_NOT_SATISFIED = 0x6985;
    /** {@code 6986}: command not allowed. */
    public static final int COMMAND_NOT_ALLOWED = 0x6986;
    /** {@code 6999}: applet selection failed (Java Card). */
    public static final int APPLET_SELECT_FAILED = 0x6999;
    /** {@code 6A80}: incorrect parameters in the command data field. */
    public static final int WRONG_DATA = 0x6A80;
    /** {@code 6A81}: function not supported. */
    public static final int FUNC_NOT_SUPPORTED = 0x6A81;
    /** {@code 6A82}: file or application not found (also the answer to a SELECT of an unknown AID). */
    public static final int FILE_NOT_FOUND = 0x6A82;
    /** {@code 6A83}: record not found. */
    public static final int RECORD_NOT_FOUND = 0x6A83;
    /** {@code 6A84}: not enough memory space in the file. */
    public static final int FILE_FULL = 0x6A84;
    /** {@code 6A86}: incorrect parameters P1-P2. */
    public static final int INCORRECT_P1P2 = 0x6A86;
    /** {@code 6A88}: referenced data not found (ISO/IEC 7816-4; not named by the Java Card API). */
    public static final int REFERENCED_DATA_NOT_FOUND = 0x6A88;
    /** {@code 6B00}: wrong parameters P1-P2. */
    public static final int WRONG_P1P2 = 0x6B00;
    /**
     * {@code 6C00}: wrong Le field; SW2 is the exact length ({@code 00} = 256), with which the command is sent again
     * (see the class documentation for who does).
     */
    public static final int CORRECT_LENGTH_00 = 0x6C00;
    /** {@code 6D00}: instruction code not supported or invalid. */
    public static final int INS_NOT_SUPPORTED = 0x6D00;
    /** {@code 6E00}: class not supported. */
    public static final int CLA_NOT_SUPPORTED = 0x6E00;
    /** {@code 6F00}: no precise diagnosis (an uncaught exception in a Java Card applet). */
    public static final int UNKNOWN = 0x6F00;

    /** Meanings of single status words: ISO/IEC 7816-4:2005 Table 6, and the Java Card API for '6999'. */
    private static final Map<Integer, String> MEANINGS = Map.ofEntries(
            Map.entry(0x9000, "success"),
            Map.entry(0x6281, "part of returned data may be corrupted"),
            Map.entry(0x6282, "end of file or record reached before reading Ne bytes"),
            Map.entry(0x6283, "selected file invalidated"),
            Map.entry(0x6300, "verification failed"),
            Map.entry(0x6400, "execution error"),
            Map.entry(0x6581, "memory failure"),
            Map.entry(0x6700, "wrong length"),
            Map.entry(0x6881, "logical channel not supported"),
            Map.entry(0x6882, "secure messaging not supported"),
            Map.entry(0x6883, "last command of the chain expected"),
            Map.entry(0x6884, "command chaining not supported"),
            Map.entry(0x6981, "command incompatible with file structure"),
            Map.entry(0x6982, "security status not satisfied"),
            Map.entry(0x6983, "authentication method blocked"),
            Map.entry(0x6984, "reference data not usable"),
            Map.entry(0x6985, "conditions of use not satisfied"),
            Map.entry(0x6986, "command not allowed"),
            Map.entry(0x6987, "expected secure messaging data objects missing"),
            Map.entry(0x6988, "incorrect secure messaging data objects"),
            Map.entry(0x6999, "applet selection failed"),
            Map.entry(0x6A80, "incorrect parameters in the command data field"),
            Map.entry(0x6A81, "function not supported"),
            Map.entry(0x6A82, "file or application not found"),
            Map.entry(0x6A83, "record not found"),
            Map.entry(0x6A84, "not enough memory space in the file"),
            Map.entry(0x6A85, "Nc inconsistent with TLV structure"),
            Map.entry(0x6A86, "incorrect parameters P1-P2"),
            Map.entry(0x6A87, "Nc inconsistent with parameters P1-P2"),
            Map.entry(0x6A88, "referenced data not found"),
            Map.entry(0x6A89, "file already exists"),
            Map.entry(0x6A8A, "DF name already exists"),
            Map.entry(0x6B00, "wrong parameters P1-P2"),
            Map.entry(0x6D00, "instruction code not supported or invalid"),
            Map.entry(0x6E00, "class not supported"),
            Map.entry(0x6F00, "no precise diagnosis"));

    /** Meanings of SW1 for the status words that {@link #MEANINGS} does not list: ISO/IEC 7816-4:2005 Table 5. */
    private static final Map<Integer, String> GROUPS = Map.of(
            0x62, "warning, state of non-volatile memory unchanged",
            0x63, "warning, state of non-volatile memory changed",
            0x64, "execution error, state of non-volatile memory unchanged",
            0x65, "execution error, state of non-volatile memory changed",
            0x66, "security-related issue",
            0x68, "function in CLA not supported",
            0x69, "command not allowed",
            0x6A, "wrong parameters P1-P2");

    private SW() {
    }

    /**
     * Returns what a status word means, after ISO/IEC 7816-4:2005 5.1.3 (Tables 5 and 6). The counts of
     * {@code 61XX} (response bytes still available), {@code 6CXX} (the exact Le, {@code 00} standing for 256) and
     * {@code 63CX} (tries left) are spelled out; a status word without a meaning of its own gets the meaning of its
     * SW1 group, or {@code "unknown status"}.
     *
     * @param sw the status word, as an int ({@code 0x6A82}) or a {@code short} constant of the Java Card API
     * @return the meaning, e.g. {@code "file or application not found"}
     * @throws IllegalArgumentException if {@code sw} is outside {@code -32768} to {@code 0xFFFF}
     */
    public static String describe(int sw) {
        int value = unsigned(sw);
        int sw1 = value >> 8;
        int sw2 = value & 0xFF;
        if (sw1 == 0x61) {
            return sw2 == 0 ? "more response bytes available" : sw2 + " response bytes still available";
        }
        if (sw1 == 0x6C) {
            return "wrong Le field, " + (sw2 == 0 ? 256 : sw2) + " bytes available";
        }
        if ((value & 0xFFF0) == VERIFICATION_FAILED) {
            int tries = value & 0x0F;
            return "verification failed, " + tries + (tries == 1 ? " try left" : " tries left");
        }
        String meaning = MEANINGS.get(value);
        return meaning != null ? meaning : GROUPS.getOrDefault(sw1, "unknown status");
    }

    /**
     * Returns a status word as four hex digits with its meaning, the form failure messages use.
     *
     * @param sw the status word, as an int or a {@code short} constant of the Java Card API
     * @return e.g. {@code "6982 (security status not satisfied)"}
     * @throws IllegalArgumentException if {@code sw} is outside {@code -32768} to {@code 0xFFFF}
     */
    public static String format(int sw) {
        int value = unsigned(sw);
        return String.format("%04X (%s)", value, describe(value));
    }

    /**
     * A status word given as an unsigned value or as a {@code short} constant of the Java Card API, as the
     * unsigned two-byte value (SW1-SW2 are two bytes, ISO/IEC 7816-4:2005 5.1.3).
     */
    static int unsigned(int sw) {
        if (sw < Short.MIN_VALUE || sw > 0xFFFF) {
            throw new IllegalArgumentException(String.format("A status word is two bytes (ISO/IEC 7816-4 5.1.3):"
                    + " 0x0 to 0xFFFF, or a short constant of the Java Card API; got 0x%X", sw));
        }
        return sw & 0xFFFF;
    }
}
