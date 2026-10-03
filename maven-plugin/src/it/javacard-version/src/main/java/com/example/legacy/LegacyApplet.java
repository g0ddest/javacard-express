package com.example.legacy;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/** An applet for a Java Card 2.2.2 card. */
public class LegacyApplet extends Applet {

    private short counter;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new LegacyApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_INS] != (byte) 0x10) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        counter++;
        buf[0] = (byte) (counter >> 8);
        buf[1] = (byte) counter;
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
