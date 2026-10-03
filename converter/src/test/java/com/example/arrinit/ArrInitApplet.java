package com.example.arrinit;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Static array initializers and non-default static values: javac compiles them into
 * {@code <clinit>}, which a Java Card VM never executes. The converter must turn them into
 * array_init entries and non_default_values of the Static Field component (§2.2.4.6, §6.11).
 */
public class ArrInitApplet extends Applet {
    static final byte[] HELLO = {'H', 'e', 'l', 'l', 'o'};
    private static final short[] SHORTS = {1, -2, 0x7FFF};
    static boolean[] flags = {true, false, true};
    static final byte[] ZEROS = new byte[3];
    static byte counter = 5;

    protected ArrInitApplet() {
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ArrInitApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_INS] != 1) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        Util.arrayCopyNonAtomic(HELLO, (short) 0, buf, (short) 0, (short) 5);
        Util.setShort(buf, (short) 5, SHORTS[2]);
        buf[7] = (byte) (flags[0] ? 1 : 0);
        buf[8] = ZEROS[1];
        buf[9] = counter;
        apdu.setOutgoingAndSend((short) 0, (short) 10);
    }
}
