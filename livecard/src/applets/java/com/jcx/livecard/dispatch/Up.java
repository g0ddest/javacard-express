package com.jcx.livecard.dispatch;

/** Counts up. */
final class Up implements Counter {
    private short count;

    public short next() {
        return ++count;
    }
}
