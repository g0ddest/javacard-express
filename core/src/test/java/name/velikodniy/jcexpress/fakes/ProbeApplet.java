package name.velikodniy.jcexpress.fakes;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * Test applet that reports what the card side sees, used to check backends against the
 * specifications instead of against the implementation's own assumptions.
 *
 * <ul>
 *   <li>INS 10: returns the install data exactly as received by {@code install(bArray, bOffset, bLength)}</li>
 *   <li>INS 11: returns {@code CLA INS P1 P2 P3} as seen in the APDU buffer, then Ne (2 bytes, from
 *       {@code setOutgoing()}) and {@code APDU.getCLAChannel()} (at most Ne bytes)</li>
 *   <li>INS 12: increments a persistent counter and returns {@code [counter(2)][clearOnResetFlag(1)]},
 *       then sets the CLEAR_ON_RESET flag</li>
 *   <li>INS 13: returns P1P2 bytes of pattern data (at most Ne)</li>
 *   <li>INS 14 / 18 / 19: allocates P1P2 bytes of persistent / CLEAR_ON_DESELECT / CLEAR_ON_RESET memory</li>
 *   <li>INS 15: answers SW 6105 (five bytes available through GET RESPONSE)</li>
 *   <li>INS C0 (GET RESPONSE): returns {@code 01..05} and remembers the CLA it arrived with</li>
 *   <li>INS 16: returns the CLA of the last GET RESPONSE</li>
 *   <li>INS 17: answers 6C05 unless Ne == 5, then returns five bytes</li>
 * </ul>
 */
public class ProbeApplet extends Applet {

    private static final byte INS_INSTALL_DATA = 0x10;
    private static final byte INS_ECHO_HEADER = 0x11;
    private static final byte INS_COUNTER = 0x12;
    private static final byte INS_PATTERN = 0x13;
    private static final byte INS_ALLOC_PERSISTENT = 0x14;
    private static final byte INS_MORE_DATA = 0x15;
    private static final byte INS_LAST_GET_RESPONSE_CLA = 0x16;
    private static final byte INS_EXACT_LE = 0x17;
    private static final byte INS_ALLOC_DESELECT = 0x18;
    private static final byte INS_ALLOC_RESET = 0x19;
    private static final byte INS_GET_RESPONSE = (byte) 0xC0;

    private final byte[] installData;
    private final byte[] resetFlag;
    private final Object[] hold = new Object[16];
    private short holdIndex;
    private short counter;
    private byte lastGetResponseCla = (byte) 0xEE;

    private ProbeApplet(byte[] bArray, short bOffset, byte bLength) {
        installData = new byte[bLength];
        Util.arrayCopyNonAtomic(bArray, bOffset, installData, (short) 0, bLength);
        resetFlag = JCSystem.makeTransientByteArray((short) 1, JCSystem.CLEAR_ON_RESET);
    }

    /**
     * Installs the probe; it registers with the default AID supplied by the runtime.
     *
     * @param bArray  install data
     * @param bOffset offset of the install data
     * @param bLength length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ProbeApplet(bArray, bOffset, bLength).register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        switch (buf[ISO7816.OFFSET_INS]) {
            case INS_INSTALL_DATA -> send(apdu, installData);
            case INS_ECHO_HEADER -> echoHeader(apdu);
            case INS_COUNTER -> counter(apdu);
            case INS_PATTERN -> pattern(apdu);
            case INS_ALLOC_PERSISTENT -> hold[holdIndex++] = new byte[p1p2(buf)];
            case INS_ALLOC_DESELECT -> hold[holdIndex++] =
                    JCSystem.makeTransientByteArray(p1p2(buf), JCSystem.CLEAR_ON_DESELECT);
            case INS_ALLOC_RESET -> hold[holdIndex++] =
                    JCSystem.makeTransientByteArray(p1p2(buf), JCSystem.CLEAR_ON_RESET);
            case INS_MORE_DATA -> ISOException.throwIt((short) 0x6105);
            case INS_GET_RESPONSE -> getResponse(apdu);
            case INS_LAST_GET_RESPONSE_CLA -> send(apdu, new byte[]{lastGetResponseCla});
            case INS_EXACT_LE -> exactLe(apdu);
            default -> ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private static short p1p2(byte[] buf) {
        return Util.getShort(buf, ISO7816.OFFSET_P1);
    }

    private static void send(APDU apdu, byte[] data) {
        byte[] buf = apdu.getBuffer();
        Util.arrayCopyNonAtomic(data, (short) 0, buf, (short) 0, (short) data.length);
        apdu.setOutgoingAndSend((short) 0, (short) data.length);
    }

    private static void echoHeader(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        byte cla = buf[0];
        byte ins = buf[1];
        byte p1 = buf[2];
        byte p2 = buf[3];
        byte p3 = buf[4];
        byte channel = APDU.getCLAChannel();
        short ne = apdu.setOutgoing();
        buf[0] = cla;
        buf[1] = ins;
        buf[2] = p1;
        buf[3] = p2;
        buf[4] = p3;
        Util.setShort(buf, (short) 5, ne);
        buf[7] = channel;
        short len = ne < 8 ? ne : 8;
        apdu.setOutgoingLength(len);
        apdu.sendBytes((short) 0, len);
    }

    private void counter(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        counter++;
        Util.setShort(buf, (short) 0, counter);
        buf[2] = resetFlag[0];
        resetFlag[0] = 1;
        apdu.setOutgoingAndSend((short) 0, (short) 3);
    }

    private static void pattern(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short n = p1p2(buf);
        short ne = apdu.setOutgoing();
        if (n > ne) {
            n = ne;
        }
        apdu.setOutgoingLength(n);
        for (short i = 0; i < n; i++) {
            buf[0] = (byte) i;
            apdu.sendBytes((short) 0, (short) 1);
        }
    }

    private void getResponse(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        lastGetResponseCla = buf[ISO7816.OFFSET_CLA];
        for (short i = 0; i < 5; i++) {
            buf[i] = (byte) (i + 1);
        }
        apdu.setOutgoingAndSend((short) 0, (short) 5);
    }

    private static void exactLe(APDU apdu) {
        short ne = apdu.setOutgoing();
        if (ne != 5) {
            ISOException.throwIt((short) 0x6C05);
        }
        apdu.setOutgoingLength((short) 5);
        apdu.sendBytes((short) 0, (short) 5);
    }
}
