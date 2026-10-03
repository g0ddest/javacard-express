package com.example.bytecode.statthis;

import javacard.framework.APDU;
import javacard.framework.Applet;

/**
 * Regression fixture: in a static method local 0 is the first parameter, not {@code this};
 * getfield_&lt;t&gt;_this / putfield_&lt;t&gt;_this must not be used there
 * (JCVM 3.1 sections 7.5.21 and 7.5.76: "The currently executing method must be an instance method").
 */
public class StatThisApplet extends Applet {

    short counter;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new StatThisApplet().register();
    }

    static short peek(StatThisApplet other) {
        return other.counter;
    }

    static void bump(StatThisApplet other, short d) {
        other.counter = d;
    }

    short own() {
        return counter;
    }

    void setOwn(short d) {
        counter = d;
    }

    @Override
    public void process(APDU apdu) {
        bump(this, (short) 5);
        byte[] buf = apdu.getBuffer();
        buf[0] = (byte) peek(this);
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }
}
