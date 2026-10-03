package com.jcx.livecard.apduio;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * APDU input and output beyond setOutgoingAndSend: a 256-byte response (Le '00') sent with sendBytesLong, a
 * response sent in chunks with setOutgoing/setOutgoingLength/sendBytes, and up to 255 command data bytes
 * received with setIncomingAndReceive and receiveBytes (case 3; the count and sum are read back with INS 38).
 *
 * <pre>
 * INS 35      256 bytes 01 04 07 .. FE (byte i = 3 * i + 1)
 * INS 36      40 bytes 00..27, sent as five chunks of 8
 * INS 37 data receive the data, keep the byte count and the sum of the bytes (no response data)
 * INS 38      the kept count and sum, e.g. 00FF 7E81 after 255 bytes 00..FE
 * </pre>
 */
public class ApduIoApplet extends Applet {
    private static final short LONG_RESPONSE = 256;
    private static final short CHUNK = 8;
    private static final short CHUNKS = 5;
    private final byte[] pattern = new byte[LONG_RESPONSE];
    private short received;
    private short sum;

    private ApduIoApplet() {
        for (short i = 0; i < LONG_RESPONSE; i++) {
            pattern[i] = (byte) (3 * i + 1);
        }
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
        new ApduIoApplet();
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
        switch (apdu.getBuffer()[ISO7816.OFFSET_INS]) {
            case 0x35: sendLong(apdu); return;
            case 0x36: sendChunks(apdu); return;
            case 0x37: receive(apdu); return;
            case 0x38: report(apdu); return;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void sendLong(APDU apdu) {
        apdu.setOutgoing();
        apdu.setOutgoingLength(LONG_RESPONSE);
        apdu.sendBytesLong(pattern, (short) 0, LONG_RESPONSE);
    }

    private static void sendChunks(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        apdu.setOutgoing();
        apdu.setOutgoingLength((short) (CHUNK * CHUNKS));
        for (short chunk = 0; chunk < CHUNKS; chunk++) {
            for (short i = 0; i < CHUNK; i++) {
                buf[i] = (byte) (chunk * CHUNK + i);
            }
            apdu.sendBytes((short) 0, CHUNK);
        }
    }

    private void receive(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        short count = 0;
        short total = 0;
        short read = apdu.setIncomingAndReceive();
        while (read > 0) {
            for (short i = 0; i < read; i++) {
                total += (short) (buf[(short) (ISO7816.OFFSET_CDATA + i)] & 0xFF);
            }
            count += read;
            read = apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }
        received = count;
        sum = total;
    }

    private void report(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        Util.setShort(buf, (short) 0, received);
        Util.setShort(buf, (short) 2, sum);
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }
}
