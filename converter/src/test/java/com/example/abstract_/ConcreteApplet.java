package com.example.abstract_;

import javacard.framework.ISO7816;

/**
 * Concrete subclass of AbstractBase for testing ClassComponent generation.
 * Tests abstract class hierarchy and virtual method dispatch tables.
 */
public class ConcreteApplet extends AbstractBase {

    protected ConcreteApplet() {
        super();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new ConcreteApplet();
    }

    @Override
    public short handleCommand(byte[] buffer, short offset, short length) {
        if (length > 0) {
            return (short) (buffer[offset] & 0xFF);
        }
        return ISO7816.SW_WRONG_LENGTH;
    }
}
