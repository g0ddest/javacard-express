package com.example.bytecode.catch0;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.SystemException;

/**
 * Regression fixture: no instance fields, and the first translated method (the constructor) has
 * a typed catch, so the catch type class reference is the first constant pool entry. Index 0
 * means "finally" (JCVM 3.1 section 6.10.3), so the entry must move to a non-zero index.
 */
public class Catch0Applet extends Applet {

    private Catch0Applet() {
        try {
            JCSystem.requestObjectDeletion();
        } catch (SystemException e) {
            // object deletion not supported: ignore ONLY SystemException
        }
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new Catch0Applet();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        try {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA); // must reach the terminal as 6A80
        } catch (SystemException e) {
            ISOException.throwIt(ISO7816.SW_UNKNOWN);
        }
    }
}
