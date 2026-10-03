package com.jcx.livecard.dispatch;

/** First level of the override chain: a public method and a package-private one (package-visible token). */
public class Base {

    public short value() {
        return 1;
    }

    short hidden() {
        return 0x10;
    }

    short callHidden() {
        return hidden();
    }
}
