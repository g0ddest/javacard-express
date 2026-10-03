package com.example.pkgvirt;

/** Overrides a package-private method, adds a new package-private one, overrides a public one. */
public class Sub extends Base {
    @Override
    short calc(short x) {
        return (short) (x + 2);
    }

    short extra() {
        return 7;
    }

    @Override
    public short pub() {
        return 200;
    }
}
