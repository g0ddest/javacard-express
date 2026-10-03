package com.example.nometh;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

/** Uses classes that declare no virtual methods of their own. */
public class NoMethApplet extends Applet {
    private final Holder holder;
    private final MyException error;

    NoMethApplet() {
        holder = new Holder();
        holder.data = new byte[4];
        error = new MyException((short) 0x6A80);
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new NoMethApplet();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        holder.len++;
        if (holder.len > 100) {
            throw error;
        }
        Util.setShort(buf, (short) 0, (short) (holder.len + error.getReason()));
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
