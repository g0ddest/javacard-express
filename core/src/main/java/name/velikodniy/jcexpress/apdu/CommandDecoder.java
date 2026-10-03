package name.velikodniy.jcexpress.apdu;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;

import java.util.Arrays;

/**
 * Reads a command APDU into an {@link APDUCommand} after the length fields of ISO/IEC 7816-4:2005 5.1 (Table 1):
 * no body (case 1); one byte, a short Le (case 2S); a short Lc of {@code '01'} to {@code 'FF'} with the data and
 * an optional short Le (cases 3S, 4S); or {@code '00'} followed by a two-byte Le (case 2E) or a two-byte Lc with
 * the data and an optional two-byte Le (cases 3E, 4E). A short Le of {@code '00'} is Ne 256, an extended Le of
 * {@code '0000'} Ne 65536.
 */
final class CommandDecoder {

    private static final int HEADER = 4;

    private CommandDecoder() {
    }

    /**
     * Decodes a command APDU.
     *
     * @param apdu the command bytes
     * @return the command
     * @throws IllegalArgumentException if the length fields do not match the bytes
     */
    static APDUCommand decode(byte[] apdu) {
        if (apdu.length < HEADER) {
            throw malformed(apdu, "a command APDU has at least 4 bytes (CLA INS P1 P2)");
        }
        int body = apdu.length - HEADER;
        if (body == 0) {
            return command(apdu, null, SmartCardSession.NO_LE);
        }
        int first = apdu[HEADER] & 0xFF;
        if (body == 1) {
            return command(apdu, null, first == 0 ? 256 : first);
        }
        return first != 0 ? decodeShort(apdu, first, body) : decodeExtended(apdu, body);
    }

    /** Cases 3S and 4S: Lc, the data, and an optional one-byte Le. */
    private static APDUCommand decodeShort(byte[] apdu, int lc, int body) {
        byte[] data = Arrays.copyOfRange(apdu, HEADER + 1, Math.min(apdu.length, HEADER + 1 + lc));
        if (body == 1 + lc) {
            return command(apdu, data, SmartCardSession.NO_LE);
        }
        if (body == 2 + lc) {
            int le = apdu[apdu.length - 1] & 0xFF;
            return command(apdu, data, le == 0 ? 256 : le);
        }
        throw malformed(apdu, "the short Lc " + lc + " needs " + (1 + lc) + " or " + (2 + lc)
                + " bytes after the header, found " + body);
    }

    /** Cases 2E, 3E and 4E: {@code '00'}, then a two-byte Le, or a two-byte Lc, the data and an optional Le. */
    private static APDUCommand decodeExtended(byte[] apdu, int body) {
        if (body < 3) {
            throw malformed(apdu, "an extended length field is '00' and two bytes, found " + body
                    + " bytes after the header");
        }
        int value = twoBytes(apdu, HEADER + 1);
        if (body == 3) {
            return command(apdu, null, value == 0 ? 65_536 : value);
        }
        if (value == 0) {
            throw malformed(apdu, "the extended Lc is 0000, but a command with data has 1 to 65535 bytes");
        }
        byte[] data = Arrays.copyOfRange(apdu, HEADER + 3, Math.min(apdu.length, HEADER + 3 + value));
        if (body == 3 + value) {
            return command(apdu, data, SmartCardSession.NO_LE);
        }
        if (body == 5 + value) {
            int le = twoBytes(apdu, apdu.length - 2);
            return command(apdu, data, le == 0 ? 65_536 : le);
        }
        throw malformed(apdu, "the extended Lc " + value + " needs " + (3 + value) + " or " + (5 + value)
                + " bytes after the header, found " + body);
    }

    private static APDUCommand command(byte[] apdu, byte[] data, int le) {
        return new APDUCommand(apdu[0], apdu[1], apdu[2], apdu[3], data, le);
    }

    private static int twoBytes(byte[] apdu, int offset) {
        return ((apdu[offset] & 0xFF) << 8) | (apdu[offset + 1] & 0xFF);
    }

    private static IllegalArgumentException malformed(byte[] apdu, String reason) {
        return new IllegalArgumentException("Malformed command APDU " + Hex.encode(apdu) + ": " + reason
                + " (ISO/IEC 7816-4:2005 5.1)");
    }
}
