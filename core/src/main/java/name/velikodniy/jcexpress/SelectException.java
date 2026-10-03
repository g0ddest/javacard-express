package name.velikodniy.jcexpress;

/**
 * Thrown by {@link SmartCardSession#select(AID)} (and the install methods that select the new applet)
 * when the card does not select the requested applet.
 *
 * <p>ISO/IEC 7816-4:2005 7.1.1: a SELECT that completes with an error status leaves the current
 * selection unchanged, so commands sent after a failed SELECT would still reach the previously
 * selected applet. Sessions report the failure instead of continuing silently.</p>
 */
public class SelectException extends RuntimeException {

    private final transient AID aid;
    private final int sw;

    /**
     * Creates a new exception.
     *
     * @param aid     the AID that was to be selected
     * @param sw      the status word returned by the card (0x9000 when the card answered success but
     *                another applet stayed selected)
     * @param message the detail message
     */
    public SelectException(AID aid, int sw, String message) {
        super(message);
        this.aid = aid;
        this.sw = sw;
    }

    /**
     * Returns the AID that was to be selected.
     *
     * @return the requested AID
     */
    public AID aid() {
        return aid;
    }

    /**
     * Returns the status word of the SELECT command.
     *
     * @return SW1-SW2
     */
    public int sw() {
        return sw;
    }
}
