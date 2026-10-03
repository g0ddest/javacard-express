package com.example.chain;

/** Overrides only m(): its public table must still cover the inherited n() (§6.9.2.3). */
public class B extends A {
    @Override
    public short m() {
        return 2;
    }
}
