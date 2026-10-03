package name.velikodniy.jcexpress.container.applets;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

/** Applet that holds a {@link Counter} implementation; INS 00 returns the next count (0010, 0020, ...). */
public class InterfaceApplet extends Applet {

    private final Counter counter = new StepCounter();

    /**
     * Applet installation entry point.
     *
     * @param bArray  install parameters
     * @param bOffset offset
     * @param bLength length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new InterfaceApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        Util.setShort(buffer, (short) 0, counter.next());
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
