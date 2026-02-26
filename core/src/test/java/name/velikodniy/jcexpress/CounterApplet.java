package name.velikodniy.jcexpress;

import javacard.framework.*;

/**
 * Test applet that maintains a persistent counter.
 *
 * <p>Used to demonstrate multi-applet scenarios and data isolation.
 * Each instance has its own independent counter that persists across
 * card resets (but not runtime resets).</p>
 *
 * <p>Supported commands (CLA=0x80):</p>
 * <ul>
 *   <li>INS 0x01 (INCREMENT): increments the counter by 1, returns new value as 4 bytes</li>
 *   <li>INS 0x02 (GET): returns the current counter value as 4 bytes (big-endian)</li>
 *   <li>INS 0x03 (RESET): resets the counter to 0</li>
 * </ul>
 */
public class CounterApplet extends Applet {

    private static final byte INS_INCREMENT = 0x01;
    private static final byte INS_GET = 0x02;
    private static final byte INS_RESET = 0x03;

    /** Persistent counter stored in EEPROM. */
    private int counter;

    private CounterApplet() {
        counter = 0;
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new CounterApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buffer = apdu.getBuffer();
        byte ins = buffer[ISO7816.OFFSET_INS];

        switch (ins) {
            case INS_INCREMENT:
                counter++;
                sendCounter(apdu);
                break;
            case INS_GET:
                sendCounter(apdu);
                break;
            case INS_RESET:
                counter = 0;
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void sendCounter(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        buffer[0] = (byte) ((counter >> 24) & 0xFF);
        buffer[1] = (byte) ((counter >> 16) & 0xFF);
        buffer[2] = (byte) ((counter >> 8) & 0xFF);
        buffer[3] = (byte) (counter & 0xFF);
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }
}
