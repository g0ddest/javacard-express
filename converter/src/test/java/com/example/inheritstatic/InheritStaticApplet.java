package com.example.inheritstatic;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/** Uses inherited static and instance members through a subclass. */
public class InheritStaticApplet extends Applet {
    private final Leaf leaf;

    protected InheritStaticApplet() {
        leaf = new Leaf();
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new InheritStaticApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_INS] != 1) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        Util.setShort(buf, (short) 0, leaf.sum());
        Util.setShort(buf, (short) 2, Leaf.statics());
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }
}
