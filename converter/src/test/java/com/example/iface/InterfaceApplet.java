package com.example.iface;

import javacard.framework.APDU;
import javacard.framework.AID;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Shareable;
import javacard.framework.Util;

/**
 * Test applet implementing Shareable interface — exercises interface method
 * dispatch and getShareableInterfaceObject.
 */
public class InterfaceApplet extends Applet implements Shareable {

    private final byte[] sharedData;

    protected InterfaceApplet() {
        sharedData = new byte[16];
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new InterfaceApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];

        if (ins == (byte) 0x01) {
            short len = apdu.setIncomingAndReceive();
            Util.arrayCopy(buf, ISO7816.OFFSET_CDATA, sharedData, (short) 0, len);
        } else if (ins == (byte) 0x02) {
            Util.arrayCopy(sharedData, (short) 0, buf, (short) 0, (short) 16);
            apdu.setOutgoingAndSend((short) 0, (short) 16);
        } else {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    @Override
    public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
        return this;
    }
}
