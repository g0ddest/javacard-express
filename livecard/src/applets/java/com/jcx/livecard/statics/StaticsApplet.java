package com.jcx.livecard.statics;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Static fields the converter initializes in the StaticField component: byte, short, boolean and array
 * references with initializers, static final byte/short/boolean arrays (static final fields that are not
 * compile-time constants), a compile-time constant the converter inlines, and a static counter with the
 * default value. (A blank static final primitive assigned in a static initializer is not valid Java Card code:
 * only inline initializers are, see LIVE_CARD_TESTING.md, "Known issues".)
 *
 * <pre>
 * INS 40  all statics -> 12 1234 01 010203 A0A1 0102F00F7FFF 010001 0BEE 0000
 * INS 41  change smallValue, wideValue, enabled, table[2], counter, then as INS 40
 *         -> 13 1244 00 010209 A0A1 0102F00F7FFF 010001 0BEE 0001
 * </pre>
 */
public class StaticsApplet extends Applet {
    private static byte smallValue = 0x12;
    private static short wideValue = 0x1234;
    private static boolean enabled = true;
    private static byte[] table = {1, 2, 3};
    private static final byte[] BYTES = {(byte) 0xA0, (byte) 0xA1};
    private static final short[] SHORTS = {0x0102, (short) 0xF00F, 0x7FFF};
    private static final boolean[] BOOLS = {true, false, true};
    private static final short INLINED = 0x0BEE;
    private static short counter;

    private StaticsApplet() {
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
        new StaticsApplet();
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
        byte ins = buf[ISO7816.OFFSET_INS];
        if (ins == 0x41) {
            change();
        } else if (ins != 0x40) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        apdu.setOutgoingAndSend((short) 0, report(buf));
    }

    private static void change() {
        smallValue++;
        wideValue += 0x10;
        enabled = !enabled;
        table[2] = 9;
        counter++;
    }

    private static short report(byte[] buf) {
        buf[0] = smallValue;
        Util.setShort(buf, (short) 1, wideValue);
        buf[3] = enabled ? (byte) 1 : (byte) 0;
        Util.arrayCopyNonAtomic(table, (short) 0, buf, (short) 4, (short) 3);
        Util.arrayCopyNonAtomic(BYTES, (short) 0, buf, (short) 7, (short) 2);
        for (short i = 0; i < 3; i++) {
            Util.setShort(buf, (short) (9 + 2 * i), SHORTS[i]);
            buf[(short) (15 + i)] = BOOLS[i] ? (byte) 1 : (byte) 0;
        }
        Util.setShort(buf, (short) 18, INLINED);
        Util.setShort(buf, (short) 20, counter);
        return 22;
    }
}
