package com.example.bytecode.nest;

import javacard.framework.APDU;
import javacard.framework.Applet;

/**
 * Regression fixture: a nested class reads and writes private fields and calls a private method
 * and the private constructor of its outer class. javac 11 and later compile this as nestmate
 * access (JEP 181: getfield/putfield/invokevirtual/invokespecial naming the outer class), which
 * the Java Card platform does not have (JCVM 3.1 section 2.2.1.1.6); javac 10 and earlier
 * generated package-visible access$NNN bridges instead.
 */
public class NestApplet extends Applet {

    private byte[] data = new byte[4];
    private short secret = 7;

    private NestApplet() {
        register();
    }

    private short bump(short d) {
        secret += d;
        return secret;
    }

    /** Nested helper with direct access to the private members of the applet. */
    public static class Helper {

        /** Gives the helper a virtual method of its own. */
        public short id() {
            return 1;
        }

        static NestApplet create() {
            return new NestApplet();
        }

        static short run(NestApplet n) {
            n.data[0] = (byte) n.secret;
            n.secret = 3;
            return n.bump((short) 2);
        }
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        Helper.create();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        buf[0] = (byte) Helper.run(this);
        buf[1] = (byte) bump((short) 1);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
