package com.example.wallet.extra;

import javacard.framework.APDU;
import javacard.framework.Applet;

/** An applet of the sub-package com.example.wallet.extra: a different Java Card package. */
public class ExtraApplet extends Applet {

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ExtraApplet().register();
    }

    public void process(APDU apdu) {
    }
}
