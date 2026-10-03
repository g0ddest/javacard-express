package com.example.badclinit;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

/**
 * Static initializer outside the Java Card subset (§2.2.4.6): a method call in
 * {@code <clinit>}. The converter must reject it instead of silently dropping the value.
 */
public class BadClinitApplet extends Applet {
    static short value = Util.makeShort((byte) 0x12, (byte) 0x34);

    protected BadClinitApplet() {
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new BadClinitApplet();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        Util.setShort(apdu.getBuffer(), (short) 0, value);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
