package name.velikodniy.jcexpress.apdu;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;

import java.util.Arrays;

/**
 * A command APDU as a value (ISO/IEC 7816-4:2005 5.1): header, data and Le, immutable, so a test class can keep its
 * commands in constants and parameterized tests can take them as arguments.
 *
 * <pre>
 * static final APDUCommand GET_BALANCE = APDUCommand.of(0x80, 0x52).le(2);
 * static final APDUCommand CREDIT = APDUCommand.of(0x80, 0x30);
 *
 * assertThat(card.send(CREDIT.data(0x00, 0x64))).isSuccess();
 * assertThat(card.send(GET_BALANCE)).isSuccess().u16(0).isEqualTo(100);
 * assertThat(card.send(APDUCommand.fromHex("80 30 00 00 02 0064"))).isSuccess();
 *
 * &#64;ParameterizedTest
 * &#64;CsvSource({"8052000002, 0000 9000", "807F0000, 6D00"})   // GET BALANCE of a new wallet, an unknown INS
 * void answers(APDUCommand command, APDUResponse expected, SmartCardSession card) {
 *     assertThat(card.send(command)).isEqualTo(expected);
 * }
 * </pre>
 *
 * <p>{@link #fromHex(String)} is the only static factory that takes a {@code String}, so JUnit converts
 * {@code @CsvSource} and {@code @ValueSource} arguments to commands without a converter (as it does for
 * {@code APDUResponse.fromHex}). Header bytes are {@code 0x00} to {@code 0xFF} or an applet's {@code byte}
 * constants ({@code (byte) 0x80}); {@code le} means what it means for
 * {@link SmartCardSession#send(int, int, int, int, byte[], int)}: {@link SmartCardSession#NO_LE} for no Le
 * field, {@code 1} to {@code 65536} for Ne, {@code 0} for an Le field of {@code '00'} bytes. {@link #toBytes()}
 * encodes like every session ({@link APDUCodec#encode(int, int, int, int, byte[], int)}): the short form when the
 * lengths allow it, the extended form otherwise.</p>
 *
 * @param cla  the class byte, {@code 0x00} to {@code 0xFF}
 * @param ins  the instruction byte, {@code 0x00} to {@code 0xFF}
 * @param p1   the parameter byte P1, {@code 0x00} to {@code 0xFF}
 * @param p2   the parameter byte P2, {@code 0x00} to {@code 0xFF}
 * @param data the command data (Nc bytes, at most {@value APDUCodec#MAX_NC}); empty for none
 * @param le   Ne, {@code 0} or {@link SmartCardSession#NO_LE}
 */
public record APDUCommand(int cla, int ins, int p1, int p2, byte[] data, int le) {

    /** SELECT (ISO/IEC 7816-4:2005 7.1.1). */
    private static final int INS_SELECT = 0xA4;
    /** SELECT P1: selection by DF name (ISO/IEC 7816-4:2005 Table 39). */
    private static final int P1_SELECT_BY_NAME = 0x04;
    /** Ne of a SELECT: 256, sent as the short Le '00'. */
    private static final int SELECT_NE = 256;

    /**
     * Checks the values and keeps unsigned header bytes and a copy of the data.
     *
     * @param cla  the class byte, {@code -128} to {@code 0xFF}
     * @param ins  the instruction byte, {@code -128} to {@code 0xFF}
     * @param p1   P1, {@code -128} to {@code 0xFF}
     * @param p2   P2, {@code -128} to {@code 0xFF}
     * @param data the command data, or {@code null} for none
     * @param le   Ne, {@code 0} or {@link SmartCardSession#NO_LE}
     * @throws IllegalArgumentException if a value is out of range
     */
    public APDUCommand {
        cla = APDUCodec.headerByte("CLA", cla);
        ins = APDUCodec.headerByte("INS", ins);
        p1 = APDUCodec.headerByte("P1", p1);
        p2 = APDUCodec.headerByte("P2", p2);
        data = data == null ? new byte[0] : data.clone();
        if (data.length > APDUCodec.MAX_NC) {
            throw new IllegalArgumentException("Command data too long: " + data.length + " bytes (Nc must not exceed "
                    + APDUCodec.MAX_NC + ", ISO/IEC 7816-4:2005 5.1)");
        }
        if (le < SmartCardSession.NO_LE || le > APDUCodec.MAX_NE) {
            throw new IllegalArgumentException("le must be SmartCardSession.NO_LE (-1) or 0.." + APDUCodec.MAX_NE
                    + " (ISO/IEC 7816-4:2005 5.1), got: " + le);
        }
    }

    /**
     * Returns a command with CLA and INS, P1 = P2 = {@code 00}, no data and no Le field (case 1).
     *
     * @param cla the class byte, {@code 0x00} to {@code 0xFF} or a {@code byte} constant
     * @param ins the instruction byte, {@code 0x00} to {@code 0xFF} or a {@code byte} constant
     * @return the command
     */
    public static APDUCommand of(int cla, int ins) {
        return new APDUCommand(cla, ins, 0, 0, null, SmartCardSession.NO_LE);
    }

    /**
     * Returns a command with a header, no data and no Le field (case 1).
     *
     * @param cla the class byte
     * @param ins the instruction byte
     * @param p1  P1
     * @param p2  P2
     * @return the command
     */
    public static APDUCommand of(int cla, int ins, int p1, int p2) {
        return new APDUCommand(cla, ins, p1, p2, null, SmartCardSession.NO_LE);
    }

    /**
     * Returns the SELECT command of an applet: SELECT by DF name ({@code 00 A4 04 00 Lc AID 00}; ISO/IEC 7816-4:2005
     * 7.1.1, P1 '04' selection by DF name, P2 '00' first or only occurrence with the FCI returned), with Le
     * {@code '00'} (Ne 256) so that the answer to SELECT comes back and a test can assert it:
     *
     * <pre>
     * assertThat(card.send(APDUCommand.select(card.aid(WalletApplet.class))))
     *         .isSuccess().tlv().tag(0x6F).tag(0x84).hasValue(card.aid(WalletApplet.class).toBytes());
     * </pre>
     *
     * <p>It is the command the sessions send for {@link SmartCardSession#select(AID)}. The card of a
     * {@link name.velikodniy.jcexpress.JavaCardTest} class takes note of a successful SELECT sent this way, so the
     * applet counts as selected before the next test.</p>
     *
     * @param aid the AID of the applet (an instance AID; a shorter AID selects by partial name)
     * @return the command
     * @throws IllegalArgumentException if {@code aid} is null
     */
    public static APDUCommand select(AID aid) {
        if (aid == null) {
            throw new IllegalArgumentException("The AID of a SELECT command must not be null");
        }
        return new APDUCommand(0x00, INS_SELECT, P1_SELECT_BY_NAME, 0x00, aid.toBytes(), SELECT_NE);
    }

    /**
     * Parses a command APDU written as hex, as in a specification or a transcript; spaces are ignored. All cases of
     * ISO/IEC 7816-4:2005 5.1 are read: case 1, and cases 2, 3 and 4 with short or extended length fields (an Le of
     * {@code '00'} is Ne 256, an extended Le of {@code '0000'} Ne 65536).
     *
     * @param apdu the command as hex, e.g. {@code "80 30 00 00 02 0064"}
     * @return the command
     * @throws IllegalArgumentException if the text is not hex or the length fields do not match the bytes
     */
    public static APDUCommand fromHex(String apdu) {
        return CommandDecoder.decode(Hex.decode(apdu));
    }

    /**
     * Returns this command with another P1.
     *
     * @param p1 P1, {@code 0x00} to {@code 0xFF} or a {@code byte} constant
     * @return a new command
     */
    public APDUCommand p1(int p1) {
        return new APDUCommand(cla, ins, p1, p2, data, le);
    }

    /**
     * Returns this command with another P2.
     *
     * @param p2 P2, {@code 0x00} to {@code 0xFF} or a {@code byte} constant
     * @return a new command
     */
    public APDUCommand p2(int p2) {
        return new APDUCommand(cla, ins, p1, p2, data, le);
    }

    /**
     * Returns this command with other P1 and P2.
     *
     * @param p1 P1
     * @param p2 P2
     * @return a new command
     */
    public APDUCommand p1p2(int p1, int p2) {
        return new APDUCommand(cla, ins, p1, p2, data, le);
    }

    /**
     * Returns this command with other data.
     *
     * @param data the command data (copied), or {@code null} for none
     * @return a new command
     */
    public APDUCommand data(byte[] data) {
        return new APDUCommand(cla, ins, p1, p2, data, le);
    }

    /**
     * Returns this command with other data, given byte by byte: {@code data(0x00, 0x64)}.
     *
     * @param bytes the data bytes, each {@code 0x00} to {@code 0xFF} or a {@code byte} value
     * @return a new command
     * @throws IllegalArgumentException if a value is not a byte
     */
    public APDUCommand data(int... bytes) {
        byte[] values = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            values[i] = (byte) APDUCodec.headerByte("Data byte " + i, bytes[i]);
        }
        return data(values);
    }

    /**
     * Returns this command with other data, written as hex (spaces are ignored).
     *
     * @param hex the command data as hex, e.g. {@code "A0 00 00 00 62"}
     * @return a new command
     * @throws IllegalArgumentException if the text is not hex
     */
    public APDUCommand dataHex(String hex) {
        return data(Hex.decode(hex));
    }

    /**
     * Returns this command with another Le.
     *
     * @param le Ne ({@code 1} to {@code 65536}), {@code 0} for an Le field of {@code '00'} bytes, or
     *           {@link SmartCardSession#NO_LE}
     * @return a new command
     */
    public APDUCommand le(int le) {
        return new APDUCommand(cla, ins, p1, p2, data, le);
    }

    /**
     * Returns this command without Le field.
     *
     * @return a new command with {@link SmartCardSession#NO_LE}
     */
    public APDUCommand noLe() {
        return le(SmartCardSession.NO_LE);
    }

    /**
     * Returns the command data.
     *
     * @return a copy of the data; empty for none
     */
    @Override
    public byte[] data() {
        return data.clone();
    }

    /**
     * Encodes the command as every session does
     * ({@link APDUCodec#encode(int, int, int, int, byte[], int)}).
     *
     * @return the command APDU bytes
     */
    public byte[] toBytes() {
        return APDUCodec.encode(cla, ins, p1, p2, data, le);
    }

    /**
     * Compares header, data (by content) and Le.
     *
     * @param other the object to compare with
     * @return true for a command with the same values
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof APDUCommand that && cla == that.cla && ins == that.ins && p1 == that.p1
                && p2 == that.p2 && le == that.le && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * (31 * (31 * cla + ins) + p1) + p2) + le) + Arrays.hashCode(data);
    }

    /**
     * Returns the command as hex, upper case without spaces, as the {@code C:} lines of transcripts show it.
     *
     * @return the hex of {@link #toBytes()}
     */
    @Override
    public String toString() {
        return Hex.encode(toBytes());
    }
}
