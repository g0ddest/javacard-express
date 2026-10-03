package name.velikodniy.jcexpress.livecard.guard;

/**
 * Class byte categories and the selection forms the {@link ApduGuard} relies on.
 *
 * <p>ISO/IEC 7816-4:2005 5.1.1 (Tables 2 and 3): '00'-'1F' first inter-industry coding (b2b1 channel 0-3, b4b3
 * secure messaging, b5 command chaining), '20'-'3F' reserved for future use, '40'-'7F' further inter-industry
 * coding (b4..b1 channel 4-19, b6 secure messaging, b5 command chaining), '80'-'FE' proprietary (GlobalPlatform
 * Card Specification v2.3.1 11.1.4 codes channels and secure messaging there the same way), 'FF' invalid. PC/SC
 * reader drivers take 'FF' commands as pseudo-APDUs addressed to the reader itself.</p>
 */
final class ClassBytes {

    static final int SELECT = 0xA4;
    static final int MANAGE_CHANNEL = 0x70;
    static final int GET_RESPONSE = 0xC0;
    static final int INVALID = 0xFF;

    private ClassBytes() {
    }

    /**
     * Whether a class byte is plain inter-industry: '00'-'03' or '40'-'4F', any logical channel, without secure
     * messaging and without command chaining. Only these SELECT, MANAGE CHANNEL and GET RESPONSE commands are the
     * card runtime's, in the form the harness sends them (JCRE 3.0.5 chapter 4).
     *
     * @param cla the class byte
     * @return true for '00'-'03' and '40'-'4F'
     */
    static boolean plain(int cla) {
        return (cla & 0xFC) == 0x00 || (cla & 0xF0) == 0x40;
    }

    /**
     * Whether a class byte is proprietary ('80'-'FE'), as GlobalPlatform commands are.
     *
     * @param cla the class byte
     * @return true for b8 = 1 except the invalid 'FF'
     */
    static boolean proprietary(int cla) {
        return (cla & 0x80) != 0 && cla != INVALID;
    }

    /**
     * Returns the logical channel coded in a class byte (ISO/IEC 7816-4:2005 Tables 2 and 3).
     *
     * @param cla the class byte
     * @return 0-3 for the first coding, 4-19 for the further coding
     */
    static int channel(int cla) {
        return (cla & 0x40) == 0 ? cla & 0x03 : 4 + (cla & 0x0F);
    }

    /**
     * Whether a command is a SELECT by DF name in exactly the form the harness sends: a plain class, P1 '04',
     * P2 '00' (first or only occurrence, FCI), a short Lc of 5-16 bytes (an AID, ISO/IEC 7816-5). Only this form
     * establishes a context; anything else that looks like a selection leaves the guard unsure.
     *
     * @param command the parsed command
     * @return true for the standard SELECT by AID
     */
    static boolean standardSelect(Apdu command) {
        int length = command.data().length;
        return plain(command.cla()) && command.ins() == SELECT && command.p1() == 0x04 && command.p2() == 0x00
                && !command.extended() && length >= 5 && length <= 16;
    }
}
