package name.velikodniy.jcexpress.container.applets;

import javacard.framework.APDU;
import javacard.framework.Applet;

/** Applet that delegates to a separate top-level {@link Helper} class; INS 00 returns {@code 112233}. */
public class HelperApplet extends Applet {

    private final Helper helper = new Helper();

    /**
     * Applet installation entry point.
     *
     * @param bArray  install parameters
     * @param bOffset offset
     * @param bLength length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelperApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] data = helper.data();
        apdu.setOutgoing();
        apdu.setOutgoingLength((short) data.length);
        apdu.sendBytesLong(data, (short) 0, (short) data.length);
    }
}
