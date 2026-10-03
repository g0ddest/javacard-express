package name.velikodniy.jcexpress.container.applets;

import javacard.framework.APDU;
import javacard.framework.Applet;

/**
 * Applet whose {@code select()} returns {@code false}. Java Card Runtime Environment Specification 3.0.5, 4.3:
 * when the applet declines to be selected the JCRE answers the SELECT command with SW '6999'
 * ({@code ISO7816.SW_APPLET_SELECT_FAILED}) and no applet is selected.
 */
public class RefusingSelectApplet extends Applet {

    /**
     * Applet installation entry point.
     *
     * @param bArray  install parameters
     * @param bOffset offset
     * @param bLength length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new RefusingSelectApplet().register();
    }

    @Override
    public boolean select() {
        return false;
    }

    @Override
    public void process(APDU apdu) {
        // never selected, so never called
    }
}
