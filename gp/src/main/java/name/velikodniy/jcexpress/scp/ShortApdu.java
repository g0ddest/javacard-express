package name.velikodniy.jcexpress.scp;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * A parsed ISO/IEC 7816-4 short command APDU (case 1, 2S, 3S or 4S).
 *
 * <p>GlobalPlatform commands, and therefore SCP02/SCP03 secure messaging, use short lengths only:
 * "All GlobalPlatform APDU commands use ISO/IEC 7816 short message lengths" and "All GlobalPlatform
 * APDU command messages (excluding the APDU header) are limited to 255 bytes in length"
 * (GPCS v2.3.1 section 11.1.5). Extended or malformed APDUs are rejected instead of being silently
 * truncated.</p>
 *
 * @param cla  the class byte
 * @param ins  the instruction byte
 * @param p1   parameter 1
 * @param p2   parameter 2
 * @param data the command data (empty for case 1 and 2)
 * @param le   the Le byte (0 codes 256), or {@link #NO_LE} when absent (case 1 and 3)
 */
record ShortApdu(int cla, int ins, int p1, int p2, byte[] data, int le) {

    /** Marker for an absent Le field. */
    static final int NO_LE = -1;

    /** Maximum length of a short command data field (ISO/IEC 7816-4, GPCS 11.1.5). */
    static final int MAX_LC = 255;

    /**
     * Parses a short command APDU.
     *
     * @param apdu the encoded command
     * @return the parsed command
     * @throws SCPException if the APDU is shorter than 4 bytes, uses extended lengths or its Lc
     *                      does not match its length
     */
    static ShortApdu parse(byte[] apdu) {
        if (apdu == null || apdu.length < 4) {
            throw new SCPException("APDU too short: " + (apdu == null ? 0 : apdu.length) + " bytes (minimum 4)");
        }
        int cla = apdu[0] & 0xFF;
        int ins = apdu[1] & 0xFF;
        int p1 = apdu[2] & 0xFF;
        int p2 = apdu[3] & 0xFF;
        if (apdu.length <= 5) {
            int le = apdu.length == 5 ? apdu[4] & 0xFF : NO_LE;
            return new ShortApdu(cla, ins, p1, p2, new byte[0], le);
        }
        int lc = apdu[4] & 0xFF;
        if (lc == 0) {
            throw new SCPException("Extended-length APDUs are not supported by GlobalPlatform secure messaging"
                    + " (GPCS v2.3.1 11.1.5: short lengths only)");
        }
        if (apdu.length != 5 + lc && apdu.length != 6 + lc) {
            throw new SCPException("Malformed APDU: Lc=" + lc + " but " + (apdu.length - 5)
                    + " bytes follow the header");
        }
        int le = apdu.length == 6 + lc ? apdu[5 + lc] & 0xFF : NO_LE;
        return new ShortApdu(cla, ins, p1, p2, Arrays.copyOfRange(apdu, 5, 5 + lc), le);
    }

    /**
     * Returns true if the command carries an Le field.
     *
     * @return true for case 2 and case 4 commands
     */
    boolean hasLe() {
        return le != NO_LE;
    }

    /**
     * Returns true if the class byte uses the further interindustry coding (logical channels 4 to 19,
     * GPCS v2.3.1 Table 11-12).
     */
    private boolean furtherInterindustry() {
        return (cla & 0x40) != 0;
    }

    /**
     * Returns the class byte used in the C-MAC computation: logical channel number set to zero, b4 = 0 and
     * b3 = 1 to indicate GlobalPlatform proprietary secure messaging (GPCS v2.3.1 E.4.4, Amd D 6.2.4).
     *
     * @return the class byte that is MACed
     */
    int macClassByte() {
        int chaining = cla & 0x10;
        return (cla & 0x80) | chaining | 0x04;
    }

    /**
     * Returns the class byte sent on the wire: the logical channel number is restored after the C-MAC
     * computation; for channels 4 to 19 secure messaging is indicated by b6 (GPCS v2.3.1 E.4.4,
     * Tables 11-11 and 11-12, Amd D 6.2.4).
     *
     * @return the class byte of the wrapped command
     */
    int wireClassByte() {
        return furtherInterindustry() ? cla | 0x20 : macClassByte() | (cla & 0x03);
    }

    /**
     * Returns the class byte of the "stripped" command used in the SCP02 R-MAC computation: no secure
     * messaging indication and logical channel number zero (GPCS v2.3.1 E.4.5).
     *
     * @return the class byte without secure messaging and channel information
     */
    int strippedClassByte() {
        return macClassByte() & ~0x04;
    }

    /**
     * Encodes this command unchanged.
     *
     * @return the encoded APDU
     */
    byte[] encode() {
        return encode(cla, data, new byte[0]);
    }

    /**
     * Encodes this command with a new class byte, a new data field and an appended trailer (C-MAC).
     *
     * @param newCla  the class byte to use
     * @param body    the (possibly encrypted) data field
     * @param trailer bytes appended after the data field, e.g. the C-MAC (may be empty)
     * @return the encoded APDU, Le preserved
     * @throws SCPException if the resulting data field exceeds 255 bytes (GPCS 11.1.5)
     */
    byte[] encode(int newCla, byte[] body, byte[] trailer) {
        int lc = body.length + trailer.length;
        if (lc > MAX_LC) {
            throw new SCPException("Command data field of " + lc + " bytes exceeds the 255-byte short APDU limit"
                    + " (GPCS v2.3.1 11.1.5)");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(6 + lc);
        out.write(newCla);
        out.write(ins);
        out.write(p1);
        out.write(p2);
        if (lc > 0) {
            out.write(lc);
            out.writeBytes(body);
            out.writeBytes(trailer);
        }
        if (hasLe()) {
            out.write(le);
        }
        return out.toByteArray();
    }

    /**
     * Encodes the header and data that are MACed: {@code macClass INS P1 P2 Lc data}, where Lc already
     * includes the length of the C-MAC (GPCS v2.3.1 E.4.4 "C-MAC on modified APDU", Amd D 6.2.4).
     *
     * @param body      the data field as transmitted (clear for SCP02, encrypted for SCP03)
     * @param macLength the length of the C-MAC appended to the data field
     * @return the MAC input without padding or chaining value
     */
    byte[] macInput(byte[] body, int macLength) {
        int lc = body.length + macLength;
        if (lc > MAX_LC) {
            throw new SCPException("Wrapped command data field of " + lc + " bytes exceeds the 255-byte short APDU"
                    + " limit (GPCS v2.3.1 11.1.5); send at most the secure channel's maxCommandDataLength()");
        }
        byte[] input = new byte[5 + body.length];
        input[0] = (byte) macClassByte();
        input[1] = (byte) ins;
        input[2] = (byte) p1;
        input[3] = (byte) p2;
        input[4] = (byte) lc;
        System.arraycopy(body, 0, input, 5, body.length);
        return input;
    }
}
