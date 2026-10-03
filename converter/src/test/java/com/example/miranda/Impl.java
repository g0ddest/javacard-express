package com.example.miranda;

/** Concrete subclass implementing the interface method. */
public class Impl extends Base {
    @Override
    public short run(short v) {
        return (short) (v + 1);
    }
}
