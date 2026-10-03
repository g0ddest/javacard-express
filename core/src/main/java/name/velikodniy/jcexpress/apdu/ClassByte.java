package name.velikodniy.jcexpress.apdu;

/**
 * Logical channel coding of the class byte (CLA) of a command APDU.
 *
 * <ul>
 *   <li><b>Interindustry class</b> (ISO/IEC 7816-4:2005 5.1.1): the first interindustry values
 *       {@code 000x xxxx} (Table 2) code channels 0 to 3 in b2-b1 and secure messaging in b4-b3; the
 *       further interindustry values {@code 01xx xxxx} (Table 3) code channels 4 to 19 as b4-b1 = channel - 4
 *       and secure messaging in b6. b5 is the command chaining bit in both codings.</li>
 *   <li><b>Proprietary class</b> as coded by GlobalPlatform (Card Specification 2.3.1 11.1.4, Tables 11-11
 *       and 11-12): the same layouts with b8 = 1, i.e. {@code 100x xxxx} for channels 0 to 3 (GlobalPlatform
 *       secure messaging b4-b3 = 01) and {@code 11xx xxxx} for channels 4 to 19 (secure messaging b6 = 1).</li>
 *   <li>Values that code no logical channel and therefore refer to the basic channel: {@code 'FF'} (invalid,
 *       ISO/IEC 7816-3), {@code 001x xxxx} (reserved for future use, ISO/IEC 7816-4:2005 5.1.1) and the
 *       proprietary values {@code 101x xxxx}, which GlobalPlatform does not define.</li>
 * </ul>
 *
 * <p>Moving a command between the two codings keeps the class (b8), the chaining bit (b5) and the
 * presence of secure messaging. Table 3 has a single secure messaging indication (b6 = 1, "SM according to
 * clause 6, command header not processed"), which corresponds to b4-b3 = 10 of Table 2 in the
 * interindustry class and to the GlobalPlatform indication b4-b3 = 01 in the proprietary class.</p>
 */
public final class ClassByte {

    /** Highest logical channel number (ISO/IEC 7816-4:2005 5.1.1.2 and 7.1.2). */
    public static final int MAX_CHANNEL = 19;

    private static final int PROPRIETARY = 0x80;
    private static final int FURTHER = 0x40;
    private static final int B6 = 0x20;
    private static final int CHAINING = 0x10;
    private static final int FIRST_SM = 0x0C;
    private static final int ISO_SM_HEADER_NOT_PROCESSED = 0x08;
    private static final int GP_SM = 0x04;
    private static final int INVALID = 0xFF;

    private ClassByte() {
    }

    /**
     * Returns whether the class byte codes a logical channel number (first or further interindustry
     * values, or the GlobalPlatform proprietary values).
     *
     * @param cla the class byte (0x00-0xFF, or a {@code byte} constant)
     * @return false for {@code 'FF'}, {@code 001x xxxx} and {@code 101x xxxx}
     * @throws IllegalArgumentException if {@code cla} is not a byte value
     */
    public static boolean codesChannel(int cla) {
        int value = checkByte(cla);
        return value != INVALID && (value & (FURTHER | B6)) != B6;
    }

    /**
     * Returns whether the class byte is a first or further interindustry value (ISO/IEC 7816-4:2005 5.1.1:
     * {@code 000x xxxx} or {@code 01xx xxxx}), i.e. the class of commands such as SELECT or MANAGE CHANNEL.
     *
     * @param cla the class byte (0x00-0xFF, or a {@code byte} constant)
     * @return true for interindustry values
     * @throws IllegalArgumentException if {@code cla} is not a byte value
     */
    public static boolean isInterindustry(int cla) {
        int value = checkByte(cla);
        return codesChannel(value) && (value & PROPRIETARY) == 0;
    }

    /**
     * Returns whether the class byte indicates secure messaging: b4-b3 other than {@code 00} in the first
     * interindustry class (ISO/IEC 7816-4:2005 Table 2), b6 = 1 in the further interindustry class (Table 3), and
     * the same bits in the GlobalPlatform proprietary classes (GPCS 2.3.1 Tables 11-11 and 11-12).
     *
     * @param cla the class byte (0x00-0xFF, or a {@code byte} constant)
     * @return false for values that code no channel (see {@link #codesChannel(int)})
     * @throws IllegalArgumentException if {@code cla} is not a byte value
     */
    public static boolean indicatesSecureMessaging(int cla) {
        int value = checkByte(cla);
        return codesChannel(value) && hasSecureMessaging(value);
    }

    /**
     * Returns the logical channel number coded in the class byte.
     *
     * @param cla the class byte (0x00-0xFF, or a {@code byte} constant)
     * @return 0 to {@value #MAX_CHANNEL}; 0 for values that code no channel (see {@link #codesChannel(int)})
     * @throws IllegalArgumentException if {@code cla} is not a byte value
     */
    public static int channel(int cla) {
        int value = checkByte(cla);
        if (!codesChannel(value)) {
            return 0;
        }
        return (value & FURTHER) != 0 ? 4 + (value & 0x0F) : value & 0x03;
    }

    /**
     * Re-codes the class byte for another logical channel, keeping the class, the chaining bit and the
     * secure messaging indication (see the class documentation).
     *
     * @param cla     the class byte (0x00-0xFF, or a {@code byte} constant)
     * @param channel the logical channel number, 0 to {@value #MAX_CHANNEL}
     * @return the class byte for {@code channel}; values that code no channel are returned unchanged for
     *         channel 0
     * @throws IllegalArgumentException if a value is out of range, if {@code cla} codes no channel and
     *                                  {@code channel} is not 0, or if the result would be the invalid value
     *                                  {@code 'FF'}
     */
    public static int withChannel(int cla, int channel) {
        int value = checkByte(cla);
        if (channel < 0 || channel > MAX_CHANNEL) {
            throw new IllegalArgumentException("Logical channel must be 0-" + MAX_CHANNEL
                    + " (ISO/IEC 7816-4:2005 5.1.1.2), got: " + channel);
        }
        if (!codesChannel(value)) {
            if (channel == 0) {
                return value;
            }
            throw new IllegalArgumentException(String.format("CLA '%02X' codes no logical channel"
                    + " (ISO/IEC 7816-4:2005 5.1.1), it cannot address channel %d", value, channel));
        }
        int kept = value & (PROPRIETARY | CHAINING);
        if (channel <= 3) {
            return kept | firstCodingSecureMessaging(value) | channel;
        }
        int encoded = kept | FURTHER | (hasSecureMessaging(value) ? B6 : 0) | (channel - 4);
        if (encoded == INVALID) {
            throw new IllegalArgumentException(String.format("CLA '%02X' on channel %d would be 'FF', which"
                    + " ISO/IEC 7816-3 reserves", value, channel));
        }
        return encoded;
    }

    private static boolean hasSecureMessaging(int cla) {
        return (cla & FURTHER) != 0 ? (cla & B6) != 0 : (cla & FIRST_SM) != 0;
    }

    /** Secure messaging bits b4-b3 of Table 2 / Table 11-11 for a class byte in either coding. */
    private static int firstCodingSecureMessaging(int cla) {
        if ((cla & FURTHER) == 0) {
            return cla & FIRST_SM;
        }
        if ((cla & B6) == 0) {
            return 0;
        }
        return (cla & PROPRIETARY) != 0 ? GP_SM : ISO_SM_HEADER_NOT_PROCESSED;
    }

    /** The class byte as an unsigned value; an applet's {@code byte} constant ({@code (byte) 0x80}) is accepted. */
    private static int checkByte(int cla) {
        return APDUCodec.headerByte("CLA", cla);
    }
}
