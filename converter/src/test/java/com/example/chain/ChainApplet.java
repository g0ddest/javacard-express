package com.example.chain;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.Util;

/** Override chain, empty subclass, internal and external super calls. */
public class ChainApplet extends Applet {
    private final A x1;
    private final A x2;
    private final A x4;
    private final A x5;

    ChainApplet() {
        x1 = new A();
        x2 = new B();
        x4 = new D();
        x5 = new E();
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ChainApplet();
    }

    @Override
    public boolean select() {
        return super.select();
    }

    @Override
    public void deselect() {
        super.deselect();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        short r;
        switch (buf[ISO7816.OFFSET_INS]) {
            case 1: r = x1.m(); break;
            case 2: r = x2.m(); break;
            case 4: r = x4.m(); break;
            case 5: r = x5.m(); break;
            case 6: r = x4.n(); break;
            case 7: r = ((C) x5).o(); break;
            default: r = x2.n();
        }
        Util.setShort(buf, (short) 0, r);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
