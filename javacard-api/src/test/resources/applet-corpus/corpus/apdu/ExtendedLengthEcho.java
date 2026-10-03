package corpus.apdu;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacardx.apdu.ExtendedLength;
import javacardx.apdu.util.APDUUtil;

/**
 * Corpus applet: echoes extended-length command data. Exercises the APDU API of Java Card 3.0.5 that
 * extended-length applets depend on (incoming length, C-data offset, CLA decoding, media/protocol masks).
 */
public class ExtendedLengthEcho extends Applet implements ExtendedLength {

    private static final short MAX_DATA = 2048;
    private static final byte INS_ECHO = 0x10;

    private final byte[] data;

    private ExtendedLengthEcho() {
        data = JCSystem.makeTransientByteArray(MAX_DATA, JCSystem.CLEAR_ON_DESELECT);
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ExtendedLengthEcho();
    }

    public boolean select() {
        if (!reSelectingApplet()) {
            Util.arrayFillNonAtomic(data, (short) 0, MAX_DATA, (byte) 0);
        }
        return true;
    }

    public void process(APDU apdu) {
        byte[] buf = apdu.getBuffer();
        if (selectingApplet() || buf[ISO7816.OFFSET_INS] == ISO7816.INS_SELECT) {
            return;
        }
        checkClass(apdu, buf[ISO7816.OFFSET_CLA]);
        if (buf[ISO7816.OFFSET_INS] != INS_ECHO) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        short length = receiveAll(apdu, buf);
        apdu.setOutgoing();
        apdu.setOutgoingLength(length);
        apdu.sendBytesLong(data, (short) 0, length);
    }

    private static void checkClass(APDU apdu, byte cla) {
        if (!apdu.isValidCLA() || !APDUUtil.isValidCLA(cla)) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        if (apdu.isSecureMessagingCLA() || apdu.isCommandChainingCLA() || !apdu.isISOInterindustryCLA()) {
            ISOException.throwIt(ISO7816.SW_SECURE_MESSAGING_NOT_SUPPORTED);
        }
        if (APDU.getCLAChannel() != APDUUtil.getCLAChannel(cla)) {
            ISOException.throwIt(ISO7816.SW_LOGICAL_CHANNEL_NOT_SUPPORTED);
        }
        byte media = (byte) (APDU.getProtocol() & APDU.PROTOCOL_MEDIA_MASK);
        if (media == APDU.PROTOCOL_MEDIA_HCI_APDU_GATE && APDU.getCurrentAPDU() != apdu) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
    }

    private short receiveAll(APDU apdu, byte[] buf) {
        short received = apdu.setIncomingAndReceive();
        short total = apdu.getIncomingLength();
        if (total > MAX_DATA) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        short offset = apdu.getOffsetCdata();
        short copied = 0;
        while (received > 0) {
            Util.arrayCopyNonAtomic(buf, offset, data, copied, received);
            copied += received;
            received = apdu.receiveBytes(offset);
        }
        if (APDU.getCurrentAPDUBuffer() != buf || offset < ISO7816.OFFSET_CDATA
                || offset > ISO7816.OFFSET_EXT_CDATA) {
            ISOException.throwIt(ISO7816.SW_UNKNOWN);
        }
        return copied;
    }
}
