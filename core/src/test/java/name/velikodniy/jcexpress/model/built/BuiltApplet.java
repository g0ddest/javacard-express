package name.velikodniy.jcexpress.model.built;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * An applet whose package has a build descriptor ({@code META-INF/javacard/name.velikodniy.jcexpress.model.built
 * .properties} in the test resources), as the Maven plugin writes it for the packages it builds: CLA 80 INS 01
 * answers {@code 42}.
 */
public final class BuiltApplet extends Applet {

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

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_INS] != 0x01) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        buffer[0] = 0x42;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }
}
