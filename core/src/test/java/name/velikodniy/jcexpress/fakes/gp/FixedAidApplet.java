package name.velikodniy.jcexpress.fakes.gp;

import javacard.framework.APDU;
import javacard.framework.Applet;
import org.globalplatform.GPSystem;

/**
 * An applet that registers under an AID of its own choice, 'F00000000A0A0A', whatever instance AID the install
 * parameters name, and answers its life cycle state to every command.
 */
public class FixedAidApplet extends Applet {

    private static final byte[] OWN_AID = {(byte) 0xF0, 0x00, 0x00, 0x00, 0x0A, 0x0A, 0x0A};

    /**
     * Installs the applet (Java Card install method).
     *
     * @param bArray  install data
     * @param bOffset offset of the install data
     * @param bLength length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new FixedAidApplet().register(OWN_AID, (short) 0, (byte) OWN_AID.length);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        apdu.getBuffer()[0] = GPSystem.getCardContentState();
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }
}
