package com.example.exp.client;

import com.example.exp.lib.LibUtil;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.Util;

/** Applet fixture that imports the {@code com.example.exp.lib} library package. */
public class ClientApplet extends Applet {

    /**
     * Installs the applet.
     *
     * @param bArray  install parameters
     * @param bOffset offset
     * @param bLength length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ClientApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        short value = LibUtil.twice(buf[ISO7816.OFFSET_P1]);
        Util.setShort(buf, (short) 0, value);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
