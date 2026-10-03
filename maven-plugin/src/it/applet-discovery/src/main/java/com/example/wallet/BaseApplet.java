package com.example.wallet;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/** Common APDU dispatch; abstract, so it is not an applet itself (JCVM 3.1 6.6). */
public abstract class BaseApplet extends Applet {

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        if (!dispatch(buffer[ISO7816.OFFSET_INS], apdu)) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    /**
     * Handles one command.
     *
     * @param ins  the instruction byte
     * @param apdu the command
     * @return {@code false} if the instruction is not supported
     */
    protected abstract boolean dispatch(byte ins, APDU apdu);
}
