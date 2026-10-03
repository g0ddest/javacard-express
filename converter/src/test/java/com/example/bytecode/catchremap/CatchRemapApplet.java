package com.example.bytecode.catchremap;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.security.CryptoException;
import javacardx.crypto.Cipher;

/**
 * Regression fixture: the catch type class reference is created before the instance field
 * references, so the constant pool reorder (instance fields first, JCVM 3.1 section 6.8) moves
 * it; the handler's catch_type_index must follow (section 6.10.3).
 */
public class CatchRemapApplet extends Applet {

    private Cipher cipher;
    private short errors;

    private CatchRemapApplet() {
        try {
            cipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
        } catch (CryptoException e) {
            cipher = null; // algorithm not supported: only CryptoException must be caught
        }
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new CatchRemapApplet();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        try {
            if (cipher == null) {
                ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        } catch (CryptoException e) {
            errors++;
        }
    }
}
