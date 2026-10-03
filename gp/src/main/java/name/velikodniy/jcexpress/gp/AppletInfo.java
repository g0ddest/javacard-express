package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;

import java.util.Arrays;
import java.util.List;

/**
 * Parsed entry from a GlobalPlatform GET STATUS response (GPCS v2.3.1 Tables 11-36 and 11-37).
 *
 * <p>Each entry represents an application, security domain, or executable load file installed on the
 * card. Fields that the card did not return are empty.</p>
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
 * @param aid                         the AID bytes (tag '4F')
 * @param lifeCycleState              the lifecycle state byte (tag '9F70')
 * @param privileges                  the first privilege byte (tag 'C5' byte 1, Table 11-7), 0 if absent
 * @param privilegeBytes              all privilege bytes as returned (1 or 3 bytes, tag 'C5')
 * @param executableLoadFileAid       the application's Executable Load File AID (tag 'C4')
 * @param associatedSecurityDomainAid the associated Security Domain's AID (tag 'CC')
 * @param versionNumber               the Executable Load File version number (tag 'CE')
 * @param executableModuleAids        the Executable Module AIDs of a load file (tags '84')
 */
public record AppletInfo(
        byte[] aid,
        int lifeCycleState,
        int privileges,
        byte[] privilegeBytes,
        byte[] executableLoadFileAid,
        byte[] associatedSecurityDomainAid,
        byte[] versionNumber,
        List<byte[]> executableModuleAids
) {

    /**
     * Canonical constructor; copies the module list.
     *
     * @param aid                         the AID bytes
     * @param lifeCycleState              the lifecycle state byte
     * @param privileges                  the first privilege byte
     * @param privilegeBytes              all privilege bytes
     * @param executableLoadFileAid       the Executable Load File AID
     * @param associatedSecurityDomainAid the associated Security Domain AID
     * @param versionNumber               the load file version number
     * @param executableModuleAids        the Executable Module AIDs
     */
    public AppletInfo {
        executableModuleAids = List.copyOf(executableModuleAids);
    }

    /**
     * Creates an entry with AID, lifecycle state and the first privilege byte only.
     *
     * @param aid            the AID bytes
     * @param lifeCycleState the lifecycle state byte
     * @param privileges     the privilege byte
     */
    public AppletInfo(byte[] aid, int lifeCycleState, int privileges) {
        this(aid, lifeCycleState, privileges, new byte[]{(byte) privileges}, new byte[0], new byte[0],
                new byte[0], List.of());
    }

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
     * Returns true if the applet is locked (bit 8 set).
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
     * Returns a human-readable description of the privilege bits of byte 1.
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
                + ", privileges=" + Hex.encode(privilegeBytes) + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof AppletInfo that
                && lifeCycleState == that.lifeCycleState
                && privileges == that.privileges
                && Arrays.equals(aid, that.aid)
                && Arrays.equals(privilegeBytes, that.privilegeBytes)
                && Arrays.equals(executableLoadFileAid, that.executableLoadFileAid)
                && Arrays.equals(associatedSecurityDomainAid, that.associatedSecurityDomainAid)
                && Arrays.equals(versionNumber, that.versionNumber)
                && sameModules(that.executableModuleAids);
    }

    private boolean sameModules(List<byte[]> other) {
        if (executableModuleAids.size() != other.size()) {
            return false;
        }
        for (int i = 0; i < other.size(); i++) {
            if (!Arrays.equals(executableModuleAids.get(i), other.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(aid) * 31 + lifeCycleState;
    }
}
