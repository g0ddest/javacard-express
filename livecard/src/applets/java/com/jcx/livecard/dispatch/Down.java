package com.jcx.livecard.dispatch;

/** Counts down. */
final class Down implements Counter {
    private short count;

    public short next() {
        return --count;
    }
}
