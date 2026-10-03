package name.velikodniy.jcexpress.gp;

/**
 * GlobalPlatform lifecycle state constants and utilities.
 *
 * <p>Defines constants for application, card manager (ISD), and load file
 * lifecycle states as specified in the GlobalPlatform Card Specification.</p>
 *
 * <h2>Application lifecycle:</h2>
 * <pre>
 * INSTALLED (03) → SELECTABLE (07) → PERSONALIZED (0F)
 *       ↓              ↓                   ↓
 *    LOCKED (83)    LOCKED (87)       LOCKED (8F)
 *                                          ↓
 *                                    TERMINATED (FF)
 * </pre>
 *
 * <h2>Card Manager (ISD) lifecycle (GPCS v2.3.1 Figure 5-1):</h2>
 * <pre>
 * OP_READY (01) → INITIALIZED (07) → SECURED (0F)
 *                                       ↓  ↑   reversible, with restrictions: see {@link #CARD_LOCKED}
 *                                  CARD_LOCKED (7F)
 *
 * any state → TERMINATED (FF)                  irreversible: see {@link #CARD_TERMINATED}
 * </pre>
 *
 * @see GPSession#setStatus(int, String, int)
 * @see AppletInfo#lifeCycleState()
 */
public final class Lifecycle {

    private Lifecycle() {
    }

    // ── Scope constants (P1 for GET STATUS / SET STATUS) ──

    /** Issuer Security Domain scope. */
    public static final int SCOPE_ISD = 0x80;

    /** Applications and Supplementary Security Domains scope. */
    public static final int SCOPE_APPS = 0x40;

    /** Executable Load Files scope. */
    public static final int SCOPE_LOAD_FILES = 0x20;

    /** Executable Load Files and their Executable Modules scope (GET STATUS only, GPCS v2.3.1 Table 11-33). */
    public static final int SCOPE_LOAD_FILES_AND_MODULES = 0x10;

    /**
     * Security Domain and its associated Applications (SET STATUS only, GPCS v2.3.1 Table 11-86): applies
     * to the transition to, and back from, the LOCKED state.
     */
    public static final int SCOPE_SD_AND_APPS = 0x60;

    // ── Application lifecycle states ──

    /** Application is installed but not yet selectable. */
    public static final int APP_INSTALLED = 0x03;

    /** Application is installed and selectable. */
    public static final int APP_SELECTABLE = 0x07;

    /** Application is personalized (application-specific state). */
    public static final int APP_PERSONALIZED = 0x0F;

    /**
     * Application is locked (bit 8 set).
     *
     * <p>As SET STATUS P2 sent by a Security Domain for another application, b8 = 1 requests the
     * transition to LOCKED and b8 = 0 the transition back; all other bits are ignored (GPCS v2.3.1
     * 11.10.2.2). Use with {@link GPSession#lockApp(String)}.</p>
     */
    public static final int APP_LOCKED = 0x80;

    /**
     * Application is terminated (irreversible). Only an application can set this state for itself; a
     * Security Domain cannot terminate another application with SET STATUS (GPCS v2.3.1 11.10.2.2).
     */
    public static final int APP_TERMINATED = 0xFF;

    // ── Card Manager (ISD) lifecycle states ──

    /** Card Manager is in OP_READY state. */
    public static final int CARD_OP_READY = 0x01;

    /** Card Manager is initialized. */
    public static final int CARD_INITIALIZED = 0x07;

    /** Card Manager is secured (normal operational state). */
    public static final int CARD_SECURED = 0x0F;

    /**
     * Card is locked: CARD_LOCKED (GPCS v2.3.1 5.1.1.4, 9.6.3).
     *
     * <p>Only the application with the Final Application privilege (by default the Issuer Security Domain, 6.6.2)
     * can be selected; applications already selected stay selected until their session ends (9.6.3). Security
     * Domains process only GET DATA, GET STATUS and SET STATUS; DELETE, INSTALL, LOAD, PUT KEY and STORE DATA are
     * refused (Table 11-1), so no card content, key or data can change. The transition from {@link #CARD_SECURED}
     * is reversible, but only by a Security Domain with the Card Lock privilege (normally the Issuer Security
     * Domain) to which the off-card entity authenticates with that domain's keys (9.6.3, 11.10.2.2, Table 11-2),
     * and only while that domain can be selected: if another application holds the Final Application privilege,
     * the domain cannot be selected once the session that locked the card ends (Table 11-1 Note 1). Unlocking may
     * therefore be impossible in practice, and the issuer policy of a real card may restrict it further.</p>
     */
    public static final int CARD_LOCKED = 0x7F;

    /**
     * Card is terminated: TERMINATED (GPCS v2.3.1 5.1.1.5, 9.6.4). <strong>Irreversible</strong>: "The state
     * transition from any other state to TERMINATED is irreversible."
     *
     * <p>Card content management and life cycle changes are disabled for good; only the application with the Final
     * Application privilege can be selected, and if it is a Security Domain it processes GET DATA only (Table 11-1).
     * Set by a Security Domain with the Card Terminate privilege (11.10.2.2); the issuer policy may also reset the
     * card at once (9.6.4).</p>
     */
    public static final int CARD_TERMINATED = 0xFF;

    // ── Load File lifecycle states ──

    /** Load file is loaded on card. */
    public static final int LOAD_FILE_LOADED = 0x01;

    // ── Utilities ──

    /**
     * Returns true if the LOCKED bit (b8) is set in the lifecycle state.
     *
     * @param state the lifecycle state value
     * @return true if locked
     */
    public static boolean isLocked(int state) {
        return (state & 0x80) != 0;
    }

    /**
     * Returns a human-readable description of the lifecycle state.
     *
     * @param state the lifecycle state value
     * @return description string, e.g. "SELECTABLE (07)" or "LOCKED|PERSONALIZED (8F)"
     */
    public static String describe(int state) {
        if (state == APP_TERMINATED) {
            return "TERMINATED (FF)";
        }

        StringBuilder sb = new StringBuilder();

        if (isLocked(state)) {
            sb.append("LOCKED");
            int base = state & 0x7F;
            if (base != 0) {
                sb.append("|");
                sb.append(baseStateName(base));
            }
        } else {
            sb.append(baseStateName(state));
        }

        sb.append(String.format(" (%02X)", state));
        return sb.toString();
    }

    private static String baseStateName(int state) {
        return switch (state) {
            case 0x01 -> "OP_READY";
            case APP_INSTALLED -> "INSTALLED";
            case APP_SELECTABLE -> "SELECTABLE";
            case APP_PERSONALIZED -> "PERSONALIZED";
            case 0x7F -> "CARD_LOCKED";
            default -> String.format("UNKNOWN_%02X", state);
        };
    }
}
