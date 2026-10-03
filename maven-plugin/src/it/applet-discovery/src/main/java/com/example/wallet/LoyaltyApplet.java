package com.example.wallet;

import javacard.framework.APDU;
import javacard.framework.Applet;

/** A direct subclass of Applet. */
public class LoyaltyApplet extends Applet {

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new LoyaltyApplet().register();
    }

    public void process(APDU apdu) {
    }
}
