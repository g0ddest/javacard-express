package com.example.lib;

import javacard.framework.Shareable;

/** Public shareable interface of the library. */
public interface LibService extends Shareable {
    short query();
}
