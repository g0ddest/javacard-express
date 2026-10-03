package com.example.exp.lib;

import javacard.framework.CardRuntimeException;

/** Exception fixture extending an API class: its export entry lists all public supers. */
public class LibError extends CardRuntimeException {

    /**
     * Creates the exception.
     *
     * @param reason reason code
     */
    public LibError(short reason) {
        super(reason);
    }
}
