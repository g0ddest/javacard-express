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
 * <h2>Card Manager (ISD) lifecycle:</h2>
 * <pre>
 * OP_READY (01) → INITIALIZED (07) → SECURED (0F)
 *                                        ↓
 *                                  CARD_LOCKED (7F)
 *                                        ↓
 *                                  TERMINATED (FF)
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

    // ── Application lifecycle states ──

    /** Application is installed but not yet selectable. */
    public static final int APP_INSTALLED = 0x03;

    /** Application is installed and selectable. */
    public static final int APP_SELECTABLE = 0x07;

    /** Application is personalized (application-specific state). */
    public static final int APP_PERSONALIZED = 0x0F;

    /**
     * Application is locked (bit 7 set).
     *
     * <p>When sent as a SET STATUS P2, the card ORs this bit with the
     * current lifecycle state. Use with {@link GPSession#lockApp(String)}.</p>
     */
    public static final int APP_LOCKED = 0x80;

    /** Application is terminated (irreversible). */
    public static final int APP_TERMINATED = 0xFF;

    // ── Card Manager (ISD) lifecycle states ──

    /** Card Manager is in OP_READY state. */
    public static final int CARD_OP_READY = 0x01;

    /** Card Manager is initialized. */
    public static final int CARD_INITIALIZED = 0x07;

    /** Card Manager is secured (normal operational state). */
    public static final int CARD_SECURED = 0x0F;

    /** Card is locked (all applications inaccessible). */
    public static final int CARD_LOCKED = 0x7F;

    /** Card is terminated (irreversible). */
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
