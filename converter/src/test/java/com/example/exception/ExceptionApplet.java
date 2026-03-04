package com.example.exception;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Test applet with try/catch blocks — exercises exception handler table
 * generation in the Method component.
 */
public class ExceptionApplet extends Applet {

    private short errorCount;

    protected ExceptionApplet() {
        errorCount = 0;
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ExceptionApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();

        try {
            short len = apdu.setIncomingAndReceive();
            if (len == 0) {
                ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
            }
            Util.arrayCopy(buf, ISO7816.OFFSET_CDATA, buf, (short) 0, len);
            apdu.setOutgoingAndSend((short) 0, len);
        } catch (ISOException e) {
            errorCount++;
            ISOException.throwIt(e.getReason());
        }
    }
}
