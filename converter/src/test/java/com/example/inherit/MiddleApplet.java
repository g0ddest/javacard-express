package com.example.inherit;

import javacard.framework.APDU;
import javacard.framework.ISOException;

/**
 * Middle class in the inheritance chain: Base → Middle → Inheritance.
 * Adds a new virtual method and overrides getVersion.
 */
public abstract class MiddleApplet extends BaseApplet {

    protected MiddleApplet() {
        version = 2;
    }

    @Override
    public short getVersion() {
        return (short) (version + 100);
    }

    public abstract short getFeatureLevel();
}
