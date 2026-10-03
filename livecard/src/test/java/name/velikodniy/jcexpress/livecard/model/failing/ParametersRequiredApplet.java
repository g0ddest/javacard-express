package name.velikodniy.jcexpress.livecard.model.failing;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * Applet whose install method needs install parameters (the La part of the Java Card install data
 * {@code [Li][instance AID][Lc][control info][La][parameters]}) and throws {@code ISOException} 6A80 without them.
 * Its own package keeps the packages the live suite loads onto the real card unchanged; Java Card subset only (the
 * GlobalPlatform backends convert it from the test classes).
 */
public class ParametersRequiredApplet extends Applet {

    /**
     * Installs the applet under the instance AID of the install data.
     *
     * @param bArray  install data
     * @param bOffset offset of the install data
     * @param bLength length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        short controlInfo = (short) (bOffset + 1 + bArray[bOffset]);
        if (bArray[(short) (controlInfo + 1 + bArray[controlInfo])] == 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        new ParametersRequiredApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
    }
}
