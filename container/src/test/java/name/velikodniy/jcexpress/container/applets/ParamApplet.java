package name.velikodniy.jcexpress.container.applets;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

/**
 * Records what {@code install(bArray, bOffset, bLength)} received. INS 00 returns {@code bLength} as two bytes
 * followed by the parameter bytes.
 */
public class ParamApplet extends Applet {

    private final byte[] params;

    private ParamApplet(byte[] bArray, short bOffset, byte bLength) {
        params = new byte[bLength & 0xFF];
        Util.arrayCopyNonAtomic(bArray, bOffset, params, (short) 0, (short) params.length);
    }

    /**
     * Applet installation entry point.
     *
     * @param bArray  install parameters
     * @param bOffset offset
     * @param bLength length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ParamApplet(bArray, bOffset, bLength).register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        Util.setShort(buffer, (short) 0, (short) params.length);
        Util.arrayCopyNonAtomic(params, (short) 0, buffer, (short) 2, (short) params.length);
        apdu.setOutgoingAndSend((short) 0, (short) (params.length + 2));
    }
}
