package com.jcx.livecard.exceptions;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/**
 * Exception handling on the card: typed catches that tell a custom CardRuntimeException from an
 * ISOException, nested try/finally, a rethrow with a side effect, and an uncaught custom exception.
 *
 * <pre>
 * INS 10 P1 0|1|2  throw ProbeException(55) / ISOException(6A88) / nothing -> 1055 / 2A88 / 0000
 * INS 11           nested try/finally trace                                 -> 0171
 * INS 12           ISOException(6985) caught, counted, rethrown             -> SW 6985
 * INS 13           rethrow counter                                          -> 0001 after one INS 12
 * INS 14           ProbeException(33) not caught                            -> SW 6F00
 * </pre>
 */
public class ExceptionsApplet extends Applet {
    private final ProbeException probe = new ProbeException((short) 0);
    private short rethrows;

    private ExceptionsApplet() {
        register();
    }

    /**
     * Installs the applet.
     *
     * @param bArray  install parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ExceptionsApplet();
    }

    /**
     * Dispatches the commands.
     *
     * @param apdu the command
     */
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        short result = dispatch(buf[ISO7816.OFFSET_INS], buf[ISO7816.OFFSET_P1]);
        Util.setShort(buf, (short) 0, result);
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }

    private short dispatch(byte ins, byte p1) {
        switch (ins) {
            case 0x10: return classify(p1);
            case 0x11: return nested();
            case 0x12: rethrow(); return 0;
            case 0x13: return rethrows;
            case 0x14: raise((byte) 3); return 0;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED); return 0;
        }
    }

    private void raise(byte kind) {
        if (kind == 0) {
            probe.setReason((short) 0x55);
            throw probe;
        }
        if (kind == 1) {
            ISOException.throwIt((short) 0x6A88);
        }
        if (kind == 3) {
            probe.setReason((short) 0x33);
            throw probe;
        }
    }

    private short classify(byte kind) {
        try {
            raise(kind);
            return 0;
        } catch (ProbeException e) {
            return (short) (0x1000 | e.getReason());
        } catch (ISOException e) {
            return (short) (0x2000 | (e.getReason() & 0x0FFF));
        }
    }

    private short nested() {
        short trace = 0;
        try {
            try {
                probe.setReason((short) 7);
                throw probe;
            } finally {
                trace |= 1;
            }
        } catch (ProbeException e) {
            trace |= (short) (e.getReason() << 4);
        } finally {
            trace |= 0x100;
        }
        return trace;
    }

    private void rethrow() {
        try {
            ISOException.throwIt((short) 0x6985);
        } catch (ISOException e) {
            rethrows++;
            throw e;
        }
    }
}
