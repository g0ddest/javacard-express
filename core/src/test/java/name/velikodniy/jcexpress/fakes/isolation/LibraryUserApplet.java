package name.velikodniy.jcexpress.fakes.isolation;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;
import name.velikodniy.jcexpress.fakes.library.Counter;

/**
 * Applet that needs a class of another package ({@link Counter}) as soon as it is installed. Tests leave that
 * class out of the class path to see how a missing library class is reported. INS any: returns the next value
 * of the counter.
 */
public class LibraryUserApplet extends Applet {

    private final Counter counter = new Counter();

    /**
     * Installs the applet under the instance AID from the install parameters.
     *
     * @param bArray  install parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new LibraryUserApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        Util.setShort(buffer, (short) 0, counter.next());
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
