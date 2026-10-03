package com.jcx.livecard.dispatch;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Virtual dispatch on the card: abstract and concrete methods, a three-level override chain with super calls,
 * package-private methods overridden in the same package, and calls through an interface with two
 * implementations.
 *
 * <pre>
 * INS 01 P1 0|1  describe() of a Square(2) / a Triangle          -> 002C / 003F
 * INS 02         value() of Base, Middle, Leaf through Base refs  -> 0001 000B 006F
 * INS 03         callHidden() of Base, Middle, Leaf               -> 0010 0020 0020
 * INS 04         next() three times on Up and Down (persistent)   -> 0003 FFFD, then 0006 FFFA
 * </pre>
 */
public class DispatchApplet extends Applet {
    private final Shape square = new Square((short) 2);
    private final Shape triangle = new Triangle();
    private final Base[] chain = {new Base(), new Middle(), new Leaf()};
    private final Counter[] counters = {new Up(), new Down()};

    private DispatchApplet() {
        register();
    }

    /**
     * Installs the applet.
     *
     * @param bArray  install parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new DispatchApplet();
    }

    /**
     * Dispatches the commands.
     *
     * @param apdu the command
     */
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        apdu.setOutgoingAndSend((short) 0, dispatch(buf));
    }

    private short dispatch(byte[] buf) {
        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x01: return describe(buf, buf[ISO7816.OFFSET_P1]);
            case 0x02: return values(buf);
            case 0x03: return hidden(buf);
            case 0x04: return count(buf);
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED); return 0;
        }
    }

    private short describe(byte[] buf, byte which) {
        Shape shape = which == 0 ? square : triangle;
        Util.setShort(buf, (short) 0, shape.describe());
        return 2;
    }

    private short values(byte[] buf) {
        for (short i = 0; i < 3; i++) {
            Util.setShort(buf, (short) (i * 2), chain[i].value());
        }
        return 6;
    }

    private short hidden(byte[] buf) {
        for (short i = 0; i < 3; i++) {
            Util.setShort(buf, (short) (i * 2), chain[i].callHidden());
        }
        return 6;
    }

    private short count(byte[] buf) {
        short up = 0;
        short down = 0;
        for (short i = 0; i < 3; i++) {
            up = counters[0].next();
            down = counters[1].next();
        }
        Util.setShort(buf, (short) 0, up);
        Util.setShort(buf, (short) 2, down);
        return 4;
    }
}
