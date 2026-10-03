package com.example.exp.lib;

import javacard.framework.Shareable;

/** Shareable interface fixture (JCVM 3.1 §5.7 ACC_SHAREABLE). */
public interface LibService extends Shareable {

    /**
     * Echoes a value.
     *
     * @param x value
     * @return a value derived from {@code x}
     */
    short ping(short x);
}
