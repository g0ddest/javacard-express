package name.velikodniy.jcexpress.fakes;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Applet with the bugs a test should find (CLA 80): its {@code process} lets exceptions escape, which a Java Card
 * runtime answers with '6F00' (an uncaught exception other than {@code ISOException}, JCRE 3.0.5 chapter 3).
 *
 * <pre>
 * INS 01  ArrayIndexOutOfBoundsException in process ("Index 5 out of bounds for length 4")
 * INS 02  NullPointerException in a helper method of the applet
 * INS 03  ISOException 6A80 (a status word, not a bug)
 * INS 04  ArrayIndexOutOfBoundsException inside Util.arrayCopyNonAtomic (the API, called by the applet)
 * INS 05  answers 9000
 * </pre>
 */
public class ThrowingApplet extends Applet {

    private byte[] cache;

    /**
     * Installs the applet (Java Card install method).
     *
     * @param bArray  install data
     * @param bOffset offset of the install data
     * @param bLength length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ThrowingApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        switch (buffer[ISO7816.OFFSET_INS]) {
            case 0x01 -> {
                byte[][] table = new byte[4][5];
                buffer[0] = table[5][0];
            }
            case 0x02 -> fillCache(buffer[ISO7816.OFFSET_P1]);
            case 0x03 -> ISOException.throwIt(ISO7816.SW_WRONG_DATA);
            case 0x04 -> Util.arrayCopyNonAtomic(buffer, (short) 0, new byte[2], (short) 0, (short) 4);
            case 0x05 -> {
                // success
            }
            default -> ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void fillCache(byte value) {
        cache[0] = value;
    }
}
