package name.velikodniy.jcexpress.livecard.model.built;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * An applet whose package has a build descriptor in the test resources
 * ({@code META-INF/javacard/name.velikodniy.jcexpress.livecard.model.built.properties}), as the Maven plugin writes
 * it for the packages it builds: INS 38 answers {@code 42}.
 */
public class BuiltApplet extends Applet {

    private BuiltApplet() {
        register();
    }

    /**
     * Installs an instance (Java Card install method).
     *
     * @param bArray  the installation parameters
     * @param bOffset their offset
     * @param bLength their length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new BuiltApplet();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_INS] != 0x38) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        buffer[0] = 0x42;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }
}
