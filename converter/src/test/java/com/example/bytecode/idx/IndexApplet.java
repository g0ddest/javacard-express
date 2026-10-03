package com.example.bytecode.idx;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.Util;

/**
 * Regression fixture: array index and size expressions that javac computes with int instructions
 * but whose value is a short for every value of their operands (JCVM 3.1 sections 2.2.1.1.8 and
 * 2.2.3.1): a nibble of a byte, a short masked with a small constant, a byte plus one, the sum of
 * two masked bytes, a byte nibble times two as array size, a cast sum and a conditional choice of
 * shorts. They convert without int support, verify, and need no int instruction when the target
 * supports int.
 */
public class IndexApplet extends Applet {

    private final byte[] digits;

    private IndexApplet() {
        digits = new byte[16];
        for (short i = 0; i < 16; i++) {
            digits[i] = (byte) (i < 10 ? 0x30 + i : 0x37 + i);
        }
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new IndexApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        byte b = buf[ISO7816.OFFSET_P1];
        byte c = buf[ISO7816.OFFSET_P2];
        short x = Util.getShort(buf, ISO7816.OFFSET_CDATA);
        buf[0] = digits[(b >> 4) & 0x0F];
        buf[1] = digits[b & 0x0F];
        buf[2] = buf[x & 3];
        buf[3] = buf[c + 1];
        buf[4] = buf[(b & 0x7F) + (c & 0x7F)];
        byte[] pair = new byte[(c & 0x0F) * 2];
        buf[5] = (byte) pair.length;
        buf[6] = buf[(short) (x + 1)];
        buf[7] = buf[b > 0 ? x : (short) 0];
        apdu.setOutgoingAndSend((short) 0, (short) 8);
    }
}
