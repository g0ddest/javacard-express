package com.jcx.livecard.intops;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * The optional int type (converted with int support): arithmetic, shifts, an int array, a persistent int field
 * and conversions between int, short and byte. Every int goes out big-endian in 4 bytes.
 *
 * <pre>
 * INS 8A  100000 * -7, 100000 / -7, 100000 % -7, 100000 + (-7)^3
 * INS 8B  0x80000001 &gt;&gt; 4, &gt;&gt;&gt; 4, &lt;&lt; 3, and (x &lt; 0 ? 1 : 0)
 * INS 8C  int array of 4: a[i] = i * 0x10001 + 0x7FFF0000, then a.length, a[3] and the sum
 * INS 8D  persistent counter += 0x12345 -&gt; counter
 * INS 8E  (short) 0x12345678, (byte) 0x12345678, (int) (short) -2, and P1 widened to int times 0x01000000
 * </pre>
 */
public class IntApplet extends Applet {
    private static final int BIG = 100000;
    private static final short WORD = 4;
    private int counter;

    private IntApplet() {
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
        new IntApplet();
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
            case (byte) 0x8A: send(apdu, arithmetic(buf, (byte) -7)); return;
            case (byte) 0x8B: send(apdu, shifts(buf, 0x80000001)); return;
            case (byte) 0x8C: send(apdu, array(buf)); return;
            case (byte) 0x8D: send(apdu, count(buf)); return;
            case (byte) 0x8E: send(apdu, conversions(buf, buf[ISO7816.OFFSET_P1])); return;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private static short arithmetic(byte[] buf, byte divisor) {
        int b = divisor;
        put(buf, (short) 0, BIG * b);
        put(buf, (short) 4, BIG / b);
        put(buf, (short) 8, BIG % b);
        put(buf, (short) 12, BIG + b * b * b);
        return 16;
    }

    private static short shifts(byte[] buf, int x) {
        put(buf, (short) 0, x >> 4);
        put(buf, (short) 4, x >>> 4);
        put(buf, (short) 8, x << 3);
        put(buf, (short) 12, x < 0 ? 1 : 0);
        return 16;
    }

    private static short array(byte[] buf) {
        int[] values = new int[4];
        int sum = 0;
        for (short i = 0; i < values.length; i++) {
            values[i] = i * 0x10001 + 0x7FFF0000;
            sum += values[i];
        }
        put(buf, (short) 0, values.length);
        put(buf, (short) 4, values[3]);
        put(buf, (short) 8, sum);
        return 12;
    }

    private short count(byte[] buf) {
        counter += 0x12345;
        put(buf, (short) 0, counter);
        return WORD;
    }

    private static short conversions(byte[] buf, byte p1) {
        int value = 0x12345678;
        short low = (short) value;
        byte lowest = (byte) value;
        short minusTwo = -2;
        int widened = p1;
        put(buf, (short) 0, low);
        put(buf, (short) 4, lowest);
        put(buf, (short) 8, minusTwo);
        put(buf, (short) 12, widened * 0x01000000);
        return 16;
    }

    private static void put(byte[] buf, short off, int value) {
        buf[off] = (byte) (value >> 24);
        buf[(short) (off + 1)] = (byte) (value >> 16);
        buf[(short) (off + 2)] = (byte) (value >> 8);
        buf[(short) (off + 3)] = (byte) value;
    }

    private static void send(APDU apdu, short length) {
        apdu.setOutgoingAndSend((short) 0, length);
    }
}
