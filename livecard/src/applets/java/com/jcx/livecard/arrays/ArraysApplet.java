package com.jcx.livecard.arrays;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Arrays on the card: {@code length} of byte, short, boolean and Object arrays, {@code buf[off++] = x},
 * compound assignments and post-increments on array elements and fields (dup_x forms), checkcast of array
 * elements, and the Util copy, fill and compare methods.
 *
 * <pre>
 * INS 20  lengths of byte[5], short[3], boolean[4], Object[2]       -> 0005 0003 0004 0002
 * INS 21  buf[off++] = 3 * i for i 0..5, then buf[off++] = off     -> 00 03 06 09 0C 0F 07
 * INS 22  shorts[0] += 5; shorts[1]++; shorts[2] = shorts[0]++;
 *         total += shorts[2]; flags[1] = !flags[1]                  -> 0006 0001 0005 0005 01, then
 *                                                                     000C 0002 000B 0010 00
 * INS 23  Util.arrayCopy, arrayCopyNonAtomic, arrayFillNonAtomic,
 *         arrayCompare                                              -> 20 30 40 50 00 EE EE EE 00
 * INS 24  sum of the Box values in Object[], array length           -> 0007 0002
 * INS 25  a field as index: slots[cursor++] twice, slots[++cursor],
 *         tallies[cursor - 2] += cursor, previous = cursor--       -> 0A0B000C 0002 0003 0003, then
 *                                                                     0A0B000C 0002 0003 0006
 * </pre>
 */
public class ArraysApplet extends Applet {
    private static final byte[] SOURCE = {0x10, 0x20, 0x30, 0x40, 0x50};

    private final byte[] bytes = new byte[5];
    private final short[] shorts = new short[3];
    private final boolean[] flags = new boolean[4];
    private final Object[] boxes = {new Box((short) 3), new Box((short) 4)};
    private final byte[] slots = new byte[4];
    private final short[] tallies = new short[2];
    private short total;
    private short cursor;

    private ArraysApplet() {
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
        new ArraysApplet();
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
            case 0x20: return lengths(buf);
            case 0x21: return postIncrementIndex(buf);
            case 0x22: return elementUpdates(buf);
            case 0x23: return utilities(buf);
            case 0x24: return objects(buf);
            case 0x25: return fieldIndexes(buf);
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED); return 0;
        }
    }

    private short lengths(byte[] buf) {
        Util.setShort(buf, (short) 0, (short) bytes.length);
        Util.setShort(buf, (short) 2, (short) shorts.length);
        Util.setShort(buf, (short) 4, (short) flags.length);
        Util.setShort(buf, (short) 6, (short) boxes.length);
        return 8;
    }

    private static short postIncrementIndex(byte[] buf) {
        short off = 0;
        for (byte i = 0; i < 6; i++) {
            buf[off++] = (byte) (i * 3);
        }
        buf[off++] = (byte) off;
        return off;
    }

    private short elementUpdates(byte[] buf) {
        shorts[0] += 5;
        shorts[1]++;
        shorts[2] = shorts[0]++;
        total += shorts[2];
        flags[1] = !flags[1];
        Util.setShort(buf, (short) 0, shorts[0]);
        Util.setShort(buf, (short) 2, shorts[1]);
        Util.setShort(buf, (short) 4, shorts[2]);
        Util.setShort(buf, (short) 6, total);
        buf[8] = flags[1] ? (byte) 1 : (byte) 0;
        return 9;
    }

    private short utilities(byte[] buf) {
        Util.arrayCopy(SOURCE, (short) 1, bytes, (short) 0, (short) 4);
        Util.arrayCopyNonAtomic(bytes, (short) 0, buf, (short) 0, (short) 5);
        Util.arrayFillNonAtomic(buf, (short) 5, (short) 3, (byte) 0xEE);
        buf[8] = Util.arrayCompare(bytes, (short) 0, SOURCE, (short) 1, (short) 4);
        return 9;
    }

    private short fieldIndexes(byte[] buf) {
        cursor = 0;
        slots[cursor++] = 0x0A;
        slots[cursor++] = 0x0B;
        slots[++cursor] = 0x0C;
        tallies[(short) (cursor - 2)] += cursor;
        short previous = cursor--;
        Util.arrayCopyNonAtomic(slots, (short) 0, buf, (short) 0, (short) 4);
        Util.setShort(buf, (short) 4, cursor);
        Util.setShort(buf, (short) 6, previous);
        Util.setShort(buf, (short) 8, tallies[1]);
        return 10;
    }

    private short objects(byte[] buf) {
        short sum = 0;
        for (short i = 0; i < boxes.length; i++) {
            sum += ((Box) boxes[i]).value;
        }
        Util.setShort(buf, (short) 0, sum);
        Util.setShort(buf, (short) 2, (short) boxes.length);
        return 4;
    }
}
