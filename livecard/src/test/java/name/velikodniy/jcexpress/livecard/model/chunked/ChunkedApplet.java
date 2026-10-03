package name.velikodniy.jcexpress.livecard.model.chunked;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Applet that answers long data the way PIV and OpenPGP applets do (ISO/IEC 7816-4:2005 5.1.3): to a short Le it
 * sends at most Ne bytes and announces the rest with '61XX'; GET RESPONSE (INS 'C0', in any class) sends the next
 * part. The data is {@code 00 01 02 ...} (the offset modulo 256). Java Card subset only, in a package of its own (the
 * GlobalPlatform backends convert it from the test classes).
 *
 * <pre>
 * INS 40  answers P1-P2 bytes of data in parts ('61XX' while more is left)
 * INS C0  GET RESPONSE: the next part
 * INS 42  answers 01 02 03 04 05 when Ne is 5, else '6C05' (wrong Le, 5 bytes available)
 * </pre>
 */
public class ChunkedApplet extends Applet {

    private static final short EXACT = 5;

    /** [0] the length of the data, [1] the bytes sent so far. */
    private final short[] progress;

    private ChunkedApplet() {
        progress = JCSystem.makeTransientShortArray((short) 2, JCSystem.CLEAR_ON_DESELECT);
    }

    /**
     * Installs an instance under the AID of the install data (Java Card install method).
     *
     * @param bArray  the installation parameters
     * @param bOffset their offset
     * @param bLength their length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ChunkedApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        byte ins = buffer[ISO7816.OFFSET_INS];
        if (ins == 0x40) {
            progress[0] = Util.getShort(buffer, ISO7816.OFFSET_P1);
            progress[1] = 0;
            sendPart(apdu);
        } else if (ins == (byte) 0xC0) {
            sendPart(apdu);
        } else if (ins == 0x42) {
            sendExact(apdu);
        } else {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
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
        progress[1] = (short) (progress[1] + length);
        remaining = (short) (remaining - length);
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
