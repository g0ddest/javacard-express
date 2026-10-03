package name.velikodniy.jcexpress.container.applets;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;

/** Applet that never returns from {@code process} for INS 10 (an applet bug); other INS return 9000. */
public class LoopingApplet extends Applet {

    private static volatile boolean spin = true;

    /**
     * Applet installation entry point.
     *
     * @param bArray  install parameters
     * @param bOffset offset
     * @param bLength length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new LoopingApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        if (apdu.getBuffer()[ISO7816.OFFSET_INS] == 0x10) {
            while (spin) {
                Thread.onSpinWait();
            }
        }
    }
}
