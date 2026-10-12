package com.example.state;

import javacard.framework.APDU;
import javacard.framework.Applet;
import org.globalplatform.GPSystem;

/** Reports its life cycle state, which it reads through the GlobalPlatform Card API. */
public class StateApplet extends Applet {

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new StateApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        buffer[0] = GPSystem.getCardContentState();
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }
}
