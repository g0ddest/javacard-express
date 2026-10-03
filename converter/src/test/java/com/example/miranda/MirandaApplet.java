package com.example.miranda;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;

/** Calls the interface method through the abstract class. */
public class MirandaApplet extends Applet {
    private final Base base;

    protected MirandaApplet() {
        base = new Impl();
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new MirandaApplet();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        buffer[ISO7816.OFFSET_CDATA] = (byte) (base.run((short) 1) + base.twice((short) 2));
        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, (short) 1);
    }
}
