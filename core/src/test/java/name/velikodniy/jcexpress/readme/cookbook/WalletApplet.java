package name.velikodniy.jcexpress.readme.cookbook;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;

/**
 * The applet of TESTING.md: a wallet with a balance, a PIN that guards DEBIT, and a TLV summary. Written in the
 * Java Card language subset (it would convert as it is). Its own commands use the proprietary class {@code '80'}:
 *
 * <ul>
 *   <li>{@code 00 A4 04 00 <AID>} SELECT: the FCI template {@code 6F <length> 84 <length> <instance AID>} (ISO/IEC
 *       7816-4: tag {@code '84'} is the DF name);</li>
 *   <li>{@code 80 20 00 01 08 <PIN>} VERIFY: the PIN "1234", ASCII, padded with {@code 'FF'} to 8 bytes (the PIV
 *       style, NIST SP 800-73-4); {@code '63CX'} with X tries left, {@code '6983'} when blocked; without data it
 *       answers {@code '9000'} when the PIN is verified, otherwise {@code '63CX'} (ISO/IEC 7816-4:2005 7.5.6);</li>
 *   <li>{@code 80 30 00 00 02 <amount>} CREDIT;</li>
 *   <li>{@code 80 40 00 00 02 <amount>} DEBIT: {@code '6982'} before the PIN is verified, {@code '6985'} when the
 *       balance is too low;</li>
 *   <li>{@code 80 52 00 00 02} GET BALANCE: two bytes, big-endian;</li>
 *   <li>{@code 80 54 00 00 00} GET INFO: {@code E1 07 81 02 <balance> 82 01 <PIN tries left>}.</li>
 * </ul>
 *
 * <p>The install parameters are empty (balance 0) or two bytes, the starting balance; anything else fails the
 * install method with {@code '6A80'}. Deselecting the applet ends the PIN validation; the PIN's validation flag
 * of {@link OwnerPIN} is also cleared by a card reset, the balance (a field) survives both.</p>
 */
public class WalletApplet extends Applet {

    static final byte CLA_WALLET = (byte) 0x80;
    static final byte INS_VERIFY = 0x20;
    static final byte INS_CREDIT = 0x30;
    static final byte INS_DEBIT = 0x40;
    static final byte INS_GET_BALANCE = 0x52;
    static final byte INS_GET_INFO = 0x54;

    private static final byte PIN_TRIES = 3;
    private static final byte PIN_SIZE = 8;
    private static final short SW_TRIES_LEFT = 0x63C0;
    private static final short SW_BLOCKED = 0x6983;
    private static final byte[] PIN = {'1', '2', '3', '4', (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

    private final OwnerPIN pin = new OwnerPIN(PIN_TRIES, PIN_SIZE);
    private short balance;

    private WalletApplet(byte[] parameters, short offset, byte length) {
        pin.update(PIN, (short) 0, PIN_SIZE);
        if (length == 2) {
            balance = Util.getShort(parameters, offset);
        } else if (length != 0) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
    }

    /**
     * Creates and registers an instance (Java Card install method).
     *
     * @param bArray  the install data: {@code [Li][instance AID][Lc][control information][La][install parameters]}
     * @param bOffset where the install data starts
     * @param bLength the length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        byte li = bArray[bOffset];                                   // [Li][instance AID]
        byte lc = bArray[(short) (bOffset + 1 + li)];                // [Lc][control information]
        byte la = bArray[(short) (bOffset + 2 + li + lc)];           // [La][install parameters]
        new WalletApplet(bArray, (short) (bOffset + 3 + li + lc), la)
                .register(bArray, (short) (bOffset + 1), li);
    }

    @Override
    public void deselect() {
        pin.reset();                                                 // the PIN must be presented again
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            selectResponse(apdu);
            return;
        }
        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_CLA] != CLA_WALLET) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        switch (buffer[ISO7816.OFFSET_INS]) {
            case INS_VERIFY:
                verify(apdu);
                return;
            case INS_CREDIT:
                balance += amount(apdu);
                return;
            case INS_DEBIT:
                debit(amount(apdu));
                return;
            case INS_GET_BALANCE:
                Util.setShort(buffer, (short) 0, balance);
                apdu.setOutgoingAndSend((short) 0, (short) 2);
                return;
            case INS_GET_INFO:
                info(apdu);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    /** Answers SELECT with the FCI template: {@code 6F <length> 84 <length> <instance AID>}. */
    private static void selectResponse(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        byte length = JCSystem.getAID().getBytes(buffer, (short) 4);
        buffer[0] = 0x6F;
        buffer[1] = (byte) (length + 2);
        buffer[2] = (byte) 0x84;
        buffer[3] = length;
        apdu.setOutgoingAndSend((short) 0, (short) (length + 4));
    }

    private void verify(APDU apdu) {
        short length = apdu.setIncomingAndReceive();
        if (pin.getTriesRemaining() == 0) {
            ISOException.throwIt(SW_BLOCKED);
        }
        if (length == 0) {
            if (!pin.isValidated()) {
                ISOException.throwIt((short) (SW_TRIES_LEFT | pin.getTriesRemaining()));
            }
            return;
        }
        if (length != PIN_SIZE) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        if (!pin.check(apdu.getBuffer(), ISO7816.OFFSET_CDATA, PIN_SIZE)) {
            ISOException.throwIt(pin.getTriesRemaining() == 0 ? SW_BLOCKED
                    : (short) (SW_TRIES_LEFT | pin.getTriesRemaining()));
        }
    }

    private static short amount(APDU apdu) {
        if (apdu.setIncomingAndReceive() != 2) {
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        }
        return Util.getShort(apdu.getBuffer(), ISO7816.OFFSET_CDATA);
    }

    private void debit(short amount) {
        if (!pin.isValidated()) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        if (amount > balance) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        balance -= amount;
    }

    private void info(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        buffer[0] = (byte) 0xE1;                                     // constructed, private class
        buffer[1] = 7;
        buffer[2] = (byte) 0x81;
        buffer[3] = 2;
        Util.setShort(buffer, (short) 4, balance);
        buffer[6] = (byte) 0x82;
        buffer[7] = 1;
        buffer[8] = pin.getTriesRemaining();
        apdu.setOutgoingAndSend((short) 0, (short) 9);
    }
}
