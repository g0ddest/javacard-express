package com.example.arrayops;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Test applet exercising array operations, type conversions,
 * and various bytecodes not covered by other test applets.
 * Used to improve BytecodeTranslator test coverage.
 */
@SuppressWarnings("all")
public class ArrayOpsApplet extends Applet {

    private byte[] byteBuffer;
    private short dataLen;

    protected ArrayOpsApplet() {
        byteBuffer = new byte[32];
        dataLen = 0;
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ArrayOpsApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];

        // tableswitch with contiguous case values
        switch (ins) {
            case 0x01:
                handleStore(buf);
                break;
            case 0x02:
                handleLoad(buf);
                break;
            case 0x03:
                handleArithmetic(buf);
                break;
            case 0x04:
                handleConversions(buf);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void handleStore(byte[] buf) {
        // bastore and baload
        short len = (short) (buf[ISO7816.OFFSET_LC] & 0xFF);
        Util.arrayCopy(buf, ISO7816.OFFSET_CDATA, byteBuffer, (short) 0, len);
        dataLen = len;
    }

    private void handleLoad(byte[] buf) {
        Util.arrayCopy(byteBuffer, (short) 0, buf, (short) 0, dataLen);
    }

    private void handleArithmetic(byte[] buf) {
        short a = (short) (buf[0] & 0xFF);
        short b = (short) (buf[1] & 0xFF);

        // Various arithmetic covering sdiv, srem, sshl, sshr, sushr, sor, sxor
        short div = (short) (a / (short) 2);
        short rem = (short) (a % (short) 3);
        short shl = (short) (a << 1);
        short shr = (short) (a >> 1);
        short ushr = (short) (a >>> 1);
        short or = (short) (a | b);
        short xor = (short) (a ^ b);

        buf[0] = (byte) div;
        buf[1] = (byte) rem;
        buf[2] = (byte) shl;
        buf[3] = (byte) shr;
        buf[4] = (byte) ushr;
        buf[5] = (byte) or;
        buf[6] = (byte) xor;
    }

    private void handleConversions(byte[] buf) {
        // s2b conversion
        short val = (short) 0x1234;
        buf[0] = (byte) val;

        // Various stack and comparison ops
        short x = (short) (buf[0] & 0xFF);
        short y = (short) (buf[1] & 0xFF);
        if (x > y) {
            buf[2] = 1;
        } else if (x < y) {
            buf[2] = (byte) 0xFF;
        } else {
            buf[2] = 0;
        }
    }
}
