package name.velikodniy.jcexpress.container.applets;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Keeps one counter in persistent memory and one in a {@code CLEAR_ON_RESET} transient array. INS 01 increments
 * both, INS 02 returns both (persistent first, two bytes each).
 */
public class StateApplet extends Applet {

    private short persistent;
    private final short[] transientCounter = JCSystem.makeTransientShortArray((short) 1, JCSystem.CLEAR_ON_RESET);

    /**
     * Applet installation entry point.
     *
     * @param bArray  install parameters
     * @param bOffset offset
     * @param bLength length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new StateApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        switch (buffer[ISO7816.OFFSET_INS]) {
            case 0x01:
                persistent++;
                transientCounter[0]++;
                return;
            case 0x02:
                Util.setShort(buffer, (short) 0, persistent);
                Util.setShort(buffer, (short) 2, transientCounter[0]);
                apdu.setOutgoingAndSend((short) 0, (short) 4);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
