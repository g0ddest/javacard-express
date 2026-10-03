package com.example.pkgvirt;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.Util;

/** Calls package-private virtual methods through base-typed references. */
public class PkgVirtApplet extends Applet {
    private Base b1;
    private Base b2;
    private Base b3;

    PkgVirtApplet() {
        b1 = new Base();
        b2 = new Sub();
        b3 = new Sub2();
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new PkgVirtApplet();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        short r;
        switch (buf[ISO7816.OFFSET_INS]) {
            case 1: r = b1.calc((short) 10); break;
            case 2: r = b2.calc((short) 10); break;
            case 3: r = b3.calc((short) 10); break;
            case 4: r = ((Sub) b2).extra(); break;
            case 5: r = b2.pub(); break;
            default: b1.touch(); r = 0;
        }
        Util.setShort(buf, (short) 0, r);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
