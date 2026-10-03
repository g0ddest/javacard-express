package name.velikodniy.jcexpress.fakes.isolation;

import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Client applet: INS 01 reads the balance of the {@link LedgerApplet} whose instance AID is the command data,
 * through the shareable interface {@link Ledger}.
 */
public class LedgerClientApplet extends Applet {

    /**
     * Installs the applet under the instance AID from the install parameters.
     *
     * @param bArray  install parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new LedgerClientApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
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
        short length = apdu.setIncomingAndReceive();
        AID server = JCSystem.lookupAID(buffer, ISO7816.OFFSET_CDATA, (byte) length);
        Ledger ledger = (Ledger) JCSystem.getAppletShareableInterfaceObject(server, (byte) 0);
        Util.setShort(buffer, (short) 0, ledger.balance());
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
