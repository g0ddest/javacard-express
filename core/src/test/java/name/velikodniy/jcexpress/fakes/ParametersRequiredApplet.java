package name.velikodniy.jcexpress.fakes;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * Applet whose install method needs install parameters (the La part of the Java Card install data
 * {@code [Li][instance AID][Lc][control info][La][parameters]}): without them it throws {@code ISOException} 6A80,
 * with a first parameter byte {@code 00} it fails with a {@code NullPointerException}.
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
        short parameters = (short) (controlInfo + 1 + bArray[controlInfo]);
        if (bArray[parameters] == 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        if (bArray[(short) (parameters + 1)] == 0) {
            byte[] missing = null;
            missing[0] = 1; // a NullPointerException, as a careless install method causes
        }
        new ParametersRequiredApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
    }
}
