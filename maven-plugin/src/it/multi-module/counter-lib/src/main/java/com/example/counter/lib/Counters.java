package com.example.counter.lib;

/** A library package (no applets): exported, so other packages can link against it. */
public class Counters {

    /** Prevents instantiation. */
    protected Counters() {
    }

    /**
     * Increments a counter, saturating at Short.MAX_VALUE.
     *
     * @param value the counter
     * @return the incremented counter
     */
    public static short increment(short value) {
        return value == Short.MAX_VALUE ? value : (short) (value + 1);
    }
}
