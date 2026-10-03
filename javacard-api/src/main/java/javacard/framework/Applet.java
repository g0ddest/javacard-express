package javacard.framework;

/**
 * Abstract base class for Java Card applets.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public abstract class Applet {

    /**
     * Constructs an Applet instance.
     */
    protected Applet() {
        throw new RuntimeException("stub");
    }

    /**
     * Called by the runtime to create an instance of the applet.
     * Subclasses must override this method.
     *
     * @param bArray  installation parameters
     * @param bOffset starting offset in bArray
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        throw new RuntimeException("stub");
    }

    /**
     * Called by the runtime to process an incoming APDU command.
     *
     * @param apdu the incoming APDU
     * @throws ISOException if a processing error occurs
     */
    public abstract void process(APDU apdu) throws ISOException;

    /**
     * Called when the applet is selected.
     *
     * @return true if selection succeeded
     */
    public boolean select() {
        throw new RuntimeException("stub");
    }

    /**
     * Called when the applet is deselected.
     */
    public void deselect() {
        throw new RuntimeException("stub");
    }

    /**
     * Returns a shareable interface object for inter-applet communication.
     *
     * @param clientAID the AID of the requesting applet
     * @param parameter a parameter byte
     * @return a shareable interface object, or null
     */
    public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
        throw new RuntimeException("stub");
    }

    /**
     * Registers this applet instance with the runtime under the applet's own AID: its Java Card platform name, the
     * AID of the applet in the Applet component of its CAP file (JCVM 3.1 §6.6), not the instance AID of the install
     * parameters. GlobalPlatform requires applets to register with the instance AID that INSTALL [for install] gives
     * them ({@link #register(byte[], short, byte)}, GPCS v2.3.1 A.1, "Installation"); an applet installed under
     * another AID than its own, or more than once, needs that method. jCardSim, which the simulators of JavaCard
     * Express run applets on, registers an instance under the AID its installation requested with either method.
     *
     * @throws SystemException on error
     */
    protected final void register() throws SystemException {
        throw new RuntimeException("stub");
    }

    /**
     * Registers this applet instance with the runtime using the specified AID: on a GlobalPlatform card the instance
     * AID of the install parameters, {@code register(bArray, (short) (bOffset + 1), bArray[bOffset])} in
     * {@code install} (GPCS v2.3.1 A.1, "Installation").
     *
     * @param bArray byte array containing the AID
     * @param bOffset starting offset
     * @param bLength length of the AID
     * @throws SystemException on error
     */
    protected final void register(byte[] bArray, short bOffset, byte bLength) throws SystemException {
        throw new RuntimeException("stub");
    }

    /**
     * Returns whether this applet is being selected.
     *
     * @return true if the applet is being selected
     */
    protected final boolean selectingApplet() {
        throw new RuntimeException("stub");
    }

    /**
     * Tells whether the applet is being selected again on the same logical channel while it is already the
     * selected applet there, so that {@link #select()} can skip reinitialisation.
     *
     * @return {@code true} if this is a re-selection of the currently selected applet
     */
    protected static boolean reSelectingApplet() {
        throw new RuntimeException("stub");
    }
}
