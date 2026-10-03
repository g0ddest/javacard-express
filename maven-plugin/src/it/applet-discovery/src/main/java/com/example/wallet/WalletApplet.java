package com.example.wallet;

import javacard.framework.APDU;
import javacard.framework.Util;

/** An applet that extends the abstract base applet (indirect subclass of Applet). */
public class WalletApplet extends BaseApplet {

    private static final byte INS_CREDIT = (byte) 0x50;
    private static final byte INS_BALANCE = (byte) 0x52;

    private short balance;

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new WalletApplet().register();
    }

    protected boolean dispatch(byte ins, APDU apdu) {
        if (ins == INS_CREDIT) {
            balance = Amounts.add(balance, (short) 1);
            return true;
        }
        if (ins == INS_BALANCE) {
            byte[] buffer = apdu.getBuffer();
            Util.setShort(buffer, (short) 0, balance);
            apdu.setOutgoingAndSend((short) 0, (short) 2);
            return true;
        }
        return false;
    }
}
