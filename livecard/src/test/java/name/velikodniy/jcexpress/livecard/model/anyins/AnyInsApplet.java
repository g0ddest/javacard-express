package name.velikodniy.jcexpress.livecard.model.anyins;

import javacard.framework.APDU;
import javacard.framework.Applet;

/**
 * Answers every command it receives with {@code 9000} and one data byte, the command's INS, also INS values that
 * ISO/IEC 7816-4 gives SELECT ('A4') and MANAGE CHANNEL ('70') in the inter-industry classes: an applet whose own
 * commands use these values in a proprietary class.
 */
public class AnyInsApplet extends Applet {

    /**
     * Installs an instance under the instance AID of the install data.
     *
     * @param bArray  install data
     * @param bOffset offset of the install data
     * @param bLength length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new AnyInsApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        buffer[0] = buffer[1];
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }
}
