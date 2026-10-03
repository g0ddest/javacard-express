package name.velikodniy.jcexpress.pin;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.OwnerPIN;

/**
 * Test applet with a PIN in the style of the PIV Card Application (NIST SP 800-73-4 Part 2): its VERIFY command
 * uses the proprietary class {@code '80'} (any other class is answered {@code '6E00'}), the PIN "123456" is stored
 * padded with {@code 'FF'} to 8 bytes and a PIN of another length is answered {@code '6A80'}. A VERIFY without data
 * answers {@code '9000'} when the PIN is verified, {@code '63CX'} with the retries left otherwise and {@code '6983'}
 * when the PIN is blocked (ISO/IEC 7816-4:2005 7.5.6). Three tries.
 */
public final class ProprietaryPinApplet extends Applet {

    private static final byte CLA_PROPRIETARY = (byte) 0x80;
    private static final byte INS_VERIFY = 0x20;
    private static final byte PIN_LENGTH = 8;
    private static final byte TRIES = 3;
    private static final short SW_BLOCKED = 0x6983;
    private static final short SW_RETRIES = 0x63C0;
    private static final byte[] PIN = {'1', '2', '3', '4', '5', '6', (byte) 0xFF, (byte) 0xFF};

    private final OwnerPIN pin;

    private ProprietaryPinApplet() {
        pin = new OwnerPIN(TRIES, PIN_LENGTH);
        pin.update(PIN, (short) 0, PIN_LENGTH);
        register();
    }

    /**
     * Installs an instance (Java Card install method).
     *
     * @param bArray  the installation parameters
     * @param bOffset their offset
     * @param bLength their length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ProprietaryPinApplet();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        if (buffer[ISO7816.OFFSET_CLA] != CLA_PROPRIETARY) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        if (buffer[ISO7816.OFFSET_INS] != INS_VERIFY) {
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        verify(apdu);
    }

    private void verify(APDU apdu) {
        short length = apdu.setIncomingAndReceive();
        if (pin.getTriesRemaining() == 0) {
            ISOException.throwIt(SW_BLOCKED);
        }
        if (length == 0) {
            if (!pin.isValidated()) {
                ISOException.throwIt((short) (SW_RETRIES | pin.getTriesRemaining()));
            }
            return;
        }
        if (length != PIN_LENGTH) {
            ISOException.throwIt(ISO7816.SW_WRONG_DATA);
        }
        if (!pin.check(apdu.getBuffer(), ISO7816.OFFSET_CDATA, PIN_LENGTH)) {
            ISOException.throwIt((short) (SW_RETRIES | pin.getTriesRemaining()));
        }
    }
}
