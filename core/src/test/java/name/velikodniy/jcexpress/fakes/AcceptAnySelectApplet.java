package name.velikodniy.jcexpress.fakes;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;

/**
 * Applet that answers 9000 to every command, including SELECT commands the Java Card runtime forwards to
 * it because no applet matches the AID (JCRE behaviour for SELECT FILE by DF name that matches no
 * applet: the command goes to the currently selected applet).
 */
public class AcceptAnySelectApplet extends Applet {

    /**
     * Installs the applet with the AID supplied by the runtime.
     *
     * @param bArray  install data
     * @param bOffset offset of the install data
     * @param bLength length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new AcceptAnySelectApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (apdu.getBuffer()[ISO7816.OFFSET_INS] == (byte) 0xA4) {
            return; // accepts SELECT of anything (e.g. "files" it manages itself)
        }
    }
}
