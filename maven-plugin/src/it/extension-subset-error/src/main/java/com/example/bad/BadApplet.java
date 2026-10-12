package com.example.bad;

import javacard.framework.APDU;
import javacard.framework.Applet;

public class BadApplet extends Applet {
    private long counter;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new BadApplet().register();
    }

    public void process(APDU apdu) {
        Object greeting = "hello";
    }
}
