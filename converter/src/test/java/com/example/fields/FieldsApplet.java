package com.example.fields;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Instance fields of mixed types and visibilities declared in an interleaved order.
 * Tokens must follow JCVM 3.1 §4.3.7.5 (public primitives, public references, private
 * references, private primitives; int takes two tokens) and the class_info must describe the
 * instance layout in 16-bit cells (§6.9.2.3). The int fields are only declared (no int
 * arithmetic) so the fixture does not depend on int bytecode support.
 */
public class FieldsApplet extends Applet {
    private short a;
    private byte[] buf;
    private int counter;
    public short pubS;
    private Object obj;
    boolean flag;
    public byte[] pubArr;
    protected int protI;
    private short[] shorts;
    byte last;

    protected FieldsApplet() {
        buf = new byte[8];
        shorts = new short[4];
        obj = buf;
        pubArr = buf;
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new FieldsApplet();
    }

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) {
            return;
        }
        byte[] b = apdu.getBuffer();
        a++;
        pubS = Util.getShort(b, ISO7816.OFFSET_P1);
        flag = !flag;
        last = b[ISO7816.OFFSET_P2];
        shorts[0] = a;
        Util.setShort(b, (short) 0, (short) (a + pubS + shorts[0] + last + buf[0]));
        b[2] = (byte) (flag ? 1 : 0);
        b[3] = (byte) (obj == pubArr ? 1 : 0);
        apdu.setOutgoingAndSend((short) 0, (short) 4);
    }
}
