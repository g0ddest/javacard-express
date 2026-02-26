package name.velikodniy.jcexpress;

import javacard.framework.*;

/**
 * Test applet that implements PIN verification, change, and unblock.
 *
 * <p>Default PIN: "1234" (ASCII), 3 retries.
 * Default PUK: "12345678" (ASCII), 5 retries.</p>
 *
 * <p>Supported commands:</p>
 * <ul>
 *   <li>VERIFY (INS=0x20, P2=pin ref): verify PIN</li>
 *   <li>CHANGE REFERENCE DATA (INS=0x24, P1=0x00): change PIN with old+new</li>
 *   <li>RESET RETRY COUNTER (INS=0x2C): unblock with PUK+new PIN</li>
 *   <li>GET DATA (INS=0x01): returns "OK" if PIN is verified</li>
 * </ul>
 */
public class PinApplet extends Applet {

    private static final byte INS_VERIFY = (byte) 0x20;
    private static final byte INS_CHANGE = (byte) 0x24;
    private static final byte INS_UNBLOCK = (byte) 0x2C;
    private static final byte INS_GET_DATA = (byte) 0x01;

    private static final byte PIN_REF = 0x01;
    private static final byte MAX_PIN_SIZE = 8;
    private static final byte PIN_TRIES = 3;
    private static final byte PUK_TRIES = 5;
    private static final byte PUK_SIZE = 8;

    private final OwnerPIN pin;
    private final OwnerPIN puk;

    private static final byte[] DEFAULT_PIN = {'1', '2', '3', '4'};
    private static final byte[] DEFAULT_PUK = {'1', '2', '3', '4', '5', '6', '7', '8'};
    private static final byte[] OK = {'O', 'K'};

    private PinApplet() {
        pin = new OwnerPIN(PIN_TRIES, MAX_PIN_SIZE);
        pin.update(DEFAULT_PIN, (short) 0, (byte) DEFAULT_PIN.length);
        puk = new OwnerPIN(PUK_TRIES, MAX_PIN_SIZE);
        puk.update(DEFAULT_PUK, (short) 0, (byte) DEFAULT_PUK.length);
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new PinApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buffer = apdu.getBuffer();
        byte ins = buffer[ISO7816.OFFSET_INS];

        switch (ins) {
            case INS_VERIFY:
                processVerify(apdu);
                break;
            case INS_CHANGE:
                processChange(apdu);
                break;
            case INS_UNBLOCK:
                processUnblock(apdu);
                break;
            case INS_GET_DATA:
                processGetData(apdu);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void processVerify(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short bytesRead = apdu.setIncomingAndReceive();

        if (bytesRead == 0) {
            // Empty VERIFY = retry counter query
            if (pin.getTriesRemaining() == 0) {
                ISOException.throwIt((short) 0x6983);
            }
            ISOException.throwIt((short) (0x63C0 | pin.getTriesRemaining()));
        }

        if (!pin.check(buffer, ISO7816.OFFSET_CDATA, (byte) bytesRead)) {
            short remaining = pin.getTriesRemaining();
            if (remaining == 0) {
                ISOException.throwIt((short) 0x6983);
            }
            ISOException.throwIt((short) (0x63C0 | remaining));
        }
        // Success: SW=9000
    }

    private void processChange(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short bytesRead = apdu.setIncomingAndReceive();
        byte p1 = buffer[ISO7816.OFFSET_P1];

        if (p1 == 0x00) {
            // Old PIN + New PIN — each is half the data
            // We use fixed 4-byte PIN, so first 4 = old, rest = new
            short halfLen = (short) (bytesRead / 2);
            if (!pin.check(buffer, ISO7816.OFFSET_CDATA, (byte) halfLen)) {
                short remaining = pin.getTriesRemaining();
                if (remaining == 0) {
                    ISOException.throwIt((short) 0x6983);
                }
                ISOException.throwIt((short) (0x63C0 | remaining));
            }
            pin.update(buffer, (short) (ISO7816.OFFSET_CDATA + halfLen), (byte) (bytesRead - halfLen));
        } else {
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);
        }
    }

    private void processUnblock(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short bytesRead = apdu.setIncomingAndReceive();

        // PUK (8 bytes) + new PIN
        if (bytesRead <= PUK_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }

        if (!puk.check(buffer, ISO7816.OFFSET_CDATA, PUK_SIZE)) {
            short remaining = puk.getTriesRemaining();
            if (remaining == 0) {
                ISOException.throwIt((short) 0x6983);
            }
            ISOException.throwIt((short) (0x63C0 | remaining));
        }

        short newPinLen = (short) (bytesRead - PUK_SIZE);
        pin.update(buffer, (short) (ISO7816.OFFSET_CDATA + PUK_SIZE), (byte) newPinLen);
        pin.resetAndUnblock();
    }

    private void processGetData(APDU apdu) {
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        apdu.setOutgoing();
        apdu.setOutgoingLength((short) OK.length);
        apdu.sendBytesLong(OK, (short) 0, (short) OK.length);
    }
}
