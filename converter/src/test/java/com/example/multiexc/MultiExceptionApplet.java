package com.example.multiexc;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.CardRuntimeException;

/**
 * Test applet with multiple catch blocks for different exception types.
 * Exercises exception handler table with multiple catch type entries.
 */
@SuppressWarnings("all")
public class MultiExceptionApplet extends Applet {

    private short errorCode;

    protected MultiExceptionApplet() {
        errorCode = 0;
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new MultiExceptionApplet();
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
        } catch (ISOException e) {
            errorCode = e.getReason();
            ISOException.throwIt(errorCode);
        } catch (CardRuntimeException e) {
            errorCode = e.getReason();
            ISOException.throwIt((short) 0x6F00);
        }
    }
}
