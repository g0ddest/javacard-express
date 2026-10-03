package com.jcx.livecard.params;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/** Real-card applet: keeps its GP install parameters (C9 value) and reports its own instance AID. */
public class ParamsApplet extends Applet {
    private final byte[] params;
    private final short paramsLen;

    private ParamsApplet(byte[] bArray, short bOffset, byte bLength) {
        // JCRE install layout: [Li][instance AID][Lc][control info][La][application data]
        short off = bOffset;
        off = (short) (off + 1 + (bArray[off] & 0xFF));
        off = (short) (off + 1 + (bArray[off] & 0xFF));
        short la = (short) (bArray[off] & 0xFF);
        off++;
        params = new byte[(short) (la == 0 ? 1 : la)];
        paramsLen = la;
        Util.arrayCopy(bArray, off, params, (short) 0, la);
        register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ParamsApplet(bArray, bOffset, bLength);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x01:
                Util.arrayCopyNonAtomic(params, (short) 0, buf, (short) 0, paramsLen);
                apdu.setOutgoingAndSend((short) 0, paramsLen);
                return;
            case 0x02:
                byte len = JCSystem.getAID().getBytes(buf, (short) 0);
                apdu.setOutgoingAndSend((short) 0, len);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
