package com.example.bytecode.arrlen;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

/**
 * Regression fixture: {@code array.length} must translate to JCVM {@code arraylength}
 * (JCVM 3.1 section 7.5.8); the JDK ClassFile API models it as an OperatorInstruction.
 */
public class ArrLenApplet extends Applet {

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ArrLenApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short n = (short) buf.length;
        Util.setShort(buf, (short) 0, n);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

    static short lengthOf(short[] values) {
        return (short) values.length;
    }
}
