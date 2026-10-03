package name.velikodniy.jcexpress.fakes;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * Applet whose {@code select()} declines the selection, so a SELECT of it is answered '6999' (applet selection
 * failed), as a locked or not yet personalised applet may do.
 */
public class RefusingSelectApplet extends Applet {

    /**
     * Installs the applet (Java Card install method).
     *
     * @param bArray  install data
     * @param bOffset offset of the install data
     * @param bLength length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new RefusingSelectApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    @Override
    public boolean select() {
        return false;
    }

    @Override
    public void process(APDU apdu) {
        ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
    }
}
