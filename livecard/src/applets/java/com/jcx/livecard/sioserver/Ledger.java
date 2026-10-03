package com.jcx.livecard.sioserver;

import javacard.framework.Shareable;

/** The service the server applet shares with applets of other packages. */
public interface Ledger extends Shareable {

    /**
     * Adds to the server's total.
     *
     * @param delta the amount
     * @return the new total
     */
    short add(short delta);
}
