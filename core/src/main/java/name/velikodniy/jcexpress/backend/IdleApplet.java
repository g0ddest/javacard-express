package name.velikodniy.jcexpress.backend;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * An applet that does nothing; selecting it deselects the applet the tests talk to on jCardSim.
 */
public final class IdleApplet extends Applet {

    private IdleApplet() {
        register();
    }

    /**
     * Installs the applet (Java Card install method).
     *
     * @param bArray  the installation parameters
     * @param bOffset their offset
     * @param bLength their length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new IdleApplet();
    }

    @Override
    public void process(APDU apdu) {
        if (!selectingApplet()) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
