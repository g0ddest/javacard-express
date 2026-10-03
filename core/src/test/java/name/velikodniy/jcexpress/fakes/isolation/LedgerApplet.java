package name.velikodniy.jcexpress.fakes.isolation;

import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Shareable;

/** Server applet: shares {@link Ledger} (balance 0x1234) with every client. */
public class LedgerApplet extends Applet implements Ledger {

    /**
     * Installs the applet under the instance AID from the install parameters.
     *
     * @param bArray  install parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new LedgerApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    @Override
    public short balance() {
        return 0x1234;
    }

    @Override
    public Shareable getShareableInterfaceObject(AID clientAid, byte parameter) {
        return this;
    }

    @Override
    public void process(APDU apdu) {
        // the ledger is used through its shareable interface only
    }
}
