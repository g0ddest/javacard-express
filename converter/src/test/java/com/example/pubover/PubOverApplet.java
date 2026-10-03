package com.example.pubover;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

/** Applet of the rejected {@code pubover} fixture. */
public class PubOverApplet extends Applet {
    private final PBase base;

    protected PubOverApplet() {
        base = new PSub();
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new PubOverApplet();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        base.touch();
        Util.setShort(apdu.getBuffer(), (short) 0, base.val());
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
