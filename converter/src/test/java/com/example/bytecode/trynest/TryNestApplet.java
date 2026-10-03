package com.example.bytecode.trynest;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;
import javacard.security.CryptoException;

/**
 * Regression fixture for the exception handler table (JCVM 3.1 section 6.10.3): nested
 * try/catch/finally, multi-catch, handlers inside a loop, sequential try blocks and a rethrow.
 */
public class TryNestApplet extends Applet {

    private short count;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new TryNestApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        short sw = 0;
        try {
            try {
                if (buf[ISO7816.OFFSET_P1] == 1) {
                    ISOException.throwIt((short) 0x6A81);
                }
                if (buf[ISO7816.OFFSET_P1] == 2) {
                    CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
                }
                if (buf[ISO7816.OFFSET_P1] == 3) {
                    byte[] n = null;
                    n[0] = 1;
                }
            } catch (CryptoException e) {
                sw = (short) (0x6F00 | e.getReason());
            } finally {
                buf[1] = 0x55;
            }
        } catch (ISOException e) {
            sw = e.getReason();
        } catch (NullPointerException | ArrayIndexOutOfBoundsException e) {
            sw = 0x6F01;
        }
        for (short i = 0; i < 3; i++) {
            try {
                if (i == 1) {
                    ISOException.throwIt((short) 1);
                }
            } catch (ISOException e) {
                sw++;
            }
        }
        try {
            rethrow(buf[ISO7816.OFFSET_P2]);
        } catch (ISOException e) {
            count++;
            throw e; // rethrow to the JCRE
        }
        Util.setShort(buf, (short) 2, sw);
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }

    private void rethrow(byte p2) {
        try {
            if (p2 == 7) {
                ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
            }
        } finally {
            count--;
        }
    }
}
