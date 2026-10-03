package name.velikodniy.jcexpress.fakes;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;
import javacardx.apdu.ExtendedLength;

/**
 * Applet that accepts extended length commands ({@link ExtendedLength}) and answers the number of data bytes it
 * received (2 bytes).
 */
public class ExtendedEchoApplet extends Applet implements ExtendedLength {

    /**
     * Installs the applet (Java Card install method).
     *
     * @param bArray  install data
     * @param bOffset offset of the install data
     * @param bLength length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ExtendedEchoApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        short received = 0;
        short count = apdu.setIncomingAndReceive();
        while (count > 0) {
            received += count;
            count = apdu.receiveBytes(apdu.getOffsetCdata());
        }
        Util.setShort(apdu.getBuffer(), (short) 0, received);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
