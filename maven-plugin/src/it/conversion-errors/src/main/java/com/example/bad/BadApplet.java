package com.example.bad;

import javacard.framework.APDU;
import javacard.framework.Applet;

/** Uses long, which Java Card does not support (JCVM 3.1 2.2.1.3). */
public class BadApplet extends Applet {

    private long counter;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new BadApplet().register();
    }

    public void process(APDU apdu) {
        counter = counter + 1L;
    }
}
