package com.example.bytecode.intmode;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Regression fixture for a target with int support (JCVM 3.1 section 2.2.3.1): a static int field,
 * an int array, int locals, parameters and results mixed with the usual short code. Converted with
 * supportInt32, every method must verify: shorts and ints are distinct types on the JCVM
 * (an int takes two words, section 6.10.4), while javac uses int instructions for both.
 */
public class IntModeApplet extends Applet {

    private static int counter;
    private final int[] history = new int[4];
    private short calls;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new IntModeApplet().register();
    }

    static int checksum(byte[] buf, short off, short len) {
        int acc = 0;
        for (short i = 0; i < len; i++) {
            acc = acc * 31 + (buf[(short) (off + i)] & 0xFF);
        }
        return acc;
    }

    static short average(short a, short b) {
        return (short) ((a + b) / 2);
    }

    static short select(int key) {
        switch (key) {
            case 1:
                return 10;
            case 2:
                return 20;
            case 70000:
                return 30;
            default:
                return 0;
        }
    }

    public int next() {
        history[(short) (calls & 3)] = counter;
        calls++;
        return counter++;
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        short a = Util.getShort(buf, ISO7816.OFFSET_CDATA);
        short b = Util.getShort(buf, (short) (ISO7816.OFFSET_CDATA + 2));
        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x10:
                counter += 70000;
                break;
            case 0x20:
                counter = checksum(buf, ISO7816.OFFSET_CDATA, (short) 4);
                break;
            case 0x30:
                if (a + b > 100) {
                    counter = a + b;
                }
                break;
            case 0x40:
                counter = select(a) + next();
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        Util.setShort(buf, (short) 0, (short) (counter >> 16));
        Util.setShort(buf, (short) 2, (short) counter);
        Util.setShort(buf, (short) 4, average(a, b));
        buf[6] = (byte) (counter >>> 24);
        buf[7] = (byte) (counter != 0 ? 1 : 0);
        apdu.setOutgoingAndSend((short) 0, (short) 8);
    }
}
