package name.velikodniy.jcexpress.api;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;

/**
 * Minimal applet that compiles against our API stubs.
 * This verifies the stubs have correct signatures for typical applet development.
 */
public class HelloWorldApplet extends Applet {

    private static final byte INS_HELLO = 0x01;
    private static final byte INS_ECHO = 0x02;
    private static final byte[] HELLO = {0x48, 0x65, 0x6C, 0x6C, 0x6F}; // "Hello"

    protected HelloWorldApplet() {
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelloWorldApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
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
                apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, bytesRead);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
