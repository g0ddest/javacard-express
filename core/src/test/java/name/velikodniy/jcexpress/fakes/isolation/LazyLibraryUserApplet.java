package name.velikodniy.jcexpress.fakes.isolation;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;
import name.velikodniy.jcexpress.fakes.library.Counter;

/**
 * Applet that needs a class of another package ({@link Counter}) only when it processes a command (any INS:
 * returns the first value of a new counter). Tests leave that class out of the class path.
 */
public class LazyLibraryUserApplet extends Applet {

    /**
     * Installs the applet under the instance AID from the install parameters.
     *
     * @param bArray  install parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new LazyLibraryUserApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        Util.setShort(buffer, (short) 0, new Counter().next());
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
