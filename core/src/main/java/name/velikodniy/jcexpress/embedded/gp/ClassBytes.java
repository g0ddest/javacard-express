package name.velikodniy.jcexpress.embedded.gp;

import name.velikodniy.jcexpress.apdu.ClassByte;

/**
 * The class byte rules of the secure channel: which classes carry the commands that open a session
 * (GlobalPlatform Card Specification v2.3 Amendment D v1.1.2 Tables 7-2 and 7-5 with the coding of GlobalPlatform
 * Card Specification v2.3.1 section 11.1.4), how the class byte enters a C-MAC (Amendment D 6.2.4) and how it
 * looks once the protection of a command is removed.
 */
final class ClassBytes {

    private static final int PROPRIETARY = 0x80;
    private static final int FURTHER_CODING = 0x40;
    private static final int CHAINING = 0x10;
    private static final int FIRST_CODING_SECURE_MESSAGING = 0x0C;
    private static final int FURTHER_CODING_SECURE_MESSAGING = 0x20;
    private static final int GLOBALPLATFORM_SECURE_MESSAGING = 0x04;

    private ClassBytes() {
    }

    /**
     * Tells whether a class byte is a GlobalPlatform command class (b8 = 1, Tables 11-11 and 11-12).
     *
     * @param cla the class byte, 0 to 255
     * @return true for proprietary classes that code a logical channel
     */
    static boolean globalPlatform(int cla) {
        return (cla & PROPRIETARY) != 0 && ClassByte.codesChannel(cla);
    }

    /**
     * Tells whether a class byte indicates secure messaging (GPCS v2.3.1 11.1.4; ISO/IEC 7816-4).
     *
     * @param cla the class byte, 0 to 255
     * @return true when b4-b3 are not 00 in the first coding, or b6 is set in the further coding
     */
    static boolean secureMessaging(int cla) {
        return ClassByte.indicatesSecureMessaging(cla);
    }

    /**
     * Tells whether a class byte is the one of INITIALIZE UPDATE: a GlobalPlatform command without secure
     * messaging, '80' to '83' or its coding for the logical channels 4 to 19 (Amendment D Table 7-2).
     *
     * @param cla the class byte, 0 to 255
     * @return true for such a class
     */
    static boolean initializeUpdate(int cla) {
        return globalPlatform(cla) && !secureMessaging(cla) && (cla & CHAINING) == 0;
    }

    /**
     * Tells whether a class byte is the one of EXTERNAL AUTHENTICATE: a GlobalPlatform command with
     * GlobalPlatform secure messaging, '84' to '87' or its coding for the logical channels 4 to 19 (Amendment D
     * Table 7-5).
     *
     * @param cla the class byte, 0 to 255
     * @return true for such a class
     */
    static boolean externalAuthenticate(int cla) {
        if (!globalPlatform(cla) || (cla & CHAINING) != 0) {
            return false;
        }
        return (cla & FURTHER_CODING) != 0 ? (cla & FURTHER_CODING_SECURE_MESSAGING) != 0
                : (cla & FIRST_CODING_SECURE_MESSAGING) == GLOBALPLATFORM_SECURE_MESSAGING;
    }

    /**
     * Returns the class byte as it enters the C-MAC: logical channel number zero, b4 = 0, b3 = 1 (Amendment D
     * 6.2.4), in the first coding whatever coding the command arrived in.
     *
     * @param cla the class byte of the command, 0 to 255
     * @return the class byte of the MAC input
     */
    static int forMac(int cla) {
        int basic = ClassByte.codesChannel(cla) ? ClassByte.withChannel(cla, 0) : cla;
        return (basic & ~FIRST_CODING_SECURE_MESSAGING & 0xFC) | GLOBALPLATFORM_SECURE_MESSAGING;
    }

    /**
     * Returns the class byte of a command whose protection was removed: the secure messaging indication is
     * cleared, the logical channel number stays (GlobalPlatform Card API 1.8, interface SecureChannel: the
     * protection is removed without altering the indication of the logical channel).
     *
     * @param cla the class byte of the protected command, 0 to 255
     * @return the class byte without secure messaging
     */
    static int withoutSecureMessaging(int cla) {
        return (cla & FURTHER_CODING) != 0 ? cla & ~FURTHER_CODING_SECURE_MESSAGING
                : cla & ~FIRST_CODING_SECURE_MESSAGING;
    }
}
