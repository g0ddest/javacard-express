package com.example.chain;

/** Overrides m() and n() and introduces o(). */
public class C extends B {
    @Override
    public short m() {
        return 3;
    }

    @Override
    public short n() {
        return 30;
    }

    public short o() {
        return 300;
    }
}
