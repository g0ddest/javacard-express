package name.velikodniy.jcexpress.livecard.sim;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * Placeholder applet of a {@link JCardSimCard}: selecting it deselects the applet that was selected, as selecting
 * another application (e.g. the ISD) does on a real card. It answers no command.
 */
public final class IdleApplet extends Applet {

    private IdleApplet() {
        register();
    }

    /**
     * Installs the applet (called by jCardSim).
     *
     * @param bArray  install parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
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
