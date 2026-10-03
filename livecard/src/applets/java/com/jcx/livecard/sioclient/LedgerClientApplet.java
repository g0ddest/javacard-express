package com.jcx.livecard.sioclient;

import com.jcx.livecard.sioserver.Ledger;
import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Shareable;
import javacard.framework.Util;

/**
 * Client of the cross-package test: its package imports the server package through the server's export file;
 * the server's instance AID comes from the install parameters ('C9').
 *
 * <pre>
 * INS 71 P1  get the Ledger with JCSystem.getAppletShareableInterfaceObject(server, 1) and add P1 -> new total
 * INS 72     ask for parameter 2, which the server refuses -> 01 when the object is null
 * </pre>
 */
public class LedgerClientApplet extends Applet {
    private final byte[] server;
    private final byte serverLength;

    private LedgerClientApplet(byte[] bArray, short bOffset) {
        short off = bOffset;
        off = (short) (off + 1 + (bArray[off] & 0xFF));
        off = (short) (off + 1 + (bArray[off] & 0xFF));
        serverLength = bArray[off];
        server = new byte[16];
        Util.arrayCopy(bArray, (short) (off + 1), server, (short) 0, serverLength);
        register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    /**
     * Installs the applet; the application specific parameters are the server's instance AID.
     *
     * @param bArray  install parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new LedgerClientApplet(bArray, bOffset);
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
        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x71: add(apdu, buf[ISO7816.OFFSET_P1]); return;
            case 0x72: refused(apdu); return;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private Shareable service(byte parameter) {
        AID aid = JCSystem.lookupAID(server, (short) 0, serverLength);
        if (aid == null) {
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
        }
        return JCSystem.getAppletShareableInterfaceObject(aid, parameter);
    }

    private void add(APDU apdu, byte delta) {
        Ledger ledger = (Ledger) service((byte) 1);
        if (ledger == null) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        byte[] buf = apdu.getBuffer();
        Util.setShort(buf, (short) 0, ledger.add(delta));
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

    private void refused(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        buf[0] = service((byte) 2) == null ? (byte) 1 : (byte) 0;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }
}
