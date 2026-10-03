package com.example.blankfinal;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

/**
 * A blank static final primitive assigned in the static initializer (§2.2.4.6). It is not a
 * compile-time constant, so it can neither be inlined nor be listed in the Descriptor component
 * (§6.14). The converter must reject it instead of producing a CAP file the off-card verifier
 * refuses.
 */
public class BlankFinalApplet extends Applet {
    private static final short BLANK;

    static {
        BLANK = 0x0BEE;
    }

    protected BlankFinalApplet() {
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new BlankFinalApplet();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        Util.setShort(apdu.getBuffer(), (short) 0, BLANK);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
