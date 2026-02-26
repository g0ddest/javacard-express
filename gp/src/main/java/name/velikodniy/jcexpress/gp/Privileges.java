package name.velikodniy.jcexpress.gp;

import java.util.StringJoiner;

/**
 * GlobalPlatform privilege bit constants and helpers.
 *
 * <p>Privilege bytes describe the permissions granted to an application or
 * security domain on a smart card. These bits are typically found in the
 * INSTALL [for install] command and in GET STATUS responses (tag C5).</p>
 *
 * <h2>Byte 1 bits (GP Card Specification 2.2+):</h2>
 * <ul>
 *   <li>Bit 8 ({@link #SECURITY_DOMAIN}) — entry is a Security Domain</li>
 *   <li>Bit 7 ({@link #DAP_VERIFICATION}) — DAP verification</li>
 *   <li>Bit 6 ({@link #DELEGATED_MANAGEMENT}) — delegated management</li>
 *   <li>Bit 5 ({@link #CARD_LOCK}) — can lock the card</li>
 *   <li>Bit 4 ({@link #CARD_TERMINATE}) — can terminate the card</li>
 *   <li>Bit 3 ({@link #CARD_RESET}) — default selected / card reset</li>
 *   <li>Bit 2 ({@link #CVM_MANAGEMENT}) — CVM management</li>
 *   <li>Bit 1 ({@link #MANDATED_DAP}) — mandated DAP verification</li>
 * </ul>
 *
 * @see AppletInfo#privileges()
 */
public final class Privileges {

    private Privileges() {
    }

    /** Bit 8: entry is a Security Domain. */
    public static final int SECURITY_DOMAIN = 0x80;

    /** Bit 7: DAP verification. */
    public static final int DAP_VERIFICATION = 0x40;

    /** Bit 6: delegated management privilege. */
    public static final int DELEGATED_MANAGEMENT = 0x20;

    /** Bit 5: can lock the card. */
    public static final int CARD_LOCK = 0x10;

    /** Bit 4: can terminate the card. */
    public static final int CARD_TERMINATE = 0x08;

    /** Bit 3: default selected / card reset. */
    public static final int CARD_RESET = 0x04;

    /** Bit 2: CVM management. */
    public static final int CVM_MANAGEMENT = 0x02;

    /** Bit 1: mandated DAP verification. */
    public static final int MANDATED_DAP = 0x01;

    /**
     * Returns true if the privilege byte has the Security Domain bit set.
     *
     * @param privileges the privilege byte
     * @return true if Security Domain
     */
    public static boolean isSecurityDomain(int privileges) {
        return (privileges & SECURITY_DOMAIN) != 0;
    }

    /**
     * Returns true if Delegated Management privilege is set.
     *
     * @param privileges the privilege byte
     * @return true if delegated management
     */
    public static boolean hasDelegatedManagement(int privileges) {
        return (privileges & DELEGATED_MANAGEMENT) != 0;
    }

    /**
     * Returns a human-readable description of privilege bits.
     *
     * <p>Multiple bits are joined with " | ". Returns "none" if no bits are set.</p>
     *
     * @param privileges the privilege byte
     * @return description string, e.g. "SECURITY_DOMAIN | DELEGATED_MANAGEMENT"
     */
    public static String describe(int privileges) {
        if (privileges == 0) {
            return "none";
        }
        StringJoiner joiner = new StringJoiner(" | ");
        if ((privileges & SECURITY_DOMAIN) != 0) joiner.add("SECURITY_DOMAIN");
        if ((privileges & DAP_VERIFICATION) != 0) joiner.add("DAP_VERIFICATION");
        if ((privileges & DELEGATED_MANAGEMENT) != 0) joiner.add("DELEGATED_MANAGEMENT");
        if ((privileges & CARD_LOCK) != 0) joiner.add("CARD_LOCK");
        if ((privileges & CARD_TERMINATE) != 0) joiner.add("CARD_TERMINATE");
        if ((privileges & CARD_RESET) != 0) joiner.add("CARD_RESET");
        if ((privileges & CVM_MANAGEMENT) != 0) joiner.add("CVM_MANAGEMENT");
        if ((privileges & MANDATED_DAP) != 0) joiner.add("MANDATED_DAP");
        return joiner.toString();
    }
}
