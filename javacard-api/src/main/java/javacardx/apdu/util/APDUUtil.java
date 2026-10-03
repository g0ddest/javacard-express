package javacardx.apdu.util;

/**
 * Decodes the class byte (CLA) of a command APDU according to ISO/IEC 7816-4. The methods work on any CLA
 * value, for example one stored from an earlier command.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class APDUUtil {

    private APDUUtil() {
    }

    /**
     * Returns the logical channel number encoded in a class byte.
     *
     * @param cla the class byte
     * @return the logical channel number (0 to 19)
     */
    public static byte getCLAChannel(byte cla) {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether a class byte indicates secure messaging.
     *
     * @param cla the class byte
     * @return {@code true} if the secure messaging bits are set
     */
    public static boolean isSecureMessagingCLA(byte cla) {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether a class byte has the command chaining bit set.
     *
     * @param cla the class byte
     * @return {@code true} if more commands of a chain follow
     */
    public static boolean isCommandChainingCLA(byte cla) {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether a class byte uses the ISO/IEC 7816-4 interindustry encoding.
     *
     * @param cla the class byte
     * @return {@code true} for an interindustry class byte
     */
    public static boolean isISOInterindustryCLA(byte cla) {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether a class byte is valid; the value {@code 0xFF} is not.
     *
     * @param cla the class byte
     * @return {@code true} if the class byte is valid
     */
    public static boolean isValidCLA(byte cla) {
        throw new RuntimeException("stub");
    }
}
