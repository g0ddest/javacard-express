package name.velikodniy.jcexpress;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * Minimal example applet for testing.
 *
 * <ul>
 *   <li>INS 0x01: returns "Hello" (5 bytes ASCII)</li>
 *   <li>INS 0x02: echoes received data</li>
 *   <li>Any other INS: 6D00 (INS not supported)</li>
 * </ul>
 */
public class HelloWorldApplet extends Applet {

    private static final byte INS_HELLO = 0x01;
    private static final byte INS_ECHO = 0x02;

    private static final byte[] HELLO = {'H', 'e', 'l', 'l', 'o'};

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelloWorldApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buffer = apdu.getBuffer();
        byte ins = buffer[ISO7816.OFFSET_INS];

        switch (ins) {
            case INS_HELLO:
                apdu.setOutgoing();
                apdu.setOutgoingLength((short) HELLO.length);
                apdu.sendBytesLong(HELLO, (short) 0, (short) HELLO.length);
                break;

            case INS_ECHO:
                short bytesRead = apdu.setIncomingAndReceive();
                apdu.setOutgoing();
                apdu.setOutgoingLength(bytesRead);
                apdu.sendBytesLong(buffer, ISO7816.OFFSET_CDATA, bytesRead);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
