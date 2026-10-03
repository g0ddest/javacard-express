package com.example.jdk;

import java.util.Arrays;

import javacard.framework.APDU;
import javacard.framework.Applet;

/** Calls a Java SE library class that does not exist on a card. */
public class JdkApplet extends Applet {

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new JdkApplet().register();
    }

    public void process(APDU apdu) {
        Arrays.fill(apdu.getBuffer(), (byte) 0);
    }
}
