package com.example.abs;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

/** Abstract base with public, package-private and protected abstract methods. */
public abstract class AbsBase extends Applet {
    public abstract short f();

    abstract short g();

    protected abstract void h();

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        short r = (short) (f() + g());
        h();
        Util.setShort(buf, (short) 0, r);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
