package name.velikodniy.jcexpress.fakes.gp;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;
import org.globalplatform.GPSystem;
import org.globalplatform.SecureChannel;
import org.globalplatform.SecureChannelx;
import org.globalplatform.SecureChannelx2;

/**
 * An applet that uses the secure channel of its Security Domain the ways published applets do (class '80'). The
 * commands INITIALIZE UPDATE and EXTERNAL AUTHENTICATE go to {@code processSecurity}; a command whose class byte
 * indicates secure messaging is unwrapped with {@code unwrap(buffer, 0, 5 + Lc)} and must then have the applet's
 * class; its response gets the status bytes appended and goes through {@code wrap}.
 *
 * <p>The first byte of the install parameters chooses how the secure channel commands are passed on: 0 without
 * receiving the data first, 1 after {@code setIncomingAndReceive()}, 2 through
 * {@code SecureChannelx2.processSecurity} on the APDU buffer. A second byte 1 makes {@code deselect()} leave the
 * session alone instead of calling {@code resetSecurity()}.</p>
 *
 * <pre>
 * INS 10  echoes the command data
 * INS 12  answers the Current Security Level (one byte)
 * INS 14  answers 'OK' only to a protected command of an authenticated session with C-MAC, else 6982
 * INS 16  decryptData on the command data, answers the result
 * INS 18  encryptData on the command data, answers the result
 * INS 1A  answers what unwrap did: its result (two bytes), then the class byte and Lc in the buffer
 * INS 1C  setSecurityLevel(P1)
 * INS 1E  resetSecurity()
 * INS 20  answers 'CAFE' with the status word P1 P2 through wrap
 * </pre>
 */
public class SecureChannelApplet extends Applet {

    private static final byte CLA = (byte) 0x80;
    private static final byte RECEIVE_FIRST = 1;
    private static final byte FROM_ARRAY = 2;

    private final byte mode;
    private final boolean keepSessionOnDeselect;

    private SecureChannelApplet(byte mode, boolean keepSessionOnDeselect) {
        this.mode = mode;
        this.keepSessionOnDeselect = keepSessionOnDeselect;
    }

    /**
     * Installs the applet (Java Card install method).
     *
     * @param bArray  install data
     * @param bOffset offset of the install data
     * @param bLength length of the install data
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        short control = (short) (bOffset + 1 + bArray[bOffset]);
        short data = (short) (control + 1 + bArray[control]);
        byte length = bArray[data];
        byte mode = length > 0 ? bArray[(short) (data + 1)] : 0;
        boolean keep = length > 1 && bArray[(short) (data + 2)] == 1;
        new SecureChannelApplet(mode, keep).register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    @Override
    public void deselect() {
        if (!keepSessionOnDeselect) {
            GPSystem.getSecureChannel().resetSecurity();
        }
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        SecureChannel channel = GPSystem.getSecureChannel();
        byte ins = buffer[ISO7816.OFFSET_INS];
        if (ins == (byte) 0x50 || ins == (byte) 0x82) {
            secureChannelCommand(apdu, buffer, channel);
            return;
        }
        short received = apdu.setIncomingAndReceive();
        boolean secureMessaging = (buffer[ISO7816.OFFSET_CLA] & 0x04) != 0;
        short unwrapped = secureMessaging
                ? channel.unwrap(buffer, (short) 0, (short) (ISO7816.OFFSET_CDATA + received)) : 0;
        if (buffer[ISO7816.OFFSET_CLA] != CLA) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        command(apdu, buffer, channel, secureMessaging, unwrapped);
    }

    private void secureChannelCommand(APDU apdu, byte[] buffer, SecureChannel channel) {
        short length;
        if (mode == FROM_ARRAY) {
            short received = apdu.setIncomingAndReceive();
            length = ((SecureChannelx2) channel).processSecurity(buffer, (short) 0,
                    (short) (ISO7816.OFFSET_CDATA + received), ISO7816.OFFSET_CDATA);
        } else {
            if (mode == RECEIVE_FIRST) {
                apdu.setIncomingAndReceive();
            }
            length = channel.processSecurity(apdu);
        }
        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, length);
    }

    private void command(APDU apdu, byte[] buffer, SecureChannel channel, boolean secureMessaging, short unwrapped) {
        short lc = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);
        short length = 0;
        switch (buffer[ISO7816.OFFSET_INS]) {
            case 0x10 -> length = lc;
            case 0x12 -> {
                buffer[ISO7816.OFFSET_CDATA] = channel.getSecurityLevel();
                length = 1;
            }
            case 0x14 -> length = secret(buffer, channel, secureMessaging);
            case 0x16 -> length = channel.decryptData(buffer, ISO7816.OFFSET_CDATA, lc);
            case 0x18 -> length = channel.encryptData(buffer, ISO7816.OFFSET_CDATA, lc);
            case 0x1A -> {
                byte cla = buffer[ISO7816.OFFSET_CLA];
                Util.setShort(buffer, ISO7816.OFFSET_CDATA, unwrapped);
                buffer[(short) (ISO7816.OFFSET_CDATA + 2)] = cla;
                buffer[(short) (ISO7816.OFFSET_CDATA + 3)] = (byte) lc;
                length = 4;
            }
            case 0x1C -> ((SecureChannelx) channel).setSecurityLevel(buffer[ISO7816.OFFSET_P1]);
            case 0x1E -> channel.resetSecurity();
            case 0x20 -> {
                withStatus(apdu, buffer, channel);
                return;
            }
            default -> ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
        send(apdu, buffer, channel, secureMessaging, length);
    }

    private static short secret(byte[] buffer, SecureChannel channel, boolean secureMessaging) {
        byte required = (byte) (SecureChannel.AUTHENTICATED | SecureChannel.C_MAC);
        if (!secureMessaging || (channel.getSecurityLevel() & required) != required) {
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
        }
        buffer[ISO7816.OFFSET_CDATA] = 'O';
        buffer[(short) (ISO7816.OFFSET_CDATA + 1)] = 'K';
        return 2;
    }

    /** Sends response data; the response to a protected command gets '9000' appended and goes through wrap. */
    private static void send(APDU apdu, byte[] buffer, SecureChannel channel, boolean secureMessaging, short length) {
        short toSend = length;
        if (secureMessaging) {
            Util.setShort(buffer, (short) (ISO7816.OFFSET_CDATA + length), (short) 0x9000);
            toSend = channel.wrap(buffer, ISO7816.OFFSET_CDATA, (short) (length + 2));
        }
        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, toSend);
    }

    /** Answers 'CAFE' with the status word of P1 P2, both through wrap. */
    private static void withStatus(APDU apdu, byte[] buffer, SecureChannel channel) {
        short status = Util.getShort(buffer, ISO7816.OFFSET_P1);
        Util.setShort(buffer, ISO7816.OFFSET_CDATA, (short) 0xCAFE);
        Util.setShort(buffer, (short) (ISO7816.OFFSET_CDATA + 2), status);
        short length = channel.wrap(buffer, ISO7816.OFFSET_CDATA, (short) 4);
        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, length);
        if (status != (short) 0x9000) {
            ISOException.throwIt(status);
        }
    }
}
