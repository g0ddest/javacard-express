package com.example.abstract_;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;

/**
 * Abstract base class for testing ClassComponent generation of abstract classes.
 * The abstract modifier and the virtual method table should be correctly emitted.
 */
public abstract class AbstractBase extends Applet {

    protected short status;

    protected AbstractBase() {
        status = 0;
        register();
    }

    /**
     * Abstract method that subclasses must implement.
     */
    public abstract short handleCommand(byte[] buffer, short offset, short length);

    @Override
    public void process(APDU apdu) throws ISOException {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        status = handleCommand(buf, (short) 5, (short) (buf.length - 5));
    }
}
