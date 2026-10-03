package com.example.bytecode.castarr;

import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.Applet;

/**
 * Regression fixture: checkcast / instanceof against array types use atype 10..14
 * (JCVM 3.1 section 7.5.16 Table 7-2 and section 7.5.53).
 */
public class CastArrApplet extends Applet {

    private Object[] slots = new Object[3];

    CastArrApplet() {
        slots[0] = new byte[8];
        slots[1] = new short[4];
        slots[2] = new AID[1];
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new CastArrApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte[] a = (byte[]) slots[0];
        if (slots[1] instanceof short[]) {
            buf[0] = a[0];
        }
        AID[] aids = (AID[]) slots[2];
        if (slots[0] instanceof boolean[] || aids == null) {
            buf[1] = 1;
        }
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
