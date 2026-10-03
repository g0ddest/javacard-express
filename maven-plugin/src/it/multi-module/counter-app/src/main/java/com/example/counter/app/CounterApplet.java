package com.example.counter.app;

import com.example.counter.lib.Counters;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

/** An applet that uses the library package com.example.counter.lib. */
public class CounterApplet extends Applet {

    private short count;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new CounterApplet().register();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        count = Counters.increment(count);
        byte[] buffer = apdu.getBuffer();
        Util.setShort(buffer, (short) 0, count);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
