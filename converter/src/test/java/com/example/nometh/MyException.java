package com.example.nometh;

import javacard.framework.CardRuntimeException;

/** Custom exception without own virtual methods (empty table inherits the external base). */
public class MyException extends CardRuntimeException {
    public MyException(short reason) {
        super(reason);
    }
}
