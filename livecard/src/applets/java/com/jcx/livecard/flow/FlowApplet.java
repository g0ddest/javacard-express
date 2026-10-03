package com.jcx.livecard.flow;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Control flow the converter has to widen or table: forward branches over more than 127 bytes of code
 * (if/else, goto), a backward loop branch over more than 127 bytes, a dense switch (tableswitch) and a sparse
 * switch (lookupswitch). Arithmetic is on shorts and wraps around.
 *
 * <pre>
 * INS 30 P1 0|1  long if/else: acc = 1, then 20 x (acc * 3 + 7) or 20 x (acc * 5 - 3)
 * INS 31 P1      dense switch on 0..7 -> 11 x (P1 + 1), otherwise FFFF
 * INS 32 P1P2    sparse switch on -30000, -5, 1, 100, 1000, 30000 -> 1..6, otherwise 0
 * INS 33         loop of 10 rounds, each 18 x (acc * 3 + 7), acc starting at 1
 * </pre>
 */
public class FlowApplet extends Applet {

    private FlowApplet() {
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
        new FlowApplet();
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
        Util.setShort(buf, (short) 0, dispatch(buf));
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

    private static short dispatch(byte[] buf) {
        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x30: return longBranches(buf[ISO7816.OFFSET_P1]);
            case 0x31: return dense(buf[ISO7816.OFFSET_P1]);
            case 0x32: return sparse(Util.getShort(buf, ISO7816.OFFSET_P1));
            case 0x33: return longLoop();
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED); return 0;
        }
    }

    /** Both branches are longer than 127 bytes of bytecode. */
    private static short longBranches(byte selector) {
        short acc = 1;
        if (selector == 0) {
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
        } else {
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
            acc = (short) (acc * 5 - 3);
        }
        return acc;
    }

    private static short dense(byte value) {
        switch (value) {
            case 0: return 11;
            case 1: return 22;
            case 2: return 33;
            case 3: return 44;
            case 4: return 55;
            case 5: return 66;
            case 6: return 77;
            case 7: return 88;
            default: return (short) 0xFFFF;
        }
    }

    private static short sparse(short value) {
        switch (value) {
            case -30000: return 1;
            case -5: return 2;
            case 1: return 3;
            case 100: return 4;
            case 1000: return 5;
            case 30000: return 6;
            default: return 0;
        }
    }

    /** The loop body is longer than 127 bytes, so the backward branch is wide. */
    private static short longLoop() {
        short acc = 1;
        for (short round = 0; round < 10; round++) {
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
            acc = (short) (acc * 3 + 7);
        }
        return acc;
    }
}
