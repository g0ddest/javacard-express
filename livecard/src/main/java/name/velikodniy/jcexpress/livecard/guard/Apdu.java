package name.velikodniy.jcexpress.livecard.guard;

import java.util.Arrays;
import java.util.HexFormat;

/**
 * A command APDU as the guard sees it: header, data field and the logical channel and secure messaging
 * indication coded in the class byte.
 *
 * <p>Parsing follows ISO/IEC 7816-4:2005 5.1 (cases 1 to 4, short and extended length). The class byte is
 * decoded per ISO/IEC 7816-4:2005 5.1.1 Tables 2 and 3, which GlobalPlatform Card Specification v2.3.1
 * 11.1.4 (Tables 11-11 and 11-12) applies to the proprietary classes '80'-'FF' as well: first interindustry
 * coding (b7 = 0) carries channels 0-3 in b2b1 and secure messaging in b4b3, further interindustry coding
 * (b7 = 1) carries channels 4-19 in b4..b1 and secure messaging in b6.</p>
 *
 * @param cla      the class byte
 * @param ins      the instruction byte
 * @param p1       parameter 1
 * @param p2       parameter 2
 * @param data     the command data field (empty for cases 1 and 2)
 * @param extended whether extended length fields are used
 */
record Apdu(int cla, int ins, int p1, int p2, byte[] data, boolean extended) {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    /** Length of an SCP03 C-MAC in S8 mode (Amendment D 6.2.4). */
    static final int MAC_LENGTH = 8;

    Apdu {
        data = data.clone();
    }

    /**
     * Parses a command APDU.
     *
     * @param apdu the raw command
     * @return the parsed command
     * @throws IllegalArgumentException if the bytes are not a well-formed command APDU
     */
    static Apdu parse(byte[] apdu) {
        if (apdu == null || apdu.length < 4) {
            throw new IllegalArgumentException("a command APDU has at least 4 bytes");
        }
        int cla = apdu[0] & 0xFF;
        int ins = apdu[1] & 0xFF;
        int p1 = apdu[2] & 0xFF;
        int p2 = apdu[3] & 0xFF;
        if (apdu.length == 4 || apdu.length == 5) {
            return new Apdu(cla, ins, p1, p2, new byte[0], false);
        }
        int b5 = apdu[4] & 0xFF;
        if (b5 != 0) {
            if (apdu.length != 5 + b5 && apdu.length != 6 + b5) {
                throw new IllegalArgumentException("short Lc " + b5 + " does not match the length " + apdu.length);
            }
            return new Apdu(cla, ins, p1, p2, Arrays.copyOfRange(apdu, 5, 5 + b5), false);
        }
        return parseExtended(apdu, cla, ins, p1, p2);
    }

    private static Apdu parseExtended(byte[] apdu, int cla, int ins, int p1, int p2) {
        if (apdu.length == 7) {
            return new Apdu(cla, ins, p1, p2, new byte[0], true);
        }
        int lc = apdu.length > 7 ? ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF) : -1;
        if (lc <= 0 || (apdu.length != 7 + lc && apdu.length != 9 + lc)) {
            throw new IllegalArgumentException("malformed extended length command of " + apdu.length + " bytes");
        }
        return new Apdu(cla, ins, p1, p2, Arrays.copyOfRange(apdu, 7, 7 + lc), true);
    }

    /**
     * Returns the logical channel coded in the class byte (0-19).
     *
     * @return the channel number
     */
    int channel() {
        return (cla & 0x40) == 0 ? cla & 0x03 : 4 + (cla & 0x0F);
    }

    /**
     * Returns whether the class byte indicates secure messaging.
     *
     * @return true for first interindustry b4b3 != 00 or further interindustry b6 = 1
     */
    boolean secureMessaging() {
        return (cla & 0x40) == 0 ? (cla & 0x0C) != 0 : (cla & 0x20) != 0;
    }

    /**
     * Returns the command data without the trailing C-MAC of a secure messaging command.
     *
     * @return the data field, minus 8 MAC bytes when secure messaging is indicated
     * @throws IllegalArgumentException if a secure messaging command is too short to carry a C-MAC
     */
    byte[] payload() {
        if (!secureMessaging()) {
            return data.clone();
        }
        if (data.length < MAC_LENGTH) {
            throw new IllegalArgumentException("secure messaging command without C-MAC");
        }
        return Arrays.copyOf(data, data.length - MAC_LENGTH);
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Apdu(int c, int i, int a, int b, byte[] d, boolean e)
                && c == cla && i == ins && a == p1 && b == p2 && e == extended && Arrays.equals(d, data);
    }

    @Override
    public int hashCode() {
        return 31 * ((cla << 24) | (ins << 16) | (p1 << 8) | p2) + Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return String.format("%02X %02X %02X %02X [%s]", cla, ins, p1, p2, HEX.formatHex(data));
    }
}
