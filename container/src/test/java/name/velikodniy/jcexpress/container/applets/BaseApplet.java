package name.velikodniy.jcexpress.container.applets;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

/** User-defined abstract base applet: INS 00 returns the two-byte {@link #value()} of the subclass. */
public abstract class BaseApplet extends Applet {

    /** @return the value this applet reports */
    protected abstract short value();

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        Util.setShort(buffer, (short) 0, value());
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
