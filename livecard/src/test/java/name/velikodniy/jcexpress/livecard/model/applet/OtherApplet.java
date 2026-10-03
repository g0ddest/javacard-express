package name.velikodniy.jcexpress.livecard.model.applet;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/** A second applet in the package of {@link ModelApplet}: INS 37 answers {@code 0F}. */
public class OtherApplet extends Applet {

    private OtherApplet() {
        register();
    }

    /**
     * Installs an instance (Java Card install method).
     *
     * @param bArray  the installation parameters
     * @param bOffset their offset
     * @param bLength their length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new OtherApplet();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_INS] != 0x37) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        buffer[0] = 0x0F;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }
}
