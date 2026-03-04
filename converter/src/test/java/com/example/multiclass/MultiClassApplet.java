package com.example.multiclass;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Test applet with a helper class — exercises cross-class field access
 * and method calls within the same package.
 */
public class MultiClassApplet extends Applet {

    private static final byte INS_INCREMENT = 0x01;
    private static final byte INS_GET = 0x02;
    private static final byte INS_RESET = 0x03;

    private final Helper helper;

    protected MultiClassApplet() {
        helper = new Helper();
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new MultiClassApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();

        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_INCREMENT:
                short val = helper.increment();
                Util.setShort(buf, (short) 0, val);
                apdu.setOutgoingAndSend((short) 0, (short) 2);
                break;
            case INS_GET:
                Util.setShort(buf, (short) 0, helper.getCounter());
                apdu.setOutgoingAndSend((short) 0, (short) 2);
                break;
            case INS_RESET:
                helper.reset();
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
