package name.velikodniy.jcexpress.pin;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;

import java.util.Arrays;
import java.util.OptionalInt;

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
 * OptionalInt retries = pin.retries(1);
 * pin.change(1, "1234", "5678");
 *
 * // an applet with proprietary PIN commands and a PIN padded like the PIV Card Application's
 * card.pin().cla(0x80).padTo(8, 0xFF).verify(0x80, "123456");
 * </pre>
 *
 * <p>The settings ({@link #format(PinFormat)}, {@link #cla(int)}, {@link #padTo(int, int)}) apply to every
 * command of this helper; {@link SmartCardSession#pin()} creates a new helper with the defaults.</p>
 */
public final class PinSession {

    private static final int INS_VERIFY = 0x20;
    private static final int INS_CHANGE = 0x24;
    private static final int INS_UNBLOCK = 0x2C;
    private static final int SW_NO_ERROR = 0x9000;
    private static final int SW_BLOCKED = 0x6983;

    private final SmartCardSession session;
    private PinFormat format;
    private int cla;
    private int padLength;
    private byte padByte;

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
     * Sets the class byte of the PIN commands. Default {@code '00'}, the interindustry class of ISO/IEC 7816-4
     * (5.1.1); applets whose PIN commands are proprietary use {@code '80'}.
     *
     * @param cla the CLA byte: {@code 0x00} to {@code 0xFF}, or a {@code byte} constant of the applet
     *            ({@code -128} to {@code 127}, taken as its unsigned value)
     * @return this session for chaining
     * @throws IllegalArgumentException if {@code cla} is outside {@code -128} to {@code 0xFF}
     */
    public PinSession cla(int cla) {
        this.cla = unsignedByte(cla, "CLA");
        return this;
    }

    /**
     * Pads every PIN (and PUK) to {@code length} bytes with {@code padByte} after the {@link #format(PinFormat)
     * format} encoded it. The PIV Card Application, for example, expects its PIN padded with {@code 'FF'} to 8
     * bytes (NIST SP 800-73-4 Part 2): {@code padTo(8, 0xFF)}. Without this setting a PIN is sent as encoded.
     *
     * @param length  the length of the padded PIN, 1 to 255 bytes
     * @param padByte the pad byte: {@code 0x00} to {@code 0xFF}, or a {@code byte} constant
     * @return this session for chaining
     * @throws IllegalArgumentException if {@code length} or {@code padByte} is out of range; a PIN that encodes to
     *                                  more than {@code length} bytes is rejected when it is used
     */
    public PinSession padTo(int length, int padByte) {
        if (length < 1 || length > 0xFF) {
            throw new IllegalArgumentException("padTo length must be 1 to 255 bytes, got " + length);
        }
        this.padByte = (byte) unsignedByte(padByte, "pad byte");
        this.padLength = length;
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
        byte[] pinData = encode(pin);
        return session.send(cla, INS_VERIFY, 0x00, pinRef, pinData);
    }

    /**
     * Returns the number of further allowed retries the card reports for a PIN, without attempting verification:
     * a VERIFY command without data (ISO/IEC 7816-4:2005 7.5.6), answered {@code '63CX'} with X retries, or
     * {@code '6983'} when the PIN is blocked.
     *
     * @param pinRef the PIN reference number (P2)
     * @return the retries ({@code 0} when blocked), or empty when the card reports no counter: {@code '9000'}
     *         (verification not required, e.g. the PIN is verified, see {@link #isVerified(int)}) or another status
     *         word
     */
    public OptionalInt retries(int pinRef) {
        int sw = emptyVerify(pinRef);
        if ((sw & 0xFFF0) == 0x63C0) {
            return OptionalInt.of(sw & 0x0F);
        }
        return sw == SW_BLOCKED ? OptionalInt.of(0) : OptionalInt.empty();
    }

    /**
     * Returns whether a PIN needs no verification now, typically because it was verified: a VERIFY command without
     * data answered {@code '9000'} (ISO/IEC 7816-4:2005 7.5.6).
     *
     * @param pinRef the PIN reference number (P2)
     * @return true if the card answers {@code '9000'}
     */
    public boolean isVerified(int pinRef) {
        return emptyVerify(pinRef) == SW_NO_ERROR;
    }

    /**
     * Returns the number of remaining PIN retries without attempting verification.
     *
     * <p>Sends a VERIFY command with empty data. The card responds with
     * SW=63CX where X is the remaining retry count, or SW=6983 if blocked.</p>
     *
     * @param pinRef the PIN reference number (P2)
     * @return remaining retry count, 0 if blocked, and -1 when the card reports no counter: when it answers
     *         {@code '9000'} (the PIN is verified or needs no verification) and for any other status word;
     *         {@link #retries(int)} and {@link #isVerified(int)} tell these cases apart
     */
    public int retriesRemaining(int pinRef) {
        return retries(pinRef).orElse(-1);
    }

    /**
     * Returns true if the PIN is blocked (SW=6983).
     *
     * @param pinRef the PIN reference number (P2)
     * @return true if blocked
     */
    public boolean isBlocked(int pinRef) {
        return emptyVerify(pinRef) == SW_BLOCKED;
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
        return session.send(cla, INS_CHANGE, 0x00, pinRef, concat(encode(oldPin), encode(newPin)));
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
        return session.send(cla, INS_CHANGE, 0x01, pinRef, encode(newPin));
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
        return session.send(cla, INS_UNBLOCK, 0x00, pinRef, concat(encode(puk), encode(newPin)));
    }

    /** Sends a VERIFY command without data (ISO/IEC 7816-4:2005 7.5.6) and returns the status word. */
    private int emptyVerify(int pinRef) {
        return session.send(cla, INS_VERIFY, 0x00, pinRef).sw();
    }

    /** The PIN as the format encodes it, padded when {@link #padTo(int, int)} is set. */
    private byte[] encode(String pin) {
        byte[] encoded = format.encode(pin);
        if (padLength == 0) {
            return encoded;
        }
        if (encoded.length > padLength) {
            throw new IllegalArgumentException("The PIN encodes to " + encoded.length + " bytes (" + format
                    + "), more than padTo(" + padLength + ", ...) allows");
        }
        byte[] padded = Arrays.copyOf(encoded, padLength);
        Arrays.fill(padded, encoded.length, padLength, padByte);
        return padded;
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] combined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    /** A byte given as unsigned ({@code 0x00..0xFF}) or as a signed Java {@code byte} ({@code -128..127}). */
    private static int unsignedByte(int value, String what) {
        if (value < Byte.MIN_VALUE || value > 0xFF) {
            throw new IllegalArgumentException(what + " must be 0x00-0xFF or a byte constant (-128 to 127), got "
                    + value);
        }
        return value & 0xFF;
    }
}
