package com.example;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Simple test applet for ClassFileReader tests.
 * Uses typical JavaCard patterns: fields, install, process, try/catch.
 */
public class TestApplet extends Applet {

    private static final byte INS_GET = 0x01;
    private static final byte INS_PUT = 0x02;

    private final byte[] storage;
    private short dataLen;

    protected TestApplet(byte[] bArray, short bOffset, byte bLength) {
        storage = new byte[64];
        dataLen = 0;
        register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new TestApplet(bArray, bOffset, bLength);
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();

        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_GET:
                apdu.setOutgoing();
                apdu.setOutgoingLength(dataLen);
                apdu.sendBytesLong(storage, (short) 0, dataLen);
                break;
            case INS_PUT:
                short len = apdu.setIncomingAndReceive();
                Util.arrayCopy(buf, ISO7816.OFFSET_CDATA, storage, (short) 0, len);
                dataLen = len;
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
