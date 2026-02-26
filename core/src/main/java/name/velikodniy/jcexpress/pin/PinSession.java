package name.velikodniy.jcexpress.pin;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;

/**
 * Fluent helper for PIN operations on a smart card session.
 *
 * <p>Wraps {@link SmartCardSession} and provides convenient methods for
 * ISO 7816-4 PIN commands: VERIFY, CHANGE REFERENCE DATA, RESET RETRY COUNTER.</p>
 *
 * <p>Usage:</p>
 * <pre>
 * PinSession pin = PinSession.on(card);
 * pin.verify(1, "1234");
 * int retries = pin.retriesRemaining(1);
 * pin.change(1, "1234", "5678");
 * </pre>
 */
public final class PinSession {

    private static final int CLA = 0x00;
    private static final int INS_VERIFY = 0x20;
    private static final int INS_CHANGE = 0x24;
    private static final int INS_UNBLOCK = 0x2C;

    private final SmartCardSession session;
    private PinFormat format;

    private PinSession(SmartCardSession session) {
        this.session = session;
        this.format = PinFormat.ASCII;
    }

    /**
     * Creates a PIN session wrapper around the given smart card session.
     *
     * @param session the underlying session
     * @return a new PinSession
     */
    public static PinSession on(SmartCardSession session) {
        return new PinSession(session);
    }

    /**
     * Sets the PIN encoding format. Default is {@link PinFormat#ASCII}.
     *
     * @param format the encoding format
     * @return this session for chaining
     */
    public PinSession format(PinFormat format) {
        this.format = format;
        return this;
    }

    /**
     * Sends a VERIFY command (INS=0x20) for the given PIN reference.
     *
     * @param pinRef the PIN reference number (P2)
     * @param pin    the PIN as a digit string
     * @return the APDU response
     */
    public APDUResponse verify(int pinRef, String pin) {
        byte[] pinData = format.encode(pin);
        return session.send(CLA, INS_VERIFY, 0x00, pinRef, pinData);
    }

    /**
     * Returns the number of remaining PIN retries without attempting verification.
     *
     * <p>Sends a VERIFY command with empty data. The card responds with
     * SW=63CX where X is the remaining retry count, or SW=6983 if blocked.</p>
     *
     * @param pinRef the PIN reference number (P2)
     * @return remaining retry count, or 0 if blocked
     */
    public int retriesRemaining(int pinRef) {
        APDUResponse response = session.send(CLA, INS_VERIFY, 0x00, pinRef);
        int sw = response.sw();
        if (sw == 0x9000) {
            // PIN already verified in this session
            return -1;
        }
        if ((sw & 0xFFF0) == 0x63C0) {
            return sw & 0x0F;
        }
        if (sw == 0x6983) {
            return 0;
        }
        return -1;
    }

    /**
     * Returns true if the PIN is blocked (SW=6983).
     *
     * @param pinRef the PIN reference number (P2)
     * @return true if blocked
     */
    public boolean isBlocked(int pinRef) {
        APDUResponse response = session.send(CLA, INS_VERIFY, 0x00, pinRef);
        return response.sw() == 0x6983;
    }

    /**
     * Sends a CHANGE REFERENCE DATA command (INS=0x24) with old and new PIN.
     *
     * <p>The data field contains oldPIN || newPIN.</p>
     *
     * @param pinRef the PIN reference number (P2)
     * @param oldPin the current PIN
     * @param newPin the new PIN
     * @return the APDU response
     */
    public APDUResponse change(int pinRef, String oldPin, String newPin) {
        byte[] oldData = format.encode(oldPin);
        byte[] newData = format.encode(newPin);
        byte[] combined = new byte[oldData.length + newData.length];
        System.arraycopy(oldData, 0, combined, 0, oldData.length);
        System.arraycopy(newData, 0, combined, oldData.length, newData.length);
        return session.send(CLA, INS_CHANGE, 0x00, pinRef, combined);
    }

    /**
     * Sends a CHANGE REFERENCE DATA command (INS=0x24, P1=0x01) with new PIN only.
     *
     * <p>Used when the card allows setting a PIN without knowing the old one.</p>
     *
     * @param pinRef the PIN reference number (P2)
     * @param newPin the new PIN
     * @return the APDU response
     */
    public APDUResponse changeWithoutOldPin(int pinRef, String newPin) {
        byte[] newData = format.encode(newPin);
        return session.send(CLA, INS_CHANGE, 0x01, pinRef, newData);
    }

    /**
     * Sends a RESET RETRY COUNTER command (INS=0x2C) with PUK and new PIN.
     *
     * <p>The data field contains PUK || newPIN.</p>
     *
     * @param pinRef the PIN reference number (P2)
     * @param puk    the PUK (unblock key)
     * @param newPin the new PIN
     * @return the APDU response
     */
    public APDUResponse unblock(int pinRef, String puk, String newPin) {
        byte[] pukData = format.encode(puk);
        byte[] newData = format.encode(newPin);
        byte[] combined = new byte[pukData.length + newData.length];
        System.arraycopy(pukData, 0, combined, 0, pukData.length);
        System.arraycopy(newData, 0, combined, pukData.length, newData.length);
        return session.send(CLA, INS_UNBLOCK, 0x00, pinRef, combined);
    }
}
