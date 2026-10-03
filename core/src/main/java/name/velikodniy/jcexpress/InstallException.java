package name.velikodniy.jcexpress;

import javacard.framework.ISOException;

/**
 * Thrown when an applet instance cannot be created: its install method failed (jCardSim), or the card refused
 * INSTALL [for install] (a GlobalPlatform card). The message names the applet, the instance AID and the install
 * parameters, and says where the applet finds them: missing or too short install parameters are the usual reason
 * an install method fails.
 *
 * <p>Java Card API {@code javacard.framework.Applet.install(byte[], short, byte)}: the applet receives
 * {@code [Li][instance AID][Lc][control info][La][install parameters]} (see {@link AppletInstallParameters}); on a
 * GlobalPlatform card the install parameters are the application specific parameters (tag 'C9') of INSTALL [for
 * install].</p>
 */
public class InstallException extends IllegalStateException {

    /** The applet class name. */
    private final String appletClass;
    private final transient AID aid;
    /** The install parameters the test passed, empty for none. */
    private final byte[] parameters;
    /** The status word of the refused INSTALL [for install], 0 when no command was sent. */
    private final int sw;
    /** What failed. */
    private final String reason;

    /**
     * Creates a new exception.
     *
     * @param appletClass the applet class name
     * @param aid         the instance AID
     * @param parameters  the install parameters the test passed (the La part; null or empty for none)
     * @param sw          the status word of the refused INSTALL [for install], or 0 when no command was sent
     *                    (jCardSim installs without one)
     * @param reason      what failed, e.g. {@code its install method threw ISOException with reason 6A80}
     * @param cause       the underlying failure
     */
    public InstallException(String appletClass, AID aid, byte[] parameters, int sw, String reason, Throwable cause) {
        super(message(appletClass, aid, parameters, reason), cause);
        this.appletClass = appletClass;
        this.aid = aid;
        this.parameters = parameters == null ? new byte[0] : parameters.clone();
        this.sw = sw;
        this.reason = reason;
    }

    /**
     * Explains a failure of an applet's install method on jCardSim. jCardSim passes on an {@code ISOException} of the
     * install method with its reason; any other failure inside it or the applet's constructor, and an install method
     * that does not call {@code register()}, it reports as {@code SystemException} without the cause; a class
     * without a static install method it reports as {@code IllegalArgumentException}.
     *
     * @param appletClass the applet class name
     * @param aid         the instance AID
     * @param parameters  the install parameters the test passed (null or empty for none)
     * @param failure     what jCardSim's runtime threw
     * @return the exception, with {@link #sw()} 0
     */
    public static InstallException onJCardSim(String appletClass, AID aid, byte[] parameters,
                                              RuntimeException failure) {
        String reason = switch (failure) {
            case ISOException e -> String.format("its install method threw ISOException with reason %04X",
                    e.getReason() & 0xFFFF);
            case IllegalArgumentException e -> "jCardSim cannot call its install method: " + e.getMessage();
            default -> "its install method failed without an ISOException (jCardSim does not pass on other exceptions"
                    + " of the install method or the applet's constructor, and reports an install method that does"
                    + " not call register() the same way)";
        };
        return new InstallException(appletClass, aid, parameters, 0, reason, failure);
    }

    /**
     * Returns the applet class.
     *
     * @return the fully qualified class name
     */
    public String appletClass() {
        return appletClass;
    }

    /**
     * Returns the instance AID that was to be installed.
     *
     * @return the AID
     */
    public AID aid() {
        return aid;
    }

    /**
     * Returns the install parameters the test passed.
     *
     * @return a copy, empty for none
     */
    public byte[] parameters() {
        return parameters.clone();
    }

    /**
     * Returns the status word of the refused INSTALL [for install].
     *
     * @return SW1-SW2, or 0 when no command was sent (jCardSim)
     */
    public int sw() {
        return sw;
    }

    /**
     * Returns what failed.
     *
     * @return e.g. {@code its install method threw ISOException with reason 6A80}
     */
    public String reason() {
        return reason;
    }

    private static String message(String appletClass, AID aid, byte[] parameters, String reason) {
        return "Installing " + appletClass + " as " + aid.toHex() + " with install parameters "
                + (parameters == null || parameters.length == 0 ? "(none)" : Hex.encode(parameters)) + " failed: "
                + reason + ". The applet's install(byte[] bArray, short bOffset, byte bLength) receives"
                + " [Li][instance AID][Lc][control info][La][install parameters]; a test passes the install"
                + " parameters with @InstallApplet(params = \"<hex>\") or install(appletClass, aid, installParams)";
    }
}
