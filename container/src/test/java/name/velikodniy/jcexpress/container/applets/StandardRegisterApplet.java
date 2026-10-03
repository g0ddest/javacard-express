package name.velikodniy.jcexpress.container.applets;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.JCSystem;

/**
 * Applet following the canonical Java Card install pattern: it registers under the instance AID found in its
 * install parameters ({@code register(bArray, (short) (bOffset + 1), bArray[bOffset])}). INS 00 returns the AID it
 * is registered under.
 */
public class StandardRegisterApplet extends Applet {

    /**
     * Applet installation entry point.
     *
     * @param bArray  install parameters: [Li][instance AID][Lc][control info][La][applet data]
     * @param bOffset offset of Li
     * @param bLength total length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new StandardRegisterApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        short length = JCSystem.getAID().getBytes(buffer, (short) 0);
        apdu.setOutgoingAndSend((short) 0, length);
    }
}
