package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;

import java.util.Arrays;

/**
 * Parsed entry from a GlobalPlatform GET STATUS response.
 *
 * <p>Each entry represents an application, security domain, or executable load file
 * installed on the card.</p>
 *
 * <h2>Lifecycle states (from GP Card Spec):</h2>
 * <ul>
 *   <li>{@code 0x03} — INSTALLED</li>
 *   <li>{@code 0x07} — SELECTABLE (INSTALLED + made selectable)</li>
 *   <li>{@code 0x0F} — PERSONALIZED</li>
 *   <li>{@code 0x83} — LOCKED</li>
 *   <li>{@code 0xFF} — TERMINATED</li>
 * </ul>
 *
 * @see Lifecycle
 *
 * @param aid            the AID bytes
 * @param lifeCycleState the lifecycle state byte
 * @param privileges     the privilege byte(s)
 */
public record AppletInfo(
        byte[] aid,
        int lifeCycleState,
        int privileges
) {

    /**
     * Returns the AID as an uppercase hex string.
     *
     * @return hex-encoded AID
     */
    public String aidHex() {
        return Hex.encode(aid);
    }

    /**
     * Returns true if the applet is in the SELECTABLE state (bit pattern x07).
     *
     * @return true if selectable
     */
    public boolean isSelectable() {
        return (lifeCycleState & 0x07) == 0x07;
    }

    /**
     * Returns true if the applet is locked (bit 7 set).
     *
     * @return true if locked
     */
    public boolean isLocked() {
        return (lifeCycleState & 0x80) != 0;
    }

    /**
     * Returns true if the applet is in the PERSONALIZED state (bit pattern x0F).
     *
     * @return true if personalized
     */
    public boolean isPersonalized() {
        return (lifeCycleState & 0x0F) == 0x0F;
    }

    /**
     * Returns true if the applet is terminated (0xFF).
     *
     * @return true if terminated
     */
    public boolean isTerminated() {
        return lifeCycleState == 0xFF;
    }

    /**
     * Returns a human-readable description of the lifecycle state.
     *
     * @return description string, e.g. "SELECTABLE (07)"
     * @see Lifecycle#describe(int)
     */
    public String lifeCycleDescription() {
        return Lifecycle.describe(lifeCycleState);
    }

    /**
     * Returns true if this entry is a Security Domain (privilege bit 8 set).
     *
     * @return true if Security Domain
     * @see Privileges#isSecurityDomain(int)
     */
    public boolean isSecurityDomain() {
        return Privileges.isSecurityDomain(privileges);
    }

    /**
     * Returns true if this entry has Delegated Management privilege.
     *
     * @return true if delegated management
     * @see Privileges#hasDelegatedManagement(int)
     */
    public boolean hasDelegatedManagement() {
        return Privileges.hasDelegatedManagement(privileges);
    }

    /**
     * Returns a human-readable description of the privilege bits.
     *
     * @return description string, e.g. "SECURITY_DOMAIN | DELEGATED_MANAGEMENT"
     * @see Privileges#describe(int)
     */
    public String privilegeDescription() {
        return Privileges.describe(privileges);
    }

    @Override
    public String toString() {
        return "AppletInfo[aid=" + aidHex()
                + ", state=" + String.format("0x%02X", lifeCycleState)
                + ", privileges=" + String.format("0x%02X", privileges) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppletInfo that)) return false;
        return lifeCycleState == that.lifeCycleState
                && privileges == that.privileges
                && Arrays.equals(aid, that.aid);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(aid) * 31 + lifeCycleState;
    }
}
