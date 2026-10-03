package name.velikodniy.jcexpress.model;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Test applet that answers long data the way PIV and OpenPGP applets do (ISO/IEC 7816-4:2005 5.1.3): to a short Le
 * it sends at most Ne bytes and announces the rest with '61XX'; GET RESPONSE (INS 'C0', in any class) sends the next
 * part. The data is {@code 00 01 02 ...} (the offset modulo 256).
 *
 * <pre>
 * INS 40  answers P1-P2 bytes of data in parts ('61XX' while more is left)
 * INS C0  GET RESPONSE: the next part
 * INS 42  answers 01 02 03 04 05 when Ne is 5, else '6C05' (wrong Le, 5 bytes available)
 * </pre>
 */
public final class ChunkedApplet extends Applet {

    /** The exact length INS 42 wants. */
    private static final short EXACT = 5;

    /** [0] the length of the data, [1] the bytes sent so far. */
    private final short[] progress = JCSystem.makeTransientShortArray((short) 2, JCSystem.CLEAR_ON_DESELECT);

    private ChunkedApplet() {
        register();
    }

    /**
     * Installs the applet (Java Card install method).
     *
     * @param bArray  the installation parameters
     * @param bOffset their offset
     * @param bLength their length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ChunkedApplet();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        switch (buffer[ISO7816.OFFSET_INS]) {
            case 0x40 -> {
                progress[0] = Util.getShort(buffer, ISO7816.OFFSET_P1);
                progress[1] = 0;
                sendPart(apdu);
            }
            case (byte) 0xC0 -> sendPart(apdu);
            case 0x42 -> sendExact(apdu);
            default -> ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void sendPart(APDU apdu) {
        short ne = apdu.setOutgoing();
        short remaining = (short) (progress[0] - progress[1]);
        short length = remaining < ne ? remaining : ne;
        byte[] buffer = apdu.getBuffer();
        for (short i = 0; i < length; i++) {
            buffer[i] = (byte) (progress[1] + i);
        }
        apdu.setOutgoingLength(length);
        apdu.sendBytes((short) 0, length);
        progress[1] += length;
        remaining -= length;
        if (remaining > 0) {
            ISOException.throwIt((short) (0x6100 | (remaining > 0xFF ? 0 : remaining)));
        }
    }

    private static void sendExact(APDU apdu) {
        short ne = apdu.setOutgoing();
        if (ne != EXACT) {
            ISOException.throwIt((short) (0x6C00 | EXACT));
        }
        byte[] buffer = apdu.getBuffer();
        for (short i = 0; i < EXACT; i++) {
            buffer[i] = (byte) (i + 1);
        }
        apdu.setOutgoingLength(EXACT);
        apdu.sendBytes((short) 0, EXACT);
    }
}
