package name.velikodniy.jcexpress.apdu;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Low-level codec for ISO 7816-4 APDU encoding and parsing.
 *
 * <p>Supports both short APDUs (Lc up to 255, Le up to 256) and extended APDUs
 * (Lc up to 65535, Le up to 65536). The encoding format is chosen automatically
 * based on the data length and Le value.</p>
 *
 * <h2>Short APDU format:</h2>
 * <pre>
 * Case 1:  CLA INS P1 P2
 * Case 2S: CLA INS P1 P2 Le(1)
 * Case 3S: CLA INS P1 P2 Lc(1) Data(1-255)
 * Case 4S: CLA INS P1 P2 Lc(1) Data(1-255) Le(1)
 * </pre>
 *
 * <h2>Extended APDU format:</h2>
 * <pre>
 * Case 2E: CLA INS P1 P2 0x00 Le_hi Le_lo
 * Case 3E: CLA INS P1 P2 0x00 Lc_hi Lc_lo Data(1-65535)
 * Case 4E: CLA INS P1 P2 0x00 Lc_hi Lc_lo Data(1-65535) Le_hi Le_lo
 * </pre>
 */
public final class APDUCodec {

    private APDUCodec() {
    }

    /**
     * Encodes an APDU command, automatically choosing short or extended format.
     *
     * <p>Extended format is used when {@code data.length > 255} or {@code le > 256}.
     * Otherwise short format is used for backward compatibility.</p>
     *
     * @param cla  the CLA byte
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data (may be null or empty)
     * @param le   the expected response length (-1 for no Le, 0-256 for short,
     *             0-65536 for extended; 0 means max for the format)
     * @return the encoded APDU bytes
     */
    public static byte[] encode(int cla, int ins, int p1, int p2,
                                byte[] data, int le) {
        if (cla < 0 || cla > 0xFF) throw new IllegalArgumentException("CLA must be 0x00-0xFF, got: 0x" + Integer.toHexString(cla));
        if (ins < 0 || ins > 0xFF) throw new IllegalArgumentException("INS must be 0x00-0xFF, got: 0x" + Integer.toHexString(ins));
        if (p1 < 0 || p1 > 0xFF)  throw new IllegalArgumentException("P1 must be 0x00-0xFF, got: 0x" + Integer.toHexString(p1));
        if (p2 < 0 || p2 > 0xFF)  throw new IllegalArgumentException("P2 must be 0x00-0xFF, got: 0x" + Integer.toHexString(p2));

        boolean hasData = data != null && data.length > 0;
        boolean hasLe = le >= 0;
        boolean extended = (hasData && data.length > 255) || (hasLe && le > 256);

        if (extended) {
            return encodeExtended(cla, ins, p1, p2, data, le, hasData, hasLe);
        }
        return encodeShort(cla, ins, p1, p2, data, le, hasData, hasLe);
    }

    /**
     * Returns {@code true} if the raw APDU uses extended length encoding.
     *
     * <p>Detection is based on the presence of a zero byte at position 4
     * (the extended length marker) combined with the total APDU length
     * being consistent with an extended format structure.</p>
     *
     * @param apdu the raw APDU bytes
     * @return true if extended format
     */
    public static boolean isExtended(byte[] apdu) {
        if (apdu == null || apdu.length < 7) {
            return false;
        }
        // Extended marker: byte at position 4 is 0x00 and total length >= 7
        return (apdu[4] & 0xFF) == 0x00;
    }

    /**
     * Replaces or appends Le in an existing APDU (for 6CXX Le correction).
     *
     * <p>Handles both short and extended APDU formats. For short APDUs,
     * Le is encoded as a single byte. For extended APDUs, Le is encoded
     * as two bytes.</p>
     *
     * @param apdu      the original APDU bytes
     * @param correctLe the corrected Le value from SW2 (0-255, where 0 means 256)
     * @return the corrected APDU bytes
     */
    public static byte[] correctLe(byte[] apdu, int correctLe) {
        if (isExtended(apdu)) {
            return correctLeExtended(apdu, correctLe);
        }
        return correctLeShort(apdu, correctLe);
    }

    // ── Short format ──

    private static byte[] encodeShort(int cla, int ins, int p1, int p2,
                                      byte[] data, int le,
                                      boolean hasData, boolean hasLe) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(5 + (hasData ? data.length : 0));
        out.write(cla);
        out.write(ins);
        out.write(p1);
        out.write(p2);

        if (hasData) {
            out.write(data.length); // Lc (1 byte)
            out.write(data, 0, data.length);
        }

        if (hasLe) {
            out.write(le == 256 ? 0 : le); // Le=256 encoded as 0x00
        }

        return out.toByteArray();
    }

    // ── Extended format ──

    private static byte[] encodeExtended(int cla, int ins, int p1, int p2,
                                         byte[] data, int le,
                                         boolean hasData, boolean hasLe) {
        int size = 5 + (hasData ? 2 + data.length : 0) + (hasLe ? 2 : 0);
        ByteArrayOutputStream out = new ByteArrayOutputStream(size);
        out.write(cla);
        out.write(ins);
        out.write(p1);
        out.write(p2);

        out.write(0x00); // Extended length marker

        if (hasData) {
            out.write((data.length >> 8) & 0xFF); // Lc high
            out.write(data.length & 0xFF);        // Lc low
            out.write(data, 0, data.length);
        }

        if (hasLe) {
            int encodedLe = (le == 65536) ? 0 : le;
            if (!hasData && le <= 256) {
                // Case 2E with small Le: still use 2-byte encoding
                encodedLe = (le == 256) ? 0 : le;
            }
            out.write((encodedLe >> 8) & 0xFF); // Le high
            out.write(encodedLe & 0xFF);        // Le low
        }

        return out.toByteArray();
    }

    // ── Le correction ──

    private static byte[] correctLeShort(byte[] apdu, int correctLe) {
        byte[] corrected = Arrays.copyOf(apdu, apdu.length);
        byte leValue = (byte) (correctLe == 256 ? 0 : correctLe);

        if (apdu.length == 4) {
            // Case 1: CLA INS P1 P2 — append Le
            corrected = Arrays.copyOf(apdu, apdu.length + 1);
            corrected[4] = leValue;
        } else if (apdu.length == 5) {
            // Case 2: CLA INS P1 P2 Le — replace Le
            corrected[4] = leValue;
        } else {
            int lc = apdu[4] & 0xFF;
            if (apdu.length == 5 + lc + 1) {
                // Case 4: CLA INS P1 P2 Lc Data Le — replace last byte
                corrected[corrected.length - 1] = leValue;
            } else {
                // Case 3: CLA INS P1 P2 Lc Data — append Le
                corrected = Arrays.copyOf(apdu, apdu.length + 1);
                corrected[corrected.length - 1] = leValue;
            }
        }
        return corrected;
    }

    private static byte[] correctLeExtended(byte[] apdu, int correctLe) {
        // Parse extended APDU structure
        // After header (4 bytes) + marker (1 byte):
        // Case 2E: [0x00 Le_hi Le_lo] → length == 7, no data
        // Case 3E: [0x00 Lc_hi Lc_lo Data] → no Le
        // Case 4E: [0x00 Lc_hi Lc_lo Data Le_hi Le_lo] → has Le

        if (apdu.length == 7) {
            // Case 2E: replace Le
            byte[] corrected = Arrays.copyOf(apdu, apdu.length);
            int encodedLe = correctLe == 65536 ? 0 : correctLe;
            corrected[5] = (byte) ((encodedLe >> 8) & 0xFF);
            corrected[6] = (byte) (encodedLe & 0xFF);
            return corrected;
        }

        // Case 3E or 4E: parse Lc
        int lcHi = apdu[5] & 0xFF;
        int lcLo = apdu[6] & 0xFF;
        int lc = (lcHi << 8) | lcLo;
        int dataEnd = 7 + lc;

        int encodedLe = correctLe == 65536 ? 0 : correctLe;

        if (apdu.length == dataEnd + 2) {
            // Case 4E: replace Le (last 2 bytes)
            byte[] corrected = Arrays.copyOf(apdu, apdu.length);
            corrected[corrected.length - 2] = (byte) ((encodedLe >> 8) & 0xFF);
            corrected[corrected.length - 1] = (byte) (encodedLe & 0xFF);
            return corrected;
        } else {
            // Case 3E: append Le (2 bytes)
            byte[] corrected = Arrays.copyOf(apdu, apdu.length + 2);
            corrected[corrected.length - 2] = (byte) ((encodedLe >> 8) & 0xFF);
            corrected[corrected.length - 1] = (byte) (encodedLe & 0xFF);
            return corrected;
        }
    }
}
