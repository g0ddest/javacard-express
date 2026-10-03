package com.example.share;

import javacard.framework.Shareable;

/** Shareable service interface (ACC_SHAREABLE, §6.9.2.1). */
public interface IBase extends Shareable {
    short get();
}
