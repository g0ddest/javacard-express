package com.example.bytecode.dupx;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;

/**
 * Regression fixture for JVM dup_x1 / dup_x2 (JCVM 3.1 section 7.5.18 dup_x, mn operand).
 * javac emits dup_x1 for a field increment used as a value and dup_x2 for chained array
 * stores and for an array element increment used as a value.
 */
public class DupXApplet extends Applet {

    private byte[] log = new byte[32];
    private short ptr;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new DupXApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        if (ptr >= 32) {
            ptr = 0;
        }
        log[ptr++] = buf[ISO7816.OFFSET_INS];
        short v = ++ptr;
        buf[0] = (byte) v;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    /** {@code buf[0] = buf[1] = 5} and {@code mem[i]++} as a value both use dup_x2. */
    static byte chained(byte[] buf, byte[] mem) {
        buf[0] = buf[1] = (byte) 5;
        short i = 2;
        byte v = mem[i]++;
        return v;
    }

    /** Field increment used as a value: dup_x1. */
    short postIncrement() {
        return ptr++;
    }
}
