package com.jcx.livecard.hello;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Real-card smoke applet: static array initializer, echo, short arithmetic, ISOException, persistence, transient
 * memory. Kept exactly as validated on the real card (step 2 of the real-card runs).
 */
public class HelloApplet extends Applet {
    private static final byte[] HELLO = {'H', 'e', 'l', 'l', 'o', ',', ' ', 'c', 'a', 'r', 'd'};
    private static final short HELLO_LEN = 11;

    private short counter;
    private final byte[] scratch;

    private HelloApplet() {
        scratch = JCSystem.makeTransientByteArray((short) 32, JCSystem.CLEAR_ON_DESELECT);
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelloApplet();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != (byte) 0x80) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x01:
                Util.arrayCopyNonAtomic(HELLO, (short) 0, buf, (short) 0, HELLO_LEN);
                apdu.setOutgoingAndSend((short) 0, HELLO_LEN);
                return;
            case 0x02:
                short len = apdu.setIncomingAndReceive();
                Util.arrayCopyNonAtomic(buf, ISO7816.OFFSET_CDATA, buf, (short) 0, len);
                apdu.setOutgoingAndSend((short) 0, len);
                return;
            case 0x03:
                short p1 = (short) (buf[ISO7816.OFFSET_P1] & 0xFF);
                short p2 = (short) (buf[ISO7816.OFFSET_P2] & 0xFF);
                Util.setShort(buf, (short) 0, (short) (p1 * p2 + 1000));
                buf[2] = (byte) (p1 - p2);
                apdu.setOutgoingAndSend((short) 0, (short) 3);
                return;
            case 0x04:
                ISOException.throwIt(Util.getShort(buf, ISO7816.OFFSET_P1));
                return;
            case 0x05:
                counter++;
                Util.setShort(buf, (short) 0, counter);
                apdu.setOutgoingAndSend((short) 0, (short) 2);
                return;
            case 0x06:
                Util.arrayFillNonAtomic(scratch, (short) 0, (short) 32, buf[ISO7816.OFFSET_P1]);
                Util.arrayCopyNonAtomic(scratch, (short) 0, buf, (short) 0, (short) 4);
                apdu.setOutgoingAndSend((short) 0, (short) 4);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
