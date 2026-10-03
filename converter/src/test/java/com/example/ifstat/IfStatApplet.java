package com.example.ifstat;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;

/** Applet using the non-constant interface field of {@link Config}. */
public class IfStatApplet extends Applet implements Config {

    protected IfStatApplet() {
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new IfStatApplet();
    }

    @Override
    public short ping(short v) {
        return (short) (v + SIZE);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        buffer[ISO7816.OFFSET_CDATA] = (byte) (LOCK == null ? ping((short) 1) : 0);
        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, (short) 1);
    }
}
