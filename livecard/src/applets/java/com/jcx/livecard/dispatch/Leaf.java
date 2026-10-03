package com.jcx.livecard.dispatch;

/** Third level: overrides the public method with a super call, inherits the package-private override. */
public class Leaf extends Middle {

    public short value() {
        return (short) (super.value() + 100);
    }
}
