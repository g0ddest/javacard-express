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
        return true;
    }

    /**
     * Called when the applet is deselected.
     */
    public void deselect() {
    }

    /**
     * Returns a shareable interface object for inter-applet communication.
     *
     * @param clientAID the AID of the requesting applet
     * @param parameter a parameter byte
     * @return a shareable interface object, or null
     */
    public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
        return null;
    }

    /**
     * Registers this applet instance with the runtime using the AID from the install parameters.
     *
     * @throws SystemException on error
     */
    protected final void register() throws SystemException {
        throw new RuntimeException("stub");
    }

    /**
     * Registers this applet instance with the runtime using the specified AID.
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
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
