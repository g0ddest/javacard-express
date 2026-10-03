package name.velikodniy.jcexpress.fakes.isolation;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Test applet with a static and an instance counter, to observe where static fields live.
 *
 * <ul>
 *   <li>INS 01: increments both counters and returns {@code [static counter(2)][instance counter(2)]}</li>
 * </ul>
 *
 * <p>On a card, static fields belong to the package: a newly loaded package starts at 0 and every instance of
 * the package's applets sees the same value. An instance field belongs to the applet instance.</p>
 */
public class StaticCounterApplet extends Applet {

    /** The package-wide counter (read by tests that check which copy of the class they see). */
    public static short shared;

    private short own;

    /**
     * Installs the applet under the instance AID from the install parameters.
     *
     * @param bArray  install parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new StaticCounterApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_INS] != 0x01) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        shared++;
        own++;
        Util.setShort(buffer, (short) 0, shared);
        Util.setShort(buffer, (short) 2, own);
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }
}
