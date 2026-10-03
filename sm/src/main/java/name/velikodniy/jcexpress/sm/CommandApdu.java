package name.velikodniy.jcexpress.sm;

import name.velikodniy.jcexpress.Hex;

import java.util.Arrays;

/**
 * An unprotected command APDU split into its fields.
 *
 * <p>Parsing follows the four cases of ISO/IEC 7816-4 (5.1), in short and in extended form:</p>
 * <pre>
 * Case 1:   CLA INS P1 P2
 * Case 2S:  CLA INS P1 P2 Le                         Le '00' means Ne = 256
 * Case 3S:  CLA INS P1 P2 Lc Data                    Lc '01'..'FF'
 * Case 4S:  CLA INS P1 P2 Lc Data Le
 * Case 2E:  CLA INS P1 P2 '00' Le1 Le2               Le '0000' means Ne = 65536
 * Case 3E:  CLA INS P1 P2 '00' Lc1 Lc2 Data          Lc '0001'..'FFFF'
 * Case 4E:  CLA INS P1 P2 '00' Lc1 Lc2 Data Le1 Le2
 * </pre>
 * <p>Any other byte sequence is malformed and rejected, so that Secure Messaging never protects a command whose data
 * or expected length it would silently drop.</p>
 *
 * @param cla  the class byte
 * @param ins  the instruction byte
 * @param p1   parameter byte 1
 * @param p2   parameter byte 2
 * @param data the command data field (empty for cases 1 and 2)
 * @param ne   the maximum number of response bytes expected (Ne), or 0 when the Le field is absent
 */
record CommandApdu(int cla, int ins, int p1, int p2, byte[] data, int ne) {

    private static final int HEADER = 4;

    /**
     * Parses a command APDU.
     *
     * @param apdu the encoded command APDU
     * @return the parsed fields
     * @throws SMException if the APDU is not a well-formed case 1-4 command
     */
    static CommandApdu parse(byte[] apdu) {
        if (apdu == null || apdu.length < HEADER) {
            throw malformed(apdu, "an APDU needs at least the 4 header bytes CLA INS P1 P2");
        }
        if (apdu.length == HEADER) {
            return of(apdu, new byte[0], 0);
        }
        if (apdu.length == HEADER + 1) {
            return of(apdu, new byte[0], shortLe(apdu[HEADER]));
        }
        return (apdu[HEADER] & 0xFF) != 0 ? parseShort(apdu) : parseExtended(apdu);
    }

    private static CommandApdu parseShort(byte[] apdu) {
        int lc = apdu[HEADER] & 0xFF;
        int dataEnd = HEADER + 1 + lc;
        if (apdu.length == dataEnd) {
            return of(apdu, Arrays.copyOfRange(apdu, HEADER + 1, dataEnd), 0);
        }
        if (apdu.length == dataEnd + 1) {
            return of(apdu, Arrays.copyOfRange(apdu, HEADER + 1, dataEnd), shortLe(apdu[dataEnd]));
        }
        throw malformed(apdu, "short Lc = " + lc + " does not match the APDU length " + apdu.length);
    }

    private static CommandApdu parseExtended(byte[] apdu) {
        if (apdu.length < HEADER + 3) {
            throw malformed(apdu, "'00' after the header starts an extended length field of 3 bytes");
        }
        if (apdu.length == HEADER + 3) {
            return of(apdu, new byte[0], extendedLe(apdu, HEADER + 1));
        }
        int lc = u16(apdu, HEADER + 1);
        int dataEnd = HEADER + 3 + lc;
        if (lc == 0) {
            throw malformed(apdu, "extended Lc must not be '0000'");
        }
        if (apdu.length == dataEnd) {
            return of(apdu, Arrays.copyOfRange(apdu, HEADER + 3, dataEnd), 0);
        }
        if (apdu.length == dataEnd + 2) {
            return of(apdu, Arrays.copyOfRange(apdu, HEADER + 3, dataEnd), extendedLe(apdu, dataEnd));
        }
        throw malformed(apdu, "extended Lc = " + lc + " does not match the APDU length " + apdu.length);
    }

    private static CommandApdu of(byte[] apdu, byte[] data, int ne) {
        return new CommandApdu(apdu[0] & 0xFF, apdu[1] & 0xFF, apdu[2] & 0xFF, apdu[3] & 0xFF, data, ne);
    }

    private static int shortLe(byte le) {
        return le == 0 ? 256 : le & 0xFF;
    }

    private static int extendedLe(byte[] apdu, int offset) {
        int le = u16(apdu, offset);
        return le == 0 ? 65536 : le;
    }

    private static int u16(byte[] apdu, int offset) {
        return ((apdu[offset] & 0xFF) << 8) | (apdu[offset + 1] & 0xFF);
    }

    private static SMException malformed(byte[] apdu, String reason) {
        String hex = apdu == null ? "null" : Hex.encode(apdu);
        return new SMException("Malformed command APDU " + hex + " (ISO/IEC 7816-4 5.1): " + reason);
    }
}
