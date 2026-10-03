package name.velikodniy.jcexpress.livecard.model.samepackage;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * A test-source applet in the same package as its test class ({@link SamePackageScenario}), the natural layout of
 * an applet that exists only for the tests: INS 0E answers {@code 5350} ("SP"). Java Card subset only; the test
 * class next to it is not.
 */
public class SamePackageApplet extends Applet {

    /**
     * Installs an instance under the instance AID of the install data.
     *
     * @param bArray  install data
     * @param bOffset offset of the install data
     * @param bLength length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new SamePackageApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_INS] != 0x0E) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        buffer[0] = 0x53;
        buffer[1] = 0x50;
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
