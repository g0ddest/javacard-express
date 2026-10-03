package name.velikodniy.jcexpress.livecard.model.importer;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;
import name.velikodniy.jcexpress.livecard.model.library.Tally;

/**
 * An applet that uses a class of another package ({@link Tally}), as an applet of a module that depends on a
 * library module does: INS 39 answers the next count (2 bytes). Its package imports the library package.
 */
public class ImporterApplet extends Applet {

    private final Tally tally = new Tally();

    /**
     * Installs an instance under the instance AID of the install data.
     *
     * @param bArray  install data
     * @param bOffset offset of the install data
     * @param bLength length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ImporterApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_INS] != 0x39) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        Util.setShort(buffer, (short) 0, tally.next());
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
