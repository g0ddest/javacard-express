package name.velikodniy.jcexpress.readme;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * The applet the core README examples use: INS 01 answers "Hello", INS 02 echoes the command data.
 * It registers with the instance AID from its installation parameters (Java Card API
 * {@code Applet.install}: {@code [Li][AID][Lc][control info][La][applet data]}).
 */
public class MyApplet extends Applet {

    private static final byte INS_HELLO = 0x01;
    private static final byte INS_ECHO = 0x02;
    private static final byte[] HELLO = {'H', 'e', 'l', 'l', 'o'};

    /**
     * Installs the applet under the AID given in the installation parameters.
     *
     * @param bArray  installation parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new MyApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        switch (buffer[ISO7816.OFFSET_INS]) {
            case INS_HELLO -> {
                apdu.setOutgoing();
                apdu.setOutgoingLength((short) HELLO.length);
                apdu.sendBytesLong(HELLO, (short) 0, (short) HELLO.length);
            }
            case INS_ECHO -> {
                short length = apdu.setIncomingAndReceive();
                apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, length);
            }
            default -> ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
