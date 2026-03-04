package com.example.statics;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * Test applet with various typed static fields for StaticFieldComponent testing.
 * Exercises all segments of the static field image:
 *   Segment 2: reference fields (Object, byte[])
 *   Segment 3: default-value primitives (zero-initialized)
 *   Segment 4: non-default-value primitives (explicit initial values)
 */
@SuppressWarnings("all")
public class StaticsApplet extends Applet {

    // Compile-time constants (should be EXCLUDED from static field image)
    static final byte CONST_BYTE = 0x42;
    static final short CONST_SHORT = 0x1234;

    // Segment 2: reference-type static fields (initialized to null)
    static byte[] staticBuffer;
    static Object staticRef;

    // Segment 3: default-value primitive fields (zero-initialized)
    static byte defaultByte;
    static short defaultShort;
    static boolean defaultBool;

    // Segment 4: non-default primitive fields (explicit initial values)
    static byte initByte = 0x0A;
    static short initShort = 100;
    static int initInt = 0x12345678;

    protected StaticsApplet() {
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new StaticsApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) return;
        ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
    }
}
