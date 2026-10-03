package com.jcx.livecard.transactions;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Atomicity and transient memory: an aborted transaction restores a persistent field and array element, a
 * committed one keeps them; CLEAR_ON_DESELECT memory is cleared when another application is selected,
 * CLEAR_ON_RESET memory when the card is reset.
 *
 * <pre>
 * INS 55     value = 5, journal[0] = 0; begin; value = 7, journal[0] = 9; abort
 *            -> value, journal[0], transaction depth = 0005 00 00
 * INS 56     begin; value = 8, journal[0] = 3; depth inside; commit -> 0008 03 01
 * INS 57     value, journal[0]                                      -> persistent state
 * INS 58 P1  write P1 into the CLEAR_ON_DESELECT and CLEAR_ON_RESET arrays (no data)
 * INS 59     read both                                              -> COD COR
 * </pre>
 */
public class TransactionsApplet extends Applet {
    private short value;
    private final byte[] journal = new byte[2];
    private final byte[] onDeselect;
    private final byte[] onReset;

    private TransactionsApplet() {
        onDeselect = JCSystem.makeTransientByteArray((short) 2, JCSystem.CLEAR_ON_DESELECT);
        onReset = JCSystem.makeTransientByteArray((short) 2, JCSystem.CLEAR_ON_RESET);
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
        new TransactionsApplet();
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
            case 0x55: return aborted(buf);
            case 0x56: return committed(buf);
            case 0x57: return state(buf);
            case 0x58: return write(buf[ISO7816.OFFSET_P1]);
            case 0x59: return read(buf);
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED); return 0;
        }
    }

    private short aborted(byte[] buf) {
        value = 5;
        journal[0] = 0;
        JCSystem.beginTransaction();
        value = 7;
        journal[0] = 9;
        JCSystem.abortTransaction();
        state(buf);
        buf[3] = JCSystem.getTransactionDepth();
        return 4;
    }

    private short committed(byte[] buf) {
        JCSystem.beginTransaction();
        value = 8;
        journal[0] = 3;
        byte depth = JCSystem.getTransactionDepth();
        JCSystem.commitTransaction();
        state(buf);
        buf[3] = depth;
        return 4;
    }

    private short state(byte[] buf) {
        Util.setShort(buf, (short) 0, value);
        buf[2] = journal[0];
        return 3;
    }

    private short write(byte data) {
        onDeselect[0] = data;
        onReset[0] = data;
        return 0;
    }

    private short read(byte[] buf) {
        buf[0] = onDeselect[0];
        buf[1] = onReset[0];
        return 2;
    }
}
