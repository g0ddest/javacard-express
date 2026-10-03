package com.example.ifaceorder;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

/** Calls an inherited interface method through a sub-interface reference. */
public class OrderApplet extends Applet implements ASub {
    OrderApplet() {
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new OrderApplet();
    }

    @Override
    public short z() {
        return 26;
    }

    @Override
    public short a() {
        return 1;
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        ASub s = this;
        ZSuper z = this;
        short r = (short) (s.a() + z.z() + s.z());
        Util.setShort(buf, (short) 0, r);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
