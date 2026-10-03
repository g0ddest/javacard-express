package com.jcx.livecard.exceptions;

import javacard.framework.CardRuntimeException;

/** A custom runtime exception; one instance is allocated at install time and reused with new reasons. */
public class ProbeException extends CardRuntimeException {

    /**
     * Creates the exception.
     *
     * @param reason the reason code
     */
    public ProbeException(short reason) {
        super(reason);
    }
}
