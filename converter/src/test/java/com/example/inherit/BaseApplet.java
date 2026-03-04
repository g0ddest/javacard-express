package com.example.inherit;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;

/**
 * Base class in a multi-level inheritance chain.
 * Tests that inherited virtual method tokens are correctly assigned.
 */
public abstract class BaseApplet extends Applet {

    protected short version;

    protected BaseApplet() {
        version = 1;
    }

    public short getVersion() {
        return version;
    }

    @Override
    public abstract void process(APDU apdu) throws ISOException;
}
