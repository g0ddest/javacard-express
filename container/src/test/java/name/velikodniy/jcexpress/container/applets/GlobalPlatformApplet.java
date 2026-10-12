package name.velikodniy.jcexpress.container.applets;

import javacard.framework.APDU;
import javacard.framework.Applet;
import org.globalplatform.GPSystem;

/** An applet that asks the GlobalPlatform environment of its card for its life cycle state. */
public class GlobalPlatformApplet extends Applet {

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new GlobalPlatformApplet().register();
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
