package com.example;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;

/**
 * Minimal test applet extending {@code javacard.framework.Applet}.
 * Used by maven-plugin tests for applet discovery and conversion.
 */
public class TestApplet extends Applet {

    @SuppressWarnings("java:S1172") // Parameters required by JavaCard API contract
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new TestApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        // no-op stub
    }
}
