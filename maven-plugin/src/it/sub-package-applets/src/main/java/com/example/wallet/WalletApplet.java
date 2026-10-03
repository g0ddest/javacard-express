package com.example.wallet;

import javacard.framework.APDU;
import javacard.framework.Applet;

/** The applet of package com.example.wallet. */
public class WalletApplet extends Applet {

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new WalletApplet().register();
    }

    public void process(APDU apdu) {
    }
}
