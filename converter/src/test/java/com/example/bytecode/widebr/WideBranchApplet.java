package com.example.bytecode.widebr;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;

/**
 * Regression fixture: the if block is longer than 127 bytes, so its conditional branch needs the
 * 2-byte offset form (JCVM 3.1 section 7.5.38 if_scmp&lt;cond&gt;_w).
 */
public class WideBranchApplet extends Applet {

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new WideBranchApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_P1] == 1) {
            buf[(short) 0] = (byte) (buf[(short) 1] ^ (byte) 0x00);
            buf[(short) 1] = (byte) (buf[(short) 2] ^ (byte) 0x01);
            buf[(short) 2] = (byte) (buf[(short) 3] ^ (byte) 0x02);
            buf[(short) 3] = (byte) (buf[(short) 4] ^ (byte) 0x03);
            buf[(short) 4] = (byte) (buf[(short) 5] ^ (byte) 0x04);
            buf[(short) 5] = (byte) (buf[(short) 6] ^ (byte) 0x05);
            buf[(short) 6] = (byte) (buf[(short) 7] ^ (byte) 0x06);
            buf[(short) 7] = (byte) (buf[(short) 8] ^ (byte) 0x07);
            buf[(short) 8] = (byte) (buf[(short) 9] ^ (byte) 0x08);
            buf[(short) 9] = (byte) (buf[(short) 10] ^ (byte) 0x09);
            buf[(short) 10] = (byte) (buf[(short) 11] ^ (byte) 0x0A);
            buf[(short) 11] = (byte) (buf[(short) 12] ^ (byte) 0x0B);
            buf[(short) 12] = (byte) (buf[(short) 13] ^ (byte) 0x0C);
            buf[(short) 13] = (byte) (buf[(short) 14] ^ (byte) 0x0D);
            buf[(short) 14] = (byte) (buf[(short) 15] ^ (byte) 0x0E);
            buf[(short) 15] = (byte) (buf[(short) 16] ^ (byte) 0x0F);
            buf[(short) 16] = (byte) (buf[(short) 17] ^ (byte) 0x10);
            buf[(short) 17] = (byte) (buf[(short) 18] ^ (byte) 0x11);
            buf[(short) 18] = (byte) (buf[(short) 19] ^ (byte) 0x12);
            buf[(short) 19] = (byte) (buf[(short) 20] ^ (byte) 0x13);
            buf[(short) 20] = (byte) (buf[(short) 21] ^ (byte) 0x14);
            buf[(short) 21] = (byte) (buf[(short) 22] ^ (byte) 0x15);
            buf[(short) 22] = (byte) (buf[(short) 23] ^ (byte) 0x16);
            buf[(short) 23] = (byte) (buf[(short) 24] ^ (byte) 0x17);
            buf[(short) 24] = (byte) (buf[(short) 25] ^ (byte) 0x18);
            buf[(short) 25] = (byte) (buf[(short) 26] ^ (byte) 0x19);
            buf[(short) 26] = (byte) (buf[(short) 27] ^ (byte) 0x1A);
            buf[(short) 27] = (byte) (buf[(short) 28] ^ (byte) 0x1B);
            buf[(short) 28] = (byte) (buf[(short) 29] ^ (byte) 0x1C);
            buf[(short) 29] = (byte) (buf[(short) 30] ^ (byte) 0x1D);
        }
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }
}
