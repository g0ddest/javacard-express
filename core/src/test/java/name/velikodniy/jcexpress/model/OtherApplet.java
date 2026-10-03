package name.velikodniy.jcexpress.model;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/** A second test applet in the same package: INS 20 answers {@code 0F}. */
public final class OtherApplet extends Applet {

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

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        if (apdu.getBuffer()[ISO7816.OFFSET_INS] != 0x20) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        apdu.getBuffer()[0] = 0x0F;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }
}
