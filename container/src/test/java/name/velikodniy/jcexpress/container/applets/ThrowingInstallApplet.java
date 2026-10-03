package name.velikodniy.jcexpress.container.applets;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;

/** Applet whose {@code install} always fails with {@code ISOException(6A80)}. */
public class ThrowingInstallApplet extends Applet {

    /**
     * Applet installation entry point: always fails.
     *
     * @param bArray  install parameters
     * @param bOffset offset
     * @param bLength length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        ISOException.throwIt((short) 0x6A80);
    }

    @Override
    public void process(APDU apdu) {
        // never installed
    }
}
