package com.example.helper;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * The most common real-world applet shape: package-private instance helper methods in the
 * applet class (private virtual tokens, §4.3.7.6) and a package-private helper class
 * (no class token, 0xFF in the Descriptor, §4.3.7.2 / §6.14.2).
 */
public class HelperApplet extends Applet {
    private final Counter counter;

    HelperApplet() {
        counter = new Counter();
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelperApplet();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x10: handleIncrement(); break;
            case 0x20: handleRead(apdu, buf); break;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    void handleIncrement() {
        counter.value = Counter.next(counter.value);
    }

    void handleRead(APDU apdu, byte[] buf) {
        Util.setShort(buf, (short) 0, counter.value);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
