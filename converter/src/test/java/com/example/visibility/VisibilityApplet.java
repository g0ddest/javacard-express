package com.example.visibility;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Test applet exercising method visibility combinations for Descriptor.cap token assignment.
 * Contains private static methods, package-private static methods, and package-private constructor.
 * Per JCVM spec Section 6.14 Table 6-39, all non-exported methods must have token 0xFF.
 */
public class VisibilityApplet extends Applet {

    private static final byte INS_COMPUTE = 0x01;

    private final byte[] buffer;

    /** Package-private constructor: must get token 0xFF in Descriptor.cap */
    VisibilityApplet(byte[] bArray, short bOffset, byte bLength) {
        buffer = new byte[32];
        register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new VisibilityApplet(bArray, bOffset, bLength);
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();

        if (buf[ISO7816.OFFSET_INS] == INS_COMPUTE) {
            short val = computeValue(buf[ISO7816.OFFSET_P1]);
            Util.setShort(buf, (short) 0, val);
            apdu.setOutgoingAndSend((short) 0, (short) 2);
        } else {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    /** Private static method: must get token 0xFF */
    private static short computeValue(byte input) {
        short result = clampByte(input);
        result = doubleValue(result);
        return result;
    }

    /** Private static method: must get token 0xFF */
    private static short clampByte(byte b) {
        short val = (short) (b & 0xFF);
        if (val > 100) {
            val = 100;
        }
        return val;
    }

    /** Private static method: must get token 0xFF */
    private static short doubleValue(short val) {
        return (short) (val * 2);
    }

    /** Package-private static method: must get token 0xFF */
    static short helperAdd(short a, short b) {
        return (short) (a + b);
    }
}
