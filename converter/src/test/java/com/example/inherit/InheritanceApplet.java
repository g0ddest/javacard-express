package com.example.inherit;

import javacard.framework.APDU;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Concrete leaf class in the inheritance chain: Base → Middle → Inheritance.
 * Exercises multi-level inheritance, virtual method overrides,
 * and dispatch table generation.
 */
public class InheritanceApplet extends MiddleApplet {

    private static final byte INS_GET_VERSION = 0x01;
    private static final byte INS_GET_FEATURE = 0x02;

    protected InheritanceApplet() {
        version = 3;
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new InheritanceApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];

        if (ins == INS_GET_VERSION) {
            Util.setShort(buf, (short) 0, getVersion());
            apdu.setOutgoingAndSend((short) 0, (short) 2);
        } else if (ins == INS_GET_FEATURE) {
            Util.setShort(buf, (short) 0, getFeatureLevel());
            apdu.setOutgoingAndSend((short) 0, (short) 2);
        } else {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    @Override
    public short getFeatureLevel() {
        return (short) 42;
    }
}
