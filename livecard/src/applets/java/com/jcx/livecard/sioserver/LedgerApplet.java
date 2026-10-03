package com.jcx.livecard.sioserver;

import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Shareable;
import javacard.framework.Util;

/**
 * Server of the cross-package test: shares {@link Ledger} with parameter 1 (other parameters get null). Its
 * package is converted with an Export component and an export file that the client package imports.
 *
 * <pre>
 * INS 73  the total -> 2 bytes
 * </pre>
 *
 * <p>(The test applets avoid INS '6X' and '9X', which T=0 cannot carry.)</p>
 */
public class LedgerApplet extends Applet implements Ledger {
    private short total;

    private LedgerApplet() {
    }

    /**
     * Installs the applet under the instance AID of the install parameters.
     *
     * @param bArray  install parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new LedgerApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    /**
     * Answers the total.
     *
     * @param apdu the command
     */
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_INS] != 0x73) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        Util.setShort(buf, (short) 0, total);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

    /**
     * Adds to the total (runs in the server's context when called through the shareable interface).
     *
     * @param delta the amount
     * @return the new total
     */
    public short add(short delta) {
        total += delta;
        return total;
    }

    /**
     * Shares the ledger for parameter 1.
     *
     * @param clientAID the client applet
     * @param parameter the requested service
     * @return this applet for parameter 1, otherwise null
     */
    public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
        return parameter == 1 ? this : null;
    }
}
