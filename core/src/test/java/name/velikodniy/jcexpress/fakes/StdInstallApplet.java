package name.velikodniy.jcexpress.fakes;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Applet that uses the canonical install pattern of the Java Card API
 * ({@code javacard.framework.Applet.install}, JC 3.0.5/3.1 API):
 * {@code bArray = [Li][instance AID][Lc][control info][La][applet data]}, registering with the
 * instance AID ({@code register(bArray, bOffset + 1, bArray[bOffset])}).
 *
 * <ul>
 *   <li>INS 20: returns the application data (the La part)</li>
 *   <li>INS 21: returns the control information (the Lc part)</li>
 * </ul>
 */
public class StdInstallApplet extends Applet {

    private final byte[] appData;
    private final byte[] controlInfo;

    private StdInstallApplet(byte[] bArray, short bOffset) {
        short li = bArray[bOffset];
        short lcOffset = (short) (bOffset + 1 + li);
        short lc = bArray[lcOffset];
        controlInfo = new byte[lc];
        Util.arrayCopyNonAtomic(bArray, (short) (lcOffset + 1), controlInfo, (short) 0, lc);
        short laOffset = (short) (lcOffset + 1 + lc);
        short la = bArray[laOffset];
        appData = new byte[la];
        Util.arrayCopyNonAtomic(bArray, (short) (laOffset + 1), appData, (short) 0, la);
    }

    /**
     * Installs the applet and registers it under the instance AID found in the install data.
     *
     * @param bArray  install data in the Java Card API layout
     * @param bOffset offset of the instance AID length byte
     * @param bLength total length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new StdInstallApplet(bArray, bOffset).register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        byte[] out;
        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x20 -> out = appData;
            case 0x21 -> out = controlInfo;
            default -> {
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
                return;
            }
        }
        Util.arrayCopyNonAtomic(out, (short) 0, buf, (short) 0, (short) out.length);
        apdu.setOutgoingAndSend((short) 0, (short) out.length);
    }
}
