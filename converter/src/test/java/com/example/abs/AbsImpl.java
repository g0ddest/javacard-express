package com.example.abs;

/** Implements the abstract methods, including the package-private one. */
public class AbsImpl extends AbsBase {
    protected AbsImpl() {
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new AbsImpl();
    }

    @Override
    public short f() {
        return 1;
    }

    @Override
    short g() {
        return 2;
    }

    @Override
    protected void h() {
        // nothing to do
    }
}
