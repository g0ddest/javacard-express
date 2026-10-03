package name.velikodniy.jcexpress.apdu;

import name.velikodniy.jcexpress.SmartCardSession;

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

    /** Largest Nc (data field length) of a command APDU, ISO/IEC 7816-4:2005 5.1. */
    public static final int MAX_NC = 65_535;

    /** Largest Ne (expected response length) of a command APDU, ISO/IEC 7816-4:2005 5.1. */
    public static final int MAX_NE = 65_536;

    private APDUCodec() {
    }

    /**
     * Encodes an APDU command, automatically choosing short or extended format.
     *
     * <p>Extended format is used when {@code data.length > 255} or {@code le > 256}.
     * Otherwise short format is used.</p>
     *
     * <p>{@code le} follows the contract of
     * {@link SmartCardSession#send(int, int, int, int, byte[], int)}: {@link SmartCardSession#NO_LE}
     * ({@code -1}) omits the Le field, {@code 1..65536} is Ne ({@code 256} becomes the short Le
     * {@code '00'}, {@code 65536} the extended Le {@code '0000'}), and {@code 0} produces an Le field with
     * all bytes set to {@code '00'} ("maximum", ISO/IEC 7816-4:2005 5.1).</p>
     *
     * <p>Header bytes are {@code 0x00} to {@code 0xFF}, or the {@code byte} constants of an applet:
     * {@code (byte) 0x80} reaches an {@code int} parameter as {@code -128} and is encoded as {@code '80'}.</p>
     *
     * @param cla  the CLA byte ({@code -128} to {@code 0xFF})
     * @param ins  the INS byte ({@code -128} to {@code 0xFF})
     * @param p1   the P1 byte ({@code -128} to {@code 0xFF})
     * @param p2   the P2 byte ({@code -128} to {@code 0xFF})
     * @param data the command data (may be null or empty; at most {@value #MAX_NC} bytes)
     * @param le   the expected response length: {@code -1} for no Le, {@code 0..65536} otherwise
     * @return the encoded APDU bytes
     * @throws IllegalArgumentException if a header byte, the data length or {@code le} is out of range
     */
    public static byte[] encode(int cla, int ins, int p1, int p2,
                                byte[] data, int le) {
        int[] header = {headerByte("CLA", cla), headerByte("INS", ins), headerByte("P1", p1), headerByte("P2", p2)};
        if (data != null && data.length > MAX_NC) {
            throw new IllegalArgumentException("Command data too long: " + data.length
                    + " bytes (Nc must not exceed " + MAX_NC + ", ISO/IEC 7816-4:2005 5.1)");
        }
        if (le < SmartCardSession.NO_LE || le > MAX_NE) {
            throw new IllegalArgumentException("le must be SmartCardSession.NO_LE (-1) or 0.." + MAX_NE
                    + " (ISO/IEC 7816-4:2005 5.1), got: " + le);
        }

        boolean hasData = data != null && data.length > 0;
        boolean hasLe = le >= 0;
        boolean extended = (hasData && data.length > 255) || (hasLe && le > 256);

        if (extended) {
            return encodeExtended(header[0], header[1], header[2], header[3], data, le, hasData, hasLe);
        }
        return encodeShort(header[0], header[1], header[2], header[3], data, le, hasData, hasLe);
    }

    /**
     * A byte of a command given as an unsigned value or as a Java {@code byte} (an applet's constant), as the
     * unsigned value.
     *
     * @param name  the field, for the message
     * @param value {@code -128} to {@code 0xFF}
     * @return {@code value & 0xFF}
     * @throws IllegalArgumentException if {@code value} is outside {@code -128} to {@code 0xFF}
     */
    static int headerByte(String name, int value) {
        if (value < Byte.MIN_VALUE || value > 0xFF) {
            throw new IllegalArgumentException(name + " must be a byte: 0x00 to 0xFF, or a byte constant from -128;"
                    + " got " + value + (value > 0 ? String.format(" (0x%X)", value) : ""));
        }
        return value & 0xFF;
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
     * <p>ISO/IEC 7816-4:2005 5.1.3: after {@code '6CXX'} "the same command may be re-issued using SW2 (exact
     * number of available data bytes) as short Le field". SW2 is therefore read like a short Le field, i.e.
     * {@code '00'} means 256 bytes. A short APDU gets that byte as its Le field; an extended APDU, whose Le
     * field must be extended as well, gets Ne coded on two bytes ({@code 256} becomes {@code '0100'}).</p>
     *
     * @param apdu      the original APDU bytes
     * @param correctLe SW2 of the '6CXX' response (0-255, where 0 means 256), or 256
     * @return the corrected APDU bytes
     * @throws IllegalArgumentException if {@code correctLe} is outside 0-256
     */
    public static byte[] correctLe(byte[] apdu, int correctLe) {
        if (correctLe < 0 || correctLe > 256) {
            throw new IllegalArgumentException("Corrected Le must be SW2 (0-255, '00' = 256) or 256, got: "
                    + correctLe);
        }
        int ne = correctLe == 0 ? 256 : correctLe;
        if (isExtended(apdu)) {
            return correctLeExtended(apdu, ne);
        }
        return correctLeShort(apdu, ne);
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

    /** Extended APDU: Ne (1-256 here) is written as the two-byte Le field, e.g. 256 as '0100'. */
    private static byte[] correctLeExtended(byte[] apdu, int ne) {
        // After header (4 bytes) + marker (1 byte):
        // Case 2E: [0x00 Le_hi Le_lo] -> length == 7, no data
        // Case 3E: [0x00 Lc_hi Lc_lo Data] -> no Le
        // Case 4E: [0x00 Lc_hi Lc_lo Data Le_hi Le_lo] -> has Le
        byte[] corrected;
        if (apdu.length == 7) {
            corrected = Arrays.copyOf(apdu, apdu.length);
        } else {
            int lc = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
            boolean hasLe = apdu.length == 7 + lc + 2;
            corrected = Arrays.copyOf(apdu, hasLe ? apdu.length : apdu.length + 2);
        }
        corrected[corrected.length - 2] = (byte) ((ne >> 8) & 0xFF);
        corrected[corrected.length - 1] = (byte) (ne & 0xFF);
        return corrected;
    }
}
