package com.example.intops;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Test applet exercising int arithmetic operations.
 * Used to verify ACC_INT support — when supportInt32=true,
 * converter should emit int-specific JCVM opcodes (iadd, imul, etc.).
 */
public class IntOpsApplet extends Applet {

    private int counter;

    protected IntOpsApplet() {
        counter = 0;
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new IntOpsApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];

        switch (ins) {
            case 0x01:
                // Add — exercises iadd
                counter = counter + 100;
                break;
            case 0x02:
                // Multiply — exercises imul
                counter = counter * 2;
                break;
            case 0x03:
                // Bitwise AND — exercises iand
                counter = counter & 0xFF;
                break;
            case 0x04:
                // Negate — exercises ineg
                counter = -counter;
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }

        // Return counter as 4 bytes
        buf[0] = (byte) (counter >> 24);
        buf[1] = (byte) (counter >> 16);
        buf[2] = (byte) (counter >> 8);
        buf[3] = (byte) counter;
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }
}
