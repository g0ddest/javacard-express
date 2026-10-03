package com.jcx.livecard.dispatch;

/** Second level: overrides both methods, the public one with a super call. */
public class Middle extends Base {

    public short value() {
        return (short) (super.value() + 10);
    }

    short hidden() {
        return 0x20;
    }
}
